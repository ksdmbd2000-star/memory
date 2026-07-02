package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.abs

class AudioRecordingService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var audioRecord: AudioRecord? = null
    private var isRecordingLoopActive = false
    private var currentFileStream: FileOutputStream? = null
    private var currentChunkFile: File? = null
    private var currentChunkStartTime = 0L

    private var totalDurationJob: Job? = null
    private var startTimeMillis = 0L

    private var lastCleanupTime = 0L

    private var webSocketClient: GeminiLiveWebSocketClient? = null
    private var liveChatJob: Job? = null
    private var currentTextAccumulated = ""
    private lateinit var db: AppDatabase

    companion object {
        private const val TAG = "AudioRecordingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "voice_recorder_channel"

        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_DURATION_MS = 60000L // 1 minute per chunk
        private const val KEEP_DURATION_MS = 30 * 60 * 1000L // 30 minutes retention
        private const val CLEANUP_INTERVAL_MS = 5 * 60 * 1000L // Cleanup every 5 minutes
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        createNotificationChannel()
        startForegroundServiceCompat()
        observeLiveChatState()
    }

    private fun observeLiveChatState() {
        liveChatJob = serviceScope.launch {
            AudioRecordingState.isLiveChatActive.collect { active ->
                if (active) {
                    val apiKey = BuildConfig.GEMINI_API_KEY
                    if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                        AudioRecordingState.liveChatStatus.value = "Error: API Key missing"
                        AudioRecordingState.isLiveChatActive.value = false
                        return@collect
                    }
                    if (webSocketClient == null) {
                        Log.d(TAG, "Starting Live Chat WebSocket connection...")
                        currentTextAccumulated = ""
                        webSocketClient = GeminiLiveWebSocketClient(
                            apiKey = apiKey,
                            onChunkReceived = { chunkText ->
                                currentTextAccumulated += chunkText
                                AudioRecordingState.activeStreamingText.value = currentTextAccumulated
                            },
                            onTurnCompleted = {
                                val finalMsg = currentTextAccumulated.trim()
                                if (finalMsg.isNotEmpty()) {
                                    serviceScope.launch(Dispatchers.IO) {
                                        db.chatLogDao().insertChatLog(
                                            ChatLog(sender = "user", text = finalMsg)
                                        )
                                    }
                                }
                                currentTextAccumulated = ""
                                AudioRecordingState.activeStreamingText.value = ""
                            },
                            onStatusChanged = { status ->
                                AudioRecordingState.liveChatStatus.value = status
                            }
                        )
                        webSocketClient?.connect()
                    }
                } else {
                    webSocketClient?.disconnect()
                    webSocketClient = null
                    currentTextAccumulated = ""
                    AudioRecordingState.activeStreamingText.value = ""
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        if (!isRecordingLoopActive) {
            startRecording()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundServiceCompat() {
        val notification = createNotification("Voice Rewind is active", "Always-on audio recorder is buffering...")
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, 
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Rewind Active Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification shown when always-on voice recorder service is buffering audio."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startRecording() {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        try {
            // Check permission
            if (androidx.core.app.ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                AudioRecordingState.statusMessage.value = "Microphone permission missing"
                stopSelf()
                return
            }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                AudioRecordingState.statusMessage.value = "Failed to initialize AudioRecord"
                Log.e(TAG, "AudioRecord state is not initialized")
                stopSelf()
                return
            }

            audioRecord?.startRecording()
            isRecordingLoopActive = true
            AudioRecordingState.isRecording.value = true
            AudioRecordingState.statusMessage.value = "Recording Live"
            startTimeMillis = System.currentTimeMillis()
            lastCleanupTime = System.currentTimeMillis()

            // Start duration tracker
            totalDurationJob = serviceScope.launch {
                while (isRecordingLoopActive) {
                    val duration = calculateTotalBufferedDuration()
                    AudioRecordingState.recordingDurationMillis.value = duration
                    delay(1000)
                }
            }

            // Start recording thread
            serviceScope.launch {
                val buffer = ByteArray(bufferSize)
                createNewChunk()

                while (isRecordingLoopActive) {
                    val record = audioRecord ?: break
                    val bytesRead = record.read(buffer, 0, buffer.size)

                    if (bytesRead > 0) {
                        try {
                            currentFileStream?.write(buffer, 0, bytesRead)
                            calculateAmplitude(buffer, bytesRead)
                            if (AudioRecordingState.isLiveChatActive.value) {
                                webSocketClient?.sendAudioChunk(buffer, bytesRead)
                            }
                        } catch (e: IOException) {
                            Log.e(TAG, "Error writing to chunk file", e)
                        }
                    }

                    // Check chunk rotation
                    val now = System.currentTimeMillis()
                    if (now - currentChunkStartTime >= CHUNK_DURATION_MS) {
                        rotateChunk()
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception starting recording service", e)
            AudioRecordingState.statusMessage.value = "Error: ${e.message}"
            stopSelf()
        }
    }

    private fun createNewChunk() {
        val now = System.currentTimeMillis()
        currentChunkStartTime = now
        val dir = AudioRecordingState.getChunksDir(this)
        val file = File(dir, "chunk_$now.pcm")
        currentChunkFile = file
        try {
            currentFileStream = FileOutputStream(file)
            Log.d(TAG, "Started new chunk: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating new chunk file", e)
        }
    }

    private fun rotateChunk() {
        try {
            currentFileStream?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing stream during rotation", e)
        }
        createNewChunk()
        performRetentionAndCleanup()
    }

    private fun performRetentionAndCleanup() {
        val now = System.currentTimeMillis()
        val isCleanupTime = now - lastCleanupTime >= CLEANUP_INTERVAL_MS

        val chunks = AudioRecordingState.getSortedChunkFiles(this)
        
        // Always delete files older than 30 minutes to stay within limits
        for (chunk in chunks) {
            val timestamp = chunk.nameWithoutExtension.removePrefix("chunk_").toLongOrNull() ?: 0L
            if (now - timestamp > KEEP_DURATION_MS) {
                if (chunk.exists()) {
                    chunk.delete()
                    Log.d(TAG, "Deleted old chunk: ${chunk.name}")
                }
            }
        }

        if (isCleanupTime) {
            lastCleanupTime = now
            Log.d(TAG, "Periodic 5-minute cleanup completed")
        }
    }

    private fun calculateTotalBufferedDuration(): Long {
        val chunks = AudioRecordingState.getSortedChunkFiles(this)
        if (chunks.isEmpty()) return 0L
        
        val oldestTimestamp = chunks.first().nameWithoutExtension.removePrefix("chunk_").toLongOrNull() ?: 0L
        val newestTimestamp = System.currentTimeMillis()
        
        val total = newestTimestamp - oldestTimestamp
        return total.coerceAtMost(KEEP_DURATION_MS) // clamp to max 30 mins
    }

    private fun calculateAmplitude(buffer: ByteArray, bytesRead: Int) {
        var maxVal = 0
        for (i in 0 until bytesRead step 2) {
            if (i + 1 < bytesRead) {
                // Read 16-bit PCM little-endian sample
                val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF))
                val absVal = abs(sample)
                if (absVal > maxVal) {
                    maxVal = absVal
                }
            }
        }
        val normalized = maxVal.toFloat() / Short.MAX_VALUE
        // Smooth amplitude slightly for visual appeal
        val current = AudioRecordingState.currentAmplitude.value
        AudioRecordingState.currentAmplitude.value = current * 0.4f + normalized * 0.6f
    }

    private fun stopRecording() {
        isRecordingLoopActive = false
        AudioRecordingState.isRecording.value = false
        totalDurationJob?.cancel()
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null

        try {
            currentFileStream?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing active file stream", e)
        }
        currentFileStream = null
        currentChunkFile = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
        stopRecording()
        webSocketClient?.disconnect()
        webSocketClient = null
        liveChatJob?.cancel()
        serviceJob.cancel()
    }
}

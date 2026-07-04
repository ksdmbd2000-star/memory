package com.example

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

object AudioRecordingState {
    val isRecording = MutableStateFlow(false)
    val recordingDurationMillis = MutableStateFlow(0L) // total milliseconds recorded in this active run
    val currentAmplitude = MutableStateFlow(0f) // Normalized value between 0.0f and 1.0f for UI visualizer
    val statusMessage = MutableStateFlow("Idle")
    val isUploading = MutableStateFlow(false)
    val uploadProgress = MutableStateFlow("")
    val isSummarizing = MutableStateFlow(false)
    val summaryResult = MutableStateFlow<String?>(null)

    // Live STT Chat Log Mode States
    val isLiveChatActive = MutableStateFlow(false)
    val liveChatStatus = MutableStateFlow("Disconnected")
    val activeStreamingText = MutableStateFlow("")
    val debugLog = MutableStateFlow("")

    fun getChunksDir(context: Context): File {
        val dir = File(context.cacheDir, "audio_chunks")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getSortedChunkFiles(context: Context): List<File> {
        val dir = getChunksDir(context)
        return dir.listFiles { file -> 
            file.name.startsWith("chunk_") && file.name.endsWith(".pcm") 
        }?.sortedBy { file ->
            // Extract timestamp from chunk_TIMESTAMP.pcm
            file.nameWithoutExtension.removePrefix("chunk_").toLongOrNull() ?: 0L
        } ?: emptyList()
    }
}

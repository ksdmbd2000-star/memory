package com.example

import android.util.Base64
import android.util.Log
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiLiveWebSocketClient(
    private val apiKey: String,
    private val onChunkReceived: (String) -> Unit,
    private val onTurnCompleted: () -> Unit,
    private val onStatusChanged: (String) -> Unit
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "GeminiLiveWS"
        private const val WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    }

    fun connect() {
        if (webSocket != null) return
        
        val url = "$WS_URL?key=$apiKey"
        val request = Request.Builder().url(url).build()
        onStatusChanged("Connecting...")

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket Opened successfully")
                onStatusChanged("Connected")
                sendSetupMessage()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.v(TAG, "Received message text: $text")
                parseAndHandleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closing: $code / $reason")
                onStatusChanged("Disconnecting: $code $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closed: $code / $reason")
                onStatusChanged("Disconnected")
                this@GeminiLiveWebSocketClient.webSocket = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Failure: ${t.message}", t)
                onStatusChanged("Error: ${t.message ?: "Unknown Error"}")
                this@GeminiLiveWebSocketClient.webSocket = null
            }
        })
    }

    private fun sendSetupMessage() {
        try {
            val setupJson = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", "models/gemini-2.5-flash-native-audio-latest")
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply {
                            put("AUDIO")
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "You are a helpful assistant.")
                            })
                        })
                    })
                })
            }
            webSocket?.send(setupJson.toString())
            Log.d(TAG, "Sent live setup configuration payload")
        } catch (e: Exception) {
            Log.e(TAG, "Error compiling/sending setup message", e)
        }
    }

    fun sendAudioChunk(pcmData: ByteArray, bytesCount: Int) {
        val socket = webSocket ?: return
        try {
            // If bytesCount is less than the array size, slice it
            val finalData = if (bytesCount == pcmData.size) {
                pcmData
            } else {
                pcmData.copyOfRange(0, bytesCount)
            }

            val base64Data = Base64.encodeToString(finalData, Base64.NO_WRAP)
            val audioJson = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("audio", JSONObject().apply {
                        put("data", base64Data)
                        put("mimeType", "audio/pcm;rate=16000")
                    })
                })
            }
            socket.send(audioJson.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send audio chunk", e)
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        onStatusChanged("Disconnected")
    }

    private fun parseAndHandleMessage(text: String) {
        try {
            val root = JSONObject(text)
            val serverContent = root.optJSONObject("serverContent") ?: root.optJSONObject("server_content") ?: return
            
            val inputTranscription = serverContent.optJSONObject("inputTranscription") ?: serverContent.optJSONObject("input_transcription")
            if (inputTranscription != null) {
                val chunkText = inputTranscription.optString("text")
                if (!chunkText.isNullOrEmpty()) {
                    onChunkReceived(chunkText)
                }
            }

            val modelTurn = serverContent.optJSONObject("modelTurn") ?: serverContent.optJSONObject("model_turn")
            if (modelTurn != null) {
                val parts = modelTurn.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val part = parts.optJSONObject(0)
                    if (part != null) {
                        val chunkText = part.optString("text")
                        if (!chunkText.isNullOrEmpty()) {
                            onChunkReceived(chunkText)
                        }
                    }
                }
            }

            val turnComplete = serverContent.optBoolean("turnComplete", false) || serverContent.optBoolean("turn_complete", false)
            if (turnComplete) {
                onTurnCompleted()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message", e)
        }
    }
}

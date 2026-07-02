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
        private const val WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
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
                    put("model", "models/gemini-2.0-flash")
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply {
                            put("TEXT")
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "You are an expert Japanese speech-to-text transcription bot. Listen to the user's speech and output EXACTLY what they say in Japanese. Do NOT add any preamble, greeting, response, commentary, summary, punctuation correction, or conversation filler. Output ONLY the raw transcribed words. If there is no speech or only noise, do not output anything.")
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
                    put("mediaChunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Data)
                        })
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
            
            val modelTurn = serverContent.optJSONObject("modelTurn") ?: serverContent.optJSONObject("model_turn")
            if (modelTurn != null) {
                val parts = modelTurn.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val part = parts.getJSONObject(0)
                    val chunkText = part.optString("text")
                    if (!chunkText.isNullOrEmpty()) {
                        onChunkReceived(chunkText)
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

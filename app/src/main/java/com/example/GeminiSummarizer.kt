package com.example

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object GeminiSummarizer {
    private const val TAG = "GeminiSummarizer"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Sends the local WAV audio file to the Gemini API to get a concise Japanese summary.
     * Memory-safe streaming is used to avoid OutOfMemoryErrors (OOM).
     */
    suspend fun summarizeAudio(apiKey: String, audioFile: File): String = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "エラー: Gemini APIキーが設定されていません。AI Studioの「Secrets」パネルからAPIキーを設定してください。"
        }
        
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext "エラー: 要約する音声ファイルがありません。まずは録音を一定時間行ってから再度お試しください。"
        }

        try {
            Log.d(TAG, "Preparing memory-safe stream for audio file: ${audioFile.name}, size: ${audioFile.length()} bytes")

            // --- STAGE 1: Check for meaningful human speech ---
            val detectRequestBody = object : RequestBody() {
                override fun contentType() = "application/json".toMediaType()

                override fun writeTo(sink: BufferedSink) {
                    val header = "{\"systemInstruction\":{\"parts\":[{\"text\":\"あなたは音声に人間の意味のある発言が含まれているかを正確に判定する、厳格な判定アシスタントです。単なる環境音やノイズ、マイクのガサガサ音、ため息、意味をなさない音声、無音の場合は必ず false と判定してください。日本語として1単語以上の意味のある人間の発言が聞き取れる場合のみ true と判定してください。出力は必ず指定されたJSONフォーマットのみで行い、他の解説は含めないでください。\"}]},\"contents\":[{\"parts\":[{\"text\":\"この音声に人間の意味のある発話（日本語として1単語以上の意味のある発言）が含まれているかを判定し、結果を hasSpeech フィールドを持つJSONオブジェクトとして返してください。\"},{\"inlineData\":{\"mimeType\":\"audio/wav\",\"data\":\""
                    sink.writeUtf8(header)

                    val buffer = ByteArray(3072)
                    audioFile.inputStream().use { input ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            val encodedChunk = if (bytesRead == buffer.size) {
                                Base64.encode(buffer, Base64.NO_WRAP)
                            } else {
                                Base64.encode(buffer.copyOf(bytesRead), Base64.NO_WRAP)
                            }
                            sink.write(encodedChunk)
                        }
                    }

                    val footer = "\"}}]}],\"generationConfig\":{\"responseMimeType\":\"application/json\",\"responseSchema\":{\"type\":\"OBJECT\",\"properties\":{\"hasSpeech\":{\"type\":\"BOOLEAN\"}},\"required\":[\"hasSpeech\"]}}}"
                    sink.writeUtf8(footer)
                }
            }

            val detectUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent?key=$apiKey"
            val detectRequest = Request.Builder()
                .url(detectUrl)
                .post(detectRequestBody)
                .build()

            Log.d(TAG, "Sending human speech detection request to Gemini API...")
            var hasSpeech = false
            client.newCall(detectRequest).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "Detection response code: ${response.code}")
                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(responseBody)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val resultText = parts.getJSONObject(0).optString("text")
                            Log.d(TAG, "Detection raw result: $resultText")
                            if (resultText.isNotEmpty()) {
                                try {
                                    val resultJson = JSONObject(resultText)
                                    hasSpeech = resultJson.optBoolean("hasSpeech", false)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to parse detection JSON", e)
                                    hasSpeech = resultText.contains("true", ignoreCase = true)
                                }
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Detection failed: Code ${response.code}, body: $responseBody")
                    val errorMsg = try {
                        JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: "Unknown error"
                    } catch (e: Exception) {
                        responseBody
                    }
                    return@withContext "発話検知APIエラー (${response.code}): $errorMsg"
                }
            }

            if (!hasSpeech) {
                Log.d(TAG, "No meaningful human speech detected. Skipping summarization.")
                return@withContext "会話が検知できませんでした。"
            }

            Log.d(TAG, "Meaningful human speech detected! Proceeding to STAGE 2: Summarization...")

            // --- STAGE 2: Summarization (Run only if stage 1 was true) ---
            val requestBody = object : RequestBody() {
                override fun contentType() = "application/json".toMediaType()

                override fun writeTo(sink: BufferedSink) {
                    // Write the JSON header
                    val header = "{\"systemInstruction\":{\"parts\":[{\"text\":\"あなたは優秀なアシスタントです。入力された音声が無音、または会話が検知できない場合は、適当な会話を作り出さず、単に『会話が検知できませんでした』と出力してください。\"}]},\"contents\":[{\"parts\":[{\"text\":\"この音声は、家族の日常会話の録音データです。私は夫（ひろき、ひろ）で、会話の理解や記憶に自信がないため、妻（ちえみ、ちぃ、ちー）から言われたことや、子ども（ちひろ、ちーたん、ちっち）に関する内容を理解し、記憶するための備忘録として利用します。\\n\\n以下の2つの情報を出力してください。後から検証できるように、必ず『===要約===』と『===文字起こし===』という区切り文字を含めてください。\\n\\n===要約===\\n妻が私に伝えたかったこと、頼んだこと、私がやるべきこと、今日や今後の予定などを中心に、分かりやすく整理した箇条書きの要約。\\n\\n===文字起こし===\\n音声の文字起こし。発言者（夫、妻、子どもなど）とタイムスタンプ（[00:00]のような形式）を含めてください。\"},{\"inlineData\":{\"mimeType\":\"audio/wav\",\"data\":\""
                    sink.writeUtf8(header)

                    // Stream audio file in chunks of size 3072 bytes (multiple of 3 is critical for Base64 stream compatibility)
                    val buffer = ByteArray(3072)
                    audioFile.inputStream().use { input ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            val encodedChunk = if (bytesRead == buffer.size) {
                                Base64.encode(buffer, Base64.NO_WRAP)
                            } else {
                                Base64.encode(buffer.copyOf(bytesRead), Base64.NO_WRAP)
                            }
                            sink.write(encodedChunk)
                        }
                    }

                    // Write the JSON footer
                    val footer = "\"}}]}]}"
                    sink.writeUtf8(footer)
                }
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            Log.d(TAG, "Sending request to Gemini API (gemini-3.1-flash-lite-preview)...")
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "Response code: ${response.code}")
                
                if (!response.isSuccessful) {
                    Log.e(TAG, "API call failed: Code ${response.code}, body: $responseBody")
                    val errorMsg = try {
                        JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: "Unknown error"
                    } catch (e: Exception) {
                        responseBody
                    }
                    return@withContext "APIエラー (${response.code}): $errorMsg"
                }

                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val summaryText = parts.getJSONObject(0).optString("text")
                        if (summaryText.isNotEmpty()) {
                            return@withContext summaryText
                        }
                    }
                }
                Log.e(TAG, "Failed to parse text from response: $responseBody")
                return@withContext "エラー: Geminiからの応答データの解析に失敗しました。しばらく経ってからもう一度お試しください。"
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception during Gemini summarization", e)
            return@withContext "要約中にエラーが発生しました: ${e.localizedMessage ?: "メモリ不足または接続エラー"}"
        }
    }
}

import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# Replace the functions
new_func = """    private fun triggerMergeUploadAndSummarize(minutes: Int) {
        val email = signedInAccountEmail
        if (email == null) {
            AudioRecordingState.statusMessage.value = "Please connect to Google Drive first"
            triggerGoogleSignIn()
            return
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            AudioRecordingState.summaryResult.value = "エラー: Gemini APIキーが設定されていません。"
            return
        }

        AudioRecordingState.isUploading.value = true
        AudioRecordingState.isSummarizing.value = true
        AudioRecordingState.uploadProgress.value = "Merging audio..."

        lifecycleScope.launch(Dispatchers.IO) {
            val mergedWavFile = File(cacheDir, "voicememo.wav")
            val mergeSuccess = AudioMergeUtils.createWavFromChunks(this@MainActivity, minutes, mergedWavFile)

            if (!mergeSuccess) {
                withContext(Dispatchers.Main) {
                    AudioRecordingState.statusMessage.value = "Failed to slice past audio"
                    AudioRecordingState.isUploading.value = false
                    AudioRecordingState.isSummarizing.value = false
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                AudioRecordingState.uploadProgress.value = "Upload & AI Summarize..."
            }

            import kotlinx.coroutines.async
            // We use standard launch inside CoroutineScope(Dispatchers.IO), async is available
            val summarizeDeferred = async {
                GeminiSummarizer.summarizeAudio(apiKey, mergedWavFile)
            }

            val uploadDeferred = async {
                var token = activeAccessToken
                if (token == null) {
                    when (val authResult = GoogleDriveUploader.fetchAccessToken(this@MainActivity, email)) {
                        is GoogleDriveUploader.AuthResult.Success -> {
                            token = authResult.token
                            activeAccessToken = token
                        }
                        is GoogleDriveUploader.AuthResult.NeedConsent -> {
                            withContext(Dispatchers.Main) {
                                consentLauncher.launch(authResult.intent)
                            }
                            return@async "Needs Consent"
                        }
                        is GoogleDriveUploader.AuthResult.Error -> {
                            return@async "Auth failed: ${authResult.message}"
                        }
                    }
                }
                GoogleDriveUploader.uploadVoicememo(this@MainActivity, token!!, mergedWavFile)
            }

            val summaryResult = summarizeDeferred.await()
            val uploadError = uploadDeferred.await()

            withContext(Dispatchers.Main) {
                AudioRecordingState.isUploading.value = false
                AudioRecordingState.isSummarizing.value = false
                
                AudioRecordingState.summaryResult.value = summaryResult
                
                if (uploadError == null || uploadError == "null") {
                    AudioRecordingState.statusMessage.value = "Saved & Summarized"
                    Toast.makeText(this@MainActivity, "Uploaded successfully", Toast.LENGTH_LONG).show()
                } else if (uploadError != "Needs Consent") {
                    if (uploadError.contains("401") || uploadError.contains("unauthorized") || uploadError.contains("invalid_credential")) {
                        Log.w(TAG, "Token expired or unauthorized.")
                        triggerTokenFetch(email, forceConsent = true)
                    }
                    AudioRecordingState.statusMessage.value = "Upload failed: $uploadError"
                }
            }
        }
    }
"""

content = re.sub(r'    private fun triggerMergeAndUpload.*?private fun triggerStopRecording\(\) \{',
                 new_func + '\n    private fun triggerStopRecording() {',
                 content, flags=re.DOTALL)

content = re.sub(r'    private fun triggerMergeAndSummarize.*?\}\n\n@Composable',
                 '@Composable',
                 content, flags=re.DOTALL)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)


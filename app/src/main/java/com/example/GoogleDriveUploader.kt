package com.example

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object GoogleDriveUploader {
    private const val TAG = "GoogleDriveUploader"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    sealed class AuthResult {
        data class Success(val token: String) : AuthResult()
        data class NeedConsent(val intent: android.content.Intent) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    fun fetchAccessToken(context: Context, accountName: String): AuthResult {
        val scope = "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive"
        return try {
            val account = Account(accountName, "com.google")
            val token = GoogleAuthUtil.getToken(context, account, scope)
            AuthResult.Success(token)
        } catch (e: UserRecoverableAuthException) {
            Log.w(TAG, "User recoverable auth exception", e)
            AuthResult.NeedConsent(e.intent ?: android.content.Intent())
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching access token", e)
            AuthResult.Error(e.message ?: "Failed to get access token")
        }
    }

    fun clearTokenCache(context: Context, token: String) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear token cache", e)
        }
    }

    fun uploadVoicememo(context: Context, accessToken: String, file: File, summaryText: String? = null): String? {
        try {
            // Step 1: Find or Create the `/app` folder
            val appFolderId = findOrCreateAppFolder(accessToken)
            if (appFolderId == null) {
                return "Failed to find or create /app folder"
            }

            // Step 2: Find or Create the `{yyyymmdd}` subfolder
            val sdfDate = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
            val dateFolderName = sdfDate.format(java.util.Date())
            val dateFolderId = findOrCreateSubFolder(accessToken, appFolderId, dateFolderName)
            if (dateFolderId == null) {
                return "Failed to find or create date folder ($dateFolderName)"
            }

            // Step 3: Use the current time as filename (HHmmss)
            val sdfTime = java.text.SimpleDateFormat("HHmmss", java.util.Locale.getDefault())
            val baseName = sdfTime.format(java.util.Date())
            val timeFileName = "$baseName.wav"

            // Step 4: Upload the new wav file with the formatted name
            val audioSuccess = uploadFileToFolder(accessToken, dateFolderId, timeFileName, file, "audio/wav")
            if (!audioSuccess) {
                return "Audio upload request failed"
            }

            // Step 5: If summaryText is provided, upload it as a .txt file
            if (summaryText != null && summaryText.trim().isNotEmpty()) {
                val txtFileName = "$baseName.txt"
                val tempFile = File(context.cacheDir, "temp_summary.txt")
                tempFile.writeText(summaryText, Charsets.UTF_8)
                try {
                    val textSuccess = uploadFileToFolder(accessToken, dateFolderId, txtFileName, tempFile, "text/plain")
                    if (!textSuccess) {
                        Log.e(TAG, "Failed to upload summary text file")
                    }
                } finally {
                    try {
                        tempFile.delete()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }

            return null

        } catch (e: Exception) {
            Log.e(TAG, "Upload failed with exception", e)
            return e.message ?: "Unknown upload error"
        }
    }

    private fun findOrCreateSubFolder(accessToken: String, parentFolderId: String, folderName: String): String? {
        val query = "name = '$folderName' and '$parentFolderId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val url = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Search subfolder failed: ${response.code} $bodyStr")
                    return null
                }

                val json = JSONObject(bodyStr)
                val filesArray = json.optJSONArray("files")
                if (filesArray != null && filesArray.length() > 0) {
                    return filesArray.getJSONObject(0).getString("id")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching subfolder", e)
            return null
        }

        // Subfolder not found, create it under parentFolderId
        Log.d(TAG, "Subfolder '$folderName' not found under $parentFolderId. Creating it...")
        val createUrl = "https://www.googleapis.com/drive/v3/files"
        val payload = JSONObject().apply {
            put("name", folderName)
            put("mimeType", "application/vnd.google-apps.folder")
            val parentsArray = org.json.JSONArray().apply {
                put(parentFolderId)
            }
            put("parents", parentsArray)
        }

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = RequestBody.create(mediaType, payload.toString())

        val createRequest = Request.Builder()
            .url(createUrl)
            .header("Authorization", "Bearer $accessToken")
            .post(body)
            .build()

        try {
            client.newCall(createRequest).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Create subfolder failed: ${response.code} $bodyStr")
                    return null
                }
                val json = JSONObject(bodyStr)
                return json.getString("id")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating subfolder", e)
            return null
        }
    }

    private fun findOrCreateAppFolder(accessToken: String): String? {
        // Query to find a folder named 'app'
        val query = "name = 'app' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val url = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Search folder failed: ${response.code} $bodyStr")
                    return null
                }

                val json = JSONObject(bodyStr)
                val filesArray = json.optJSONArray("files")
                if (filesArray != null && filesArray.length() > 0) {
                    // Found existing folder
                    return filesArray.getJSONObject(0).getString("id")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching folder", e)
            return null
        }

        // Folder not found, create it
        Log.d(TAG, "'app' folder not found. Creating it...")
        val createUrl = "https://www.googleapis.com/drive/v3/files"
        val payload = JSONObject().apply {
            put("name", "app")
            put("mimeType", "application/vnd.google-apps.folder")
        }

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = RequestBody.create(mediaType, payload.toString())

        val createRequest = Request.Builder()
            .url(createUrl)
            .header("Authorization", "Bearer $accessToken")
            .post(body)
            .build()

        try {
            client.newCall(createRequest).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Create folder failed: ${response.code} $bodyStr")
                    return null
                }
                val json = JSONObject(bodyStr)
                return json.getString("id")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating folder", e)
            return null
        }
    }

    private fun deleteExistingVoicememo(accessToken: String, folderId: String) {
        // Find existing 'voicememo.wav' in this folder
        val query = "name = 'voicememo.wav' and '$folderId' in parents and trashed = false"
        val url = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val fileIdsToDelete = mutableListOf<String>()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    val filesArray = json.optJSONArray("files")
                    if (filesArray != null) {
                        for (i in 0 until filesArray.length()) {
                            fileIdsToDelete.add(filesArray.getJSONObject(i).getString("id"))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for existing voicememo files", e)
        }

        // Delete each found file
        for (fileId in fileIdsToDelete) {
            Log.d(TAG, "Deleting old voicememo file: $fileId")
            val deleteRequest = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId")
                .header("Authorization", "Bearer $accessToken")
                .delete()
                .build()
            try {
                client.newCall(deleteRequest).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete old file $fileId", e)
            }
        }
    }

    private fun uploadFileToFolder(accessToken: String, folderId: String, fileName: String, file: File, mimeType: String): Boolean {
        val boundary = "drive_uploader_boundary"
        val metadataJson = JSONObject().apply {
            put("name", fileName)
            val parentsArray = org.json.JSONArray().apply {
                put(folderId)
            }
            put("parents", parentsArray)
        }.toString()

        val requestBody = object : RequestBody() {
            override fun contentType() = "multipart/related; boundary=$boundary".toMediaTypeOrNull()

            override fun writeTo(sink: BufferedSink) {
                sink.writeUtf8("--$boundary\r\n")
                sink.writeUtf8("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                sink.writeUtf8(metadataJson)
                sink.writeUtf8("\r\n--$boundary\r\n")
                sink.writeUtf8("Content-Type: $mimeType\r\n\r\n")

                file.source().use { source ->
                    sink.writeAll(source)
                }

                sink.writeUtf8("\r\n--$boundary--\r\n")
            }
        }

        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            .header("Authorization", "Bearer $accessToken")
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Log.d(TAG, "Upload success! Response: $bodyStr")
                    true
                } else {
                    Log.e(TAG, "Upload failed with status ${response.code}: $bodyStr")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed with exception", e)
            false
        }
    }
}

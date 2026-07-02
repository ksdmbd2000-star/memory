package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var signedInAccountEmail by mutableStateOf<String?>(null)
    private var activeAccessToken by mutableStateOf<String?>(null)
    private var showAuthHelpDialog by mutableStateOf(false)
    private lateinit var repository: SummaryRecordRepository
    private lateinit var chatLogRepository: ChatLogRepository

    private fun getAppSignaturesSHA1(): String {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (signatures != null && signatures.isNotEmpty()) {
                val cert = signatures[0].toByteArray()
                val md = MessageDigest.getInstance("SHA-1")
                val publicKey = md.digest(cert)
                val hexString = StringBuilder()
                for (i in publicKey.indices) {
                    val appendString = Integer.toHexString(0xFF and publicKey[i].toInt())
                        .uppercase(Locale.US)
                    if (appendString.length == 1) {
                        hexString.append("0")
                    }
                    hexString.append(appendString)
                    if (i < publicKey.size - 1) {
                        hexString.append(":")
                    }
                }
                return hexString.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting SHA1", e)
        }
        return "Could not retrieve SHA-1"
    }

    private fun saveSelectedAccount(email: String?) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putString("selected_account", email).apply()
    }

    private fun getSelectedAccount(): String? {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        return prefs.getString("selected_account", null)
    }

    // OAuth Consent launcher (for scopes)
    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            signedInAccountEmail?.let { email ->
                triggerTokenFetch(email, forceConsent = false)
            }
        } else {
            AudioRecordingState.statusMessage.value = "Drive authorization was denied"
            AudioRecordingState.isUploading.value = false
        }
    }

    // Main Google Account Chooser Launcher
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "googleSignInLauncher result: resultCode=${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            val accountName = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrEmpty()) {
                signedInAccountEmail = accountName
                saveSelectedAccount(accountName)
                Log.d(TAG, "Google Account success: $accountName")
                triggerTokenFetch(accountName, forceConsent = false)
            } else {
                AudioRecordingState.statusMessage.value = "Sign-In Failed: No account selected"
            }
        } else {
            AudioRecordingState.statusMessage.value = "Sign-In Cancelled"
            AudioRecordingState.isUploading.value = false
        }
    }

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (recordAudioGranted) {
            Log.d(TAG, "Record audio permission granted. Starting recording service...")
            startRecordingService()
        } else {
            Log.w(TAG, "Record audio permission was denied")
            Toast.makeText(this, "Microphone permission is required to record audio", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(this)
        repository = SummaryRecordRepository(database.summaryRecordDao())
        chatLogRepository = ChatLogRepository(database.chatLogDao())

        // Restored selected account
        val savedAccount = getSelectedAccount()
        if (savedAccount != null) {
            signedInAccountEmail = savedAccount
            Log.d(TAG, "Restored selected account: $savedAccount")
            // Silently fetch token in background
            triggerTokenFetch(savedAccount, forceConsent = false)
        }

        // Check and request permissions
        checkAndRequestPermissions()

        setContent {
            val records by repository.allRecords.collectAsStateWithLifecycle(initialValue = emptyList())
            val chatLogs by chatLogRepository.allLogs.collectAsStateWithLifecycle(initialValue = emptyList())

            Box(modifier = Modifier.fillMaxSize()) {
                BentoVoiceRecorderApp(
                    signedInEmail = signedInAccountEmail,
                    records = records,
                    chatLogs = chatLogs,
                    onDeleteRecord = { record ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            repository.deleteById(record.id)
                        }
                    },
                    onClearAllRecords = {
                        lifecycleScope.launch(Dispatchers.IO) {
                            repository.deleteAll()
                        }
                    },
                    onDeleteChatLog = { id ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            chatLogRepository.deleteById(id)
                        }
                    },
                    onClearAllChatLogs = {
                        lifecycleScope.launch(Dispatchers.IO) {
                            chatLogRepository.clearAll()
                        }
                    },
                    onSignInClick = { triggerGoogleSignIn() },
                    onSignOutClick = { triggerSignOut() },
                    onSaveAndSummarizeClick = { minutes -> triggerMergeUploadAndSummarize(minutes) },
                    onUploadOnlyClick = { minutes -> triggerMergeAndUploadOnly(minutes) },
                    onRequestShowAuthHelp = { showAuthHelpDialog = true },
                    onStartRecordingClick = { triggerStartRecording() },
                    onStopRecordingClick = { triggerStopRecording() }
                )

                if (showAuthHelpDialog) {
                    AuthHelpDialog(
                        packageName = packageName,
                        sha1 = getAppSignaturesSHA1(),
                        onDismiss = { showAuthHelpDialog = false }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val allGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            startRecordingService()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun startRecordingService() {
        val intent = Intent(this, AudioRecordingService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecordingService", e)
        }
    }

    private fun triggerGoogleSignIn() {
        try {
            val intent = android.accounts.AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                null,
                null,
                null,
                null
            )
            googleSignInLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start account chooser", e)
            AudioRecordingState.statusMessage.value = "Chooser Error: ${e.message}"
        }
    }

    private fun triggerSignOut() {
        signedInAccountEmail = null
        activeAccessToken = null
        saveSelectedAccount(null)
        AudioRecordingState.statusMessage.value = "Signed out"
        Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
    }

    private fun triggerTokenFetch(email: String, forceConsent: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            if (forceConsent && activeAccessToken != null) {
                GoogleDriveUploader.clearTokenCache(this@MainActivity, activeAccessToken!!)
                activeAccessToken = null
            }

            when (val result = GoogleDriveUploader.fetchAccessToken(this@MainActivity, email)) {
                is GoogleDriveUploader.AuthResult.Success -> {
                    activeAccessToken = result.token
                    Log.d(TAG, "Successfully acquired access token")
                    withContext(Dispatchers.Main) {
                        AudioRecordingState.statusMessage.value = "Google Drive Connected"
                    }
                }
                is GoogleDriveUploader.AuthResult.NeedConsent -> {
                    Log.d(TAG, "Consent is required from user")
                    withContext(Dispatchers.Main) {
                        consentLauncher.launch(result.intent)
                    }
                }
                is GoogleDriveUploader.AuthResult.Error -> {
                    Log.e(TAG, "Failed to fetch access token: ${result.message}")
                    withContext(Dispatchers.Main) {
                        if (result.message.contains("UnregisteredOnApiConsole", ignoreCase = true)) {
                            AudioRecordingState.statusMessage.value = "Auth failed: UnregisteredOnApiConsole"
                            showAuthHelpDialog = true
                        } else {
                            AudioRecordingState.statusMessage.value = "Auth Error: ${result.message}"
                        }
                    }
                }
            }
        }
    }

    private fun triggerMergeUploadAndSummarize(minutes: Int) {
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
                AudioRecordingState.uploadProgress.value = "AI Summarizing..."
            }

            val summaryResult = GeminiSummarizer.summarizeAudio(apiKey, mergedWavFile)

            withContext(Dispatchers.Main) {
                AudioRecordingState.uploadProgress.value = "Uploading to Drive..."
            }

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
                        withContext(Dispatchers.Main) {
                            AudioRecordingState.isUploading.value = false
                            AudioRecordingState.isSummarizing.value = false
                        }
                        return@launch
                    }
                    is GoogleDriveUploader.AuthResult.Error -> {
                        withContext(Dispatchers.Main) {
                            AudioRecordingState.statusMessage.value = "Auth failed: ${authResult.message}"
                            AudioRecordingState.isUploading.value = false
                            AudioRecordingState.isSummarizing.value = false
                        }
                        return@launch
                    }
                }
            }

            val uploadError = GoogleDriveUploader.uploadVoicememo(this@MainActivity, token!!, mergedWavFile, summaryText = summaryResult)

            withContext(Dispatchers.Main) {
                AudioRecordingState.isUploading.value = false
                AudioRecordingState.isSummarizing.value = false
                
                AudioRecordingState.summaryResult.value = summaryResult
                
                if (uploadError == null || uploadError == "null") {
                    AudioRecordingState.statusMessage.value = "Saved & Summarized"
                    Toast.makeText(this@MainActivity, "Uploaded successfully", Toast.LENGTH_LONG).show()
                } else {
                    if (uploadError.contains("401") || uploadError.contains("unauthorized") || uploadError.contains("invalid_credential")) {
                        Log.w(TAG, "Token expired or unauthorized.")
                        triggerTokenFetch(email, forceConsent = true)
                    }
                    AudioRecordingState.statusMessage.value = "Upload failed: $uploadError"
                }
            }

            if (summaryResult != null && !summaryResult.startsWith("エラー") && !summaryResult.startsWith("APIエラー") && !summaryResult.startsWith("要約中にエラー")) {
                repository.insert(
                    SummaryRecord(
                        durationMinutes = minutes,
                        summaryText = summaryResult
                    )
                )
            }
        }
    }

    private fun triggerMergeAndUploadOnly(minutes: Int) {
        val email = signedInAccountEmail
        if (email == null) {
            AudioRecordingState.statusMessage.value = "Please connect to Google Drive first"
            triggerGoogleSignIn()
            return
        }

        AudioRecordingState.isUploading.value = true
        AudioRecordingState.isSummarizing.value = false
        AudioRecordingState.uploadProgress.value = "Merging audio..."

        lifecycleScope.launch(Dispatchers.IO) {
            val mergedWavFile = File(cacheDir, "voicememo.wav")
            val mergeSuccess = AudioMergeUtils.createWavFromChunks(this@MainActivity, minutes, mergedWavFile)

            if (!mergeSuccess) {
                withContext(Dispatchers.Main) {
                    AudioRecordingState.statusMessage.value = "Failed to slice past audio"
                    AudioRecordingState.isUploading.value = false
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                AudioRecordingState.uploadProgress.value = "Uploading to Drive..."
            }

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
                        withContext(Dispatchers.Main) {
                            AudioRecordingState.isUploading.value = false
                        }
                        return@launch
                    }
                    is GoogleDriveUploader.AuthResult.Error -> {
                        withContext(Dispatchers.Main) {
                            AudioRecordingState.statusMessage.value = "Auth failed: ${authResult.message}"
                            AudioRecordingState.isUploading.value = false
                        }
                        return@launch
                    }
                }
            }

            val uploadError = GoogleDriveUploader.uploadVoicememo(this@MainActivity, token!!, mergedWavFile)

            withContext(Dispatchers.Main) {
                AudioRecordingState.isUploading.value = false
                
                if (uploadError == null || uploadError == "null") {
                    AudioRecordingState.statusMessage.value = "Uploaded Successfully"
                    AudioRecordingState.summaryResult.value = "要約なしでアップロード完了しました。"
                    Toast.makeText(this@MainActivity, "Uploaded successfully", Toast.LENGTH_LONG).show()
                } else {
                    if (uploadError.contains("401") || uploadError.contains("unauthorized") || uploadError.contains("invalid_credential")) {
                        Log.w(TAG, "Token expired or unauthorized.")
                        triggerTokenFetch(email, forceConsent = true)
                    }
                    AudioRecordingState.statusMessage.value = "Upload failed: $uploadError"
                }
            }

            if (uploadError == null || uploadError == "null") {
                repository.insert(
                    SummaryRecord(
                        durationMinutes = minutes,
                        summaryText = "音声アップロード完了 (${minutes}分) - 要約なし"
                    )
                )
            }
        }
    }

    private fun triggerStopRecording() {
        Log.d(TAG, "Stopping recording service manually")
        val intent = Intent(this, AudioRecordingService::class.java)
        stopService(intent)
        AudioRecordingState.isRecording.value = false
        AudioRecordingState.statusMessage.value = "Recording Stopped"
    }

    private fun triggerStartRecording() {
        Log.d(TAG, "Starting recording service manually")
        checkAndRequestPermissions()
    }
}

@Composable
fun BentoVoiceRecorderApp(
    signedInEmail: String?,
    records: List<SummaryRecord> = emptyList(),
    chatLogs: List<ChatLog> = emptyList(),
    onDeleteRecord: (SummaryRecord) -> Unit = {},
    onClearAllRecords: () -> Unit = {},
    onDeleteChatLog: (Int) -> Unit = {},
    onClearAllChatLogs: () -> Unit = {},
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onSaveAndSummarizeClick: (Int) -> Unit,
    onUploadOnlyClick: (Int) -> Unit,
    onRequestShowAuthHelp: () -> Unit = {},
    onStartRecordingClick: () -> Unit = {},
    onStopRecordingClick: () -> Unit = {}
) {
    val isRecording by AudioRecordingState.isRecording.collectAsStateWithLifecycle()
    val recordingDurationMillis by AudioRecordingState.recordingDurationMillis.collectAsStateWithLifecycle()
    val currentAmplitude by AudioRecordingState.currentAmplitude.collectAsStateWithLifecycle()
    val statusMessage by AudioRecordingState.statusMessage.collectAsStateWithLifecycle()
    val isUploading by AudioRecordingState.isUploading.collectAsStateWithLifecycle()
    val uploadProgress by AudioRecordingState.uploadProgress.collectAsStateWithLifecycle()
    val isSummarizing by AudioRecordingState.isSummarizing.collectAsStateWithLifecycle()
    val summaryResult by AudioRecordingState.summaryResult.collectAsStateWithLifecycle()

    var selectedRangeMinutes by remember { mutableIntStateOf(5) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var activeTab by remember { mutableIntStateOf(0) }

    // Format milliseconds into MM:SS format
    val formattedDuration = remember(recordingDurationMillis) {
        val totalSecs = recordingDurationMillis / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        String.format("%02d:%02d", mins, secs)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFFEF7F4)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFFFDAD4), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Recorder Logo",
                            tint = Color(0xFF410002),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Voice Rewind",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF201A19),
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Drive Voice Recorder",
                            fontSize = 11.sp,
                            color = Color(0xFF534341),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Sign-In/Profile Button
                if (signedInEmail == null) {
                    Button(
                        onClick = onSignInClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFDAD4),
                            contentColor = Color(0xFF410002)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("connect_drive_button")
                    ) {
                        Text("Connect Drive", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .background(Color(0xFFF4E0D9), RoundedCornerShape(20.dp))
                            .clickable { onSignOutClick() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color(0xFF8F4C38), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = signedInEmail.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Sign Out",
                            fontSize = 11.sp,
                            color = Color(0xFF534341),
                            fontWeight = FontWeight.Bold,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                }
            }

            // Main Body Content depending on activeTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (activeTab == 0) {
                    // Scrollable Bento Grid Main Section
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                // Recording Live Card (span 2)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color(0xFFFFDAD4), RoundedCornerShape(32.dp))
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Live Status Label with Stop/Start control
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 0.3f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "alpha"
                                )

                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(if (isRecording) Color.Red.copy(alpha = alpha) else Color.Gray, CircleShape)
                                )
                                Text(
                                    text = if (isRecording) "RECORDING LIVE" else "RECORDING STOPPED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF410002),
                                    letterSpacing = 1.5.sp
                                )
                            }

                            // Interactive Record Stop/Start Button
                            IconButton(
                                onClick = {
                                    if (isRecording) onStopRecordingClick() else onStartRecordingClick()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFF8F4C38).copy(alpha = 0.12f), CircleShape)
                                    .testTag("record_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                                    tint = Color(0xFF8F4C38),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Timer and Waveform row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    modifier = Modifier
                                ) {
                                    Text(
                                        text = formattedDuration,
                                        fontSize = 38.sp,
                                        fontWeight = FontWeight.Light,
                                        color = Color(0xFF410002),
                                        letterSpacing = (-1).sp
                                    )
                                    Text(
                                        text = " / 30:00",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = Color(0xFF410002).copy(alpha = 0.6f),
                                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                    )
                                }
                            }

                            // Interactive Live Audio Waves
                            WaveformVisualizer(
                                amplitude = if (isRecording) currentAmplitude else 0.05f,
                                modifier = Modifier
                                    .width(90.dp)
                                    .height(40.dp)
                                    .padding(bottom = 4.dp)
                            )
                        }
                    }
                }

                // Grid Row 2: Select Range (span 1) and Storage (span 1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Select Range Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(110.dp)
                            .background(Color(0xFFF4E0D9), RoundedCornerShape(28.dp))
                            .clickable { dropdownExpanded = true }
                            .padding(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "SELECT RANGE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF534341).copy(alpha = 0.7f),
                                letterSpacing = 1.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$selectedRangeMinutes min",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF201A19)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select minutes",
                                    tint = Color(0xFF201A19).copy(alpha = 0.5f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.background(Color(0xFFFEF7F4))
                        ) {
                            val ranges = listOf(5, 10, 15, 20, 25, 30)
                            ranges.forEach { range ->
                                DropdownMenuItem(
                                    text = { Text("$range min", color = Color(0xFF201A19), fontWeight = FontWeight.Medium) },
                                    onClick = {
                                        selectedRangeMinutes = range
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Storage Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(110.dp)
                            .background(Color(0xFFD8E2FF), RoundedCornerShape(28.dp))
                            .padding(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "STORAGE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF001945).copy(alpha = 0.6f),
                                letterSpacing = 1.sp
                            )

                            Column {
                                Text(
                                    text = "Google Drive",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF001945)
                                )
                                Text(
                                    text = "/app/voicememo",
                                    fontSize = 11.sp,
                                    color = Color(0xFF001945).copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Chronological Storage Format Status Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(28.dp))
                        .border(1.dp, Color(0xFFF4E0D9), RoundedCornerShape(28.dp))
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFFFFDAD4), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Save Option",
                                    tint = Color(0xFF410002),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "時系列フォルダ保存",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF201A19)
                                )
                                Text(
                                    text = "上書きせずに `/app/{日付}/{時間}.wav` (要約テキストは `.txt`) で保存します",
                                    fontSize = 11.sp,
                                    color = Color(0xFF534341)
                                )
                            }
                        }

                        // A nice visual "Auto" badge
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE8DEF8), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "AUTO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D192B)
                            )
                        }
                    }
                }

                // Save and Summarize Button (span 2)
                Button(
                    onClick = { onSaveAndSummarizeClick(selectedRangeMinutes) },
                    enabled = !isUploading && !isSummarizing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF673AB7), // Rich Indigo/Purple for AI/Gemini theme
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF673AB7).copy(alpha = 0.5f),
                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("save_and_summarize_button"),
                    contentPadding = PaddingValues()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isUploading && isSummarizing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "保存＆要約中...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Save and Summarize icon",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "直近 ${selectedRangeMinutes}分を保存＆AI要約",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Only Upload Button (span 2)
                Button(
                    onClick = { onUploadOnlyClick(selectedRangeMinutes) },
                    enabled = !isUploading && !isSummarizing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8F4C38), // Warm primary rust
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF8F4C38).copy(alpha = 0.5f),
                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("upload_only_button"),
                    contentPadding = PaddingValues()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isUploading && !isSummarizing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.2.dp
                            )
                            Text(
                                text = "アップロード中...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Upload only icon",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "要約なしでアップロードのみ",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Status Message Box (Bento helper to show helpful info cleanly)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFEF7F4), RoundedCornerShape(16.dp))
                        .clickable {
                            if (statusMessage.contains("UnregisteredOnApiConsole", ignoreCase = true) ||
                                statusMessage.contains("Auth failed", ignoreCase = true)) {
                                onRequestShowAuthHelp()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Status: $statusMessage",
                            fontSize = 12.sp,
                            color = Color(0xFF534341),
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        if (statusMessage.contains("UnregisteredOnApiConsole", ignoreCase = true) ||
                            statusMessage.contains("Auth failed", ignoreCase = true)) {
                            Text(
                                text = "⚠️ タップして解決方法とSHA-1を確認",
                                fontSize = 11.sp,
                                   modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } // End of Column at 590
            } // End of activeTab == 0

            if (activeTab == 1) {
                // Library placeholder
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_gallery),
                contentDescription = "Library Placeholder",
                tint = Color(0xFF8F4C38).copy(alpha = 0.4f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Google Drive 連携中",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF201A19)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "録音データは自動的にGoogleドライブの「/app/voicememo」フォルダへアップロードされます。",
                fontSize = 14.sp,
                color = Color(0xFF534341),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
                } // End of Column
            } // End of activeTab == 1

            if (activeTab == 2) {
                // Room DB History Section
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    HistoryScreen(
                        records = records,
                        onDeleteRecord = onDeleteRecord,
                        onClearAllRecords = onClearAllRecords,
                        onRecordClick = { record ->
                            AudioRecordingState.summaryResult.value = record.summaryText
                        }
                    )
                }
            } // End of activeTab == 2

            if (activeTab == 3) {
                // Camera Overlay Section
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    CameraOverlayScreen()
                }
            } // End of activeTab == 3

            if (activeTab == 4) {
                // Live Chat STT Section
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    LiveChatScreen(
                        chatLogs = chatLogs,
                        onDeleteLog = onDeleteChatLog,
                        onClearAllLogs = onClearAllChatLogs,
                        onStartRecording = onStartRecordingClick
                    )
                }
            } // End of activeTab == 4
        } // End of Main Content Box

    // Bottom Navigation Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(color = Color(0xFFF4E0D9), thickness = 1.dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    color = Color(0xFFFEF7F4)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Capture Tab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable { activeTab = 0 }
                                .padding(8.dp)
                                .testTag("capture_tab")
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(64.dp)
                                    .height(32.dp)
                                    .background(if (activeTab == 0) Color(0xFFFFDAD4) else Color.Transparent, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Capture Mode",
                                    tint = if (activeTab == 0) Color(0xFF8F4C38) else Color(0xFF534341).copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Capture",
                                fontSize = 11.sp,
                                fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (activeTab == 0) Color(0xFF8F4C38) else Color(0xFF534341).copy(alpha = 0.6f)
                            )
                        }

                        // Library Tab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable { activeTab = 1 }
                                .padding(8.dp)
                                .testTag("library_tab")
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(64.dp)
                                    .height(32.dp)
                                    .background(if (activeTab == 1) Color(0xFFFFDAD4) else Color.Transparent, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(android.R.drawable.ic_menu_gallery),
                                    contentDescription = "Library",
                                    tint = if (activeTab == 1) Color(0xFF8F4C38) else Color(0xFF534341).copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Library",
                                fontSize = 11.sp,
                                fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (activeTab == 1) Color(0xFF8F4C38) else Color(0xFF534341).copy(alpha = 0.6f)
                            )
                        }

                        // History Tab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable { activeTab = 2 }
                                .padding(8.dp)
                                .testTag("history_tab")
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(64.dp)
                                    .height(32.dp)
                                    .background(if (activeTab == 2) Color(0xFFFFDAD4) else Color.Transparent, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(android.R.drawable.ic_menu_recent_history),
                                    contentDescription = "History",
                                    tint = if (activeTab == 2) Color(0xFF8F4C38) else Color(0xFF534341).copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "History",
                                fontSize = 11.sp,
                                fontWeight = if (activeTab == 2) FontWeight.Bold else FontWeight.Medium,
                                color = if (activeTab == 2) Color(0xFF8F4C38) else Color(0xFF534341).copy(alpha = 0.6f)
                            )
                        }

                        // Align Cam Tab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable { activeTab = 3 }
                                .padding(8.dp)
                                .testTag("align_cam_tab")
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(64.dp)
                                    .height(32.dp)
                                    .background(if (activeTab == 3) Color(0xFFFFDAD4) else Color.Transparent, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Align Cam",
                                    tint = if (activeTab == 3) Color(0xFF8F4C38) else Color(0xFF534341).copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Align Cam",
                                fontSize = 11.sp,
                                fontWeight = if (activeTab == 3) FontWeight.Bold else FontWeight.Medium,
                                color = if (activeTab == 3) Color(0xFF8F4C38) else Color(0xFF534341).copy(alpha = 0.6f)
                            )
                        }

                        // Live Chat Tab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable { activeTab = 4 }
                                .padding(8.dp)
                                .testTag("live_chat_tab")
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(64.dp)
                                    .height(32.dp)
                                    .background(if (activeTab == 4) Color(0xFFFFDAD4) else Color.Transparent, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forum,
                                    contentDescription = "Live Chat",
                                    tint = if (activeTab == 4) Color(0xFF8F4C38) else Color(0xFF534341).copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Live Log",
                                fontSize = 11.sp,
                                fontWeight = if (activeTab == 4) FontWeight.Bold else FontWeight.Medium,
                                color = if (activeTab == 4) Color(0xFF8F4C38) else Color(0xFF534341).copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }

        // Summary Result Dialog
        if (summaryResult != null) {
            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
            val context = LocalContext.current
            
            var selectedTabIndex by remember { mutableIntStateOf(0) }
            val tabs = listOf("要約", "文字起こし")
            
            val resultString = summaryResult ?: ""
            val summaryMarker = "===要約==="
            val transcriptMarker = "===文字起こし==="
            
            val summaryPart: String
            val transcriptPart: String
            
            if (resultString.contains(summaryMarker) && resultString.contains(transcriptMarker)) {
                val split1 = resultString.split(transcriptMarker)
                transcriptPart = split1.last().trim()
                summaryPart = split1.first().replace(summaryMarker, "").trim()
            } else if (resultString.contains("===文字起こし===")) {
                val split = resultString.split("===文字起こし===")
                summaryPart = split[0].replace("===要約===", "").trim()
                transcriptPart = split.getOrNull(1)?.trim() ?: ""
            } else {
                summaryPart = resultString
                transcriptPart = "文字起こしデータがありません"
            }

            AlertDialog(
                onDismissRequest = { AudioRecordingState.summaryResult.value = null },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Icon",
                            tint = Color(0xFF673AB7),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "解析結果",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF201A19)
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = Color.Transparent,
                            contentColor = Color(0xFF673AB7)
                        ) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTabIndex == index,
                                    onClick = { selectedTabIndex = index },
                                    text = { Text(title, fontWeight = FontWeight.Bold) }
                                )
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .background(Color(0xFFF3E5F5), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFE1BEE7), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                Text(
                                    text = if (selectedTabIndex == 0) summaryPart else transcriptPart,
                                    fontSize = 14.sp,
                                    color = Color(0xFF201A19),
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                val textToCopy = if (selectedTabIndex == 0) summaryPart else transcriptPart
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(textToCopy))
                                Toast.makeText(context, "${tabs[selectedTabIndex]}をコピーしました", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy text",
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF673AB7)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("コピー", color = Color(0xFF673AB7), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { AudioRecordingState.summaryResult.value = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
                        ) {
                            Text("閉じる", color = Color.White)
                        }
                    }
                },
                containerColor = Color.White
            )
        }
    }
}

@Composable
fun WaveformVisualizer(amplitude: Float, modifier: Modifier = Modifier) {
    // Keep a list of historical amplitudes to draw a scrolling wave
    val waveList = remember { mutableStateListOf<Float>().apply {
        repeat(20) { add(0.1f) }
    } }
    
    // Add current amplitude and slide
    LaunchedEffect(amplitude) {
        waveList.add(amplitude.coerceAtLeast(0.05f))
        if (waveList.size > 20) {
            waveList.removeAt(0)
        }
    }
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val barCount = waveList.size
        val gap = 4.dp.toPx()
        val totalGaps = (barCount - 1) * gap
        val barWidth = (width - totalGaps) / barCount
        
        for (i in waveList.indices) {
            val amp = waveList[i]
            val barHeight = amp * height * 0.9f
            val x = i * (barWidth + gap)
            val y = (height - barHeight) / 2
            
            drawRoundRect(
                color = Color(0xFF410002),
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

@Composable
fun AuthHelpDialog(
    packageName: String,
    sha1: String,
    onDismiss: () -> Unit
) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Google Drive連携エラーの解決方法",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF201A19)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Google Driveに接続するには、アプリ（パッケージ名とSHA-1証明書）をGoogle Cloud Consoleに登録する必要があります。",
                    fontSize = 13.sp,
                    color = Color(0xFF534341)
                )

                Text(
                    text = "【手順】\n1. Google Cloud Console (https://console.cloud.google.com/) にアクセスします。\n2. プロジェクトを選択、または新規作成します。\n3. 「APIとサービス」 > 「認証情報」を開きます。\n4. 「認証情報を作成」 > 「OAuth クライアント ID」をクリックします。\n5. アプリの種類を「Android」に設定します。\n6. 以下の「パッケージ名」と「SHA-1証明書」を入力して保存します。\n7. 「APIとサービス」 > 「ライブラリ」から「Google Drive API」を有効化してください。",
                    fontSize = 12.sp,
                    color = Color(0xFF534341),
                    lineHeight = 18.sp
                )

                HorizontalDivider(color = Color(0xFFF4E0D9))

                // Package name section
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "パッケージ名 (Package Name):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8F4C38)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFEF7F4), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFF4E0D9), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = packageName,
                            fontSize = 11.sp,
                            color = Color(0xFF201A19),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "コピー",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8F4C38),
                            modifier = Modifier
                                .clickable {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(packageName))
                                    Toast.makeText(context, "パッケージ名をコピーしました", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                // SHA-1 section
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "SHA-1 証明書フィンガープリント:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8F4C38)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFEF7F4), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFF4E0D9), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sha1,
                            fontSize = 11.sp,
                            color = Color(0xFF201A19),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "コピー",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8F4C38),
                            modifier = Modifier
                                .clickable {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(sha1))
                                    Toast.makeText(context, "SHA-1をコピーしました", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8F4C38))
            ) {
                Text("閉じる", color = Color.White)
            }
        },
        containerColor = Color.White
    )
}


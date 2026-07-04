package com.example

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LiveChatScreen(
    chatLogs: List<ChatLog>,
    onDeleteLog: (Int) -> Unit,
    onClearAllLogs: () -> Unit,
    onStartRecording: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lazyListState = rememberLazyListState()

    val isRecording by AudioRecordingState.isRecording.collectAsStateWithLifecycle()
    val isLiveChatActive by AudioRecordingState.isLiveChatActive.collectAsStateWithLifecycle()
    val liveChatStatus by AudioRecordingState.liveChatStatus.collectAsStateWithLifecycle()
    val activeStreamingText by AudioRecordingState.activeStreamingText.collectAsStateWithLifecycle()
    val debugLog by AudioRecordingState.debugLog.collectAsStateWithLifecycle()

    var isDebugLogExpanded by remember { mutableStateOf(true) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    // Auto scroll to top/bottom when a new transcript or active streaming changes
    LaunchedEffect(chatLogs.size, activeStreamingText) {
        if (chatLogs.isNotEmpty() || activeStreamingText.isNotEmpty()) {
            lazyListState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status & Toggle Header Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFEF0EB)
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pulsing connection status light
                        val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse"
                        )

                        val statusColor = when {
                            liveChatStatus == "Connected" -> Color(0xFF4CAF50)
                            liveChatStatus.startsWith("Connecting") -> Color(0xFFFF9800)
                            liveChatStatus.startsWith("Error") -> Color(0xFFF44336)
                            else -> Color(0xFF757575)
                        }

                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(statusColor.copy(alpha = if (liveChatStatus == "Connected") alpha else 1.0f))
                        )

                        Text(
                            text = "WebSocket: $liveChatStatus",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF3E2723)
                        )
                    }

                    // Clear button
                    if (chatLogs.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearConfirmation = true },
                            modifier = Modifier.testTag("clear_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear All Logs",
                                tint = Color(0xFFB00020)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (!isRecording) {
                                // If Always-On audio service isn't running, start it
                                onStartRecording()
                            }
                            // Toggle Gemini WebSocket Live Mode
                            AudioRecordingState.isLiveChatActive.value = !isLiveChatActive
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLiveChatActive) Color(0xFFB00020) else Color(0xFF8F4C38),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("toggle_live_stt_button")
                    ) {
                        Icon(
                            imageVector = if (isLiveChatActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isLiveChatActive) "Stop" else "Start",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isLiveChatActive) "ライブ文字起こしを停止" else "ライブ文字起こしを開始",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                if (isLiveChatActive && !isRecording) {
                    Text(
                        text = "マイク録音サービスを自動的に起動しています...",
                        fontSize = 12.sp,
                        color = Color(0xFFE65100),
                        fontWeight = FontWeight.Medium
                    )
                } else if (isLiveChatActive) {
                    Text(
                        text = "🎤 常時録音バッファからリアルタイム音声ストリームをGeminiに送信中...",
                        fontSize = 12.sp,
                        color = Color(0xFF3E2723),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Realtime WebSocket Debug Log Panel
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF262121)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDebugLogExpanded = !isDebugLogExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Debug Log Icon",
                            tint = Color(0xFFFFD54F)
                        )
                        Text(
                            text = "WebSocket Realtime Debug Log",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(debugLog))
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Debug Log",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                AudioRecordingState.debugLog.value = ""
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear Debug Log",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Icon(
                            imageVector = if (isDebugLogExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isDebugLogExpanded) "Collapse" else "Expand",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (isDebugLogExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(Color.Black, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        // Auto-scroll to bottom of the log when it changes
                        LaunchedEffect(debugLog) {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                        Text(
                            text = if (debugLog.isEmpty()) "No log data yet..." else debugLog,
                            color = Color(0xFF00FF00),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        )
                    }
                }
            }
        }

        // Subtitle header
        Text(
            text = "リアルタイム発言ログ (${chatLogs.size}件)",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color(0xFF534341)
        )

        // Logs Display Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (chatLogs.isEmpty() && activeStreamingText.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Forum,
                        contentDescription = "Empty chat",
                        tint = Color(0xFF8F4C38).copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "発言ログはありません",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF534341).copy(alpha = 0.6f),
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "上の開始ボタンを押してマイクに向かって話すと、Geminiがリアルタイムに音声を読み取りここに流します。",
                        textAlign = TextAlign.Center,
                        color = Color(0xFF534341).copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Active Streaming Transcription (Draft forming at bottom)
                    if (activeStreamingText.isNotEmpty()) {
                        item(key = "active_stream") {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFFFF3E0)
                                ),
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 32.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "Forming STT",
                                            tint = Color(0xFFFF9800),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "聞き取り中...",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFF9800)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = activeStreamingText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF3E2723),
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }

                    // 2. Historical Logs
                    items(chatLogs, key = { it.id }) { log ->
                        val dateString = remember(log.timestamp) {
                            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            sdf.format(Date(log.timestamp))
                        }

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chat_log_item_${log.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF8F4C38).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "発言",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF8F4C38)
                                            )
                                        }
                                        Text(
                                            text = dateString,
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = log.text,
                                        fontSize = 14.sp,
                                        color = Color(0xFF201A19),
                                        lineHeight = 20.sp
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(log.text))
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy text",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeleteLog(log.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete entry",
                                            tint = Color.Red.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog for clearing logs
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = {
                Text(text = "ログの全削除", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = "保存されているすべての発言ログを削除します。この操作は取り消せません。よろしいですか？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllLogs()
                        showClearConfirmation = false
                    }
                ) {
                    Text(text = "削除する", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(text = "キャンセル")
                }
            }
        )
    }
}

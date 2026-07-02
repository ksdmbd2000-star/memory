import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# Add imports
imports_to_add = """import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.mutableIntStateOf
"""
content = content.replace("import androidx.compose.runtime.mutableStateOf", "import androidx.compose.runtime.mutableStateOf\n" + imports_to_add)

old_dialog = """        // Summary Result Dialog
        if (summaryResult != null) {
            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
            val context = LocalContext.current
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
                            text = "Gemini AI要約結果",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF201A19)
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "過去 $selectedRangeMinutes 分の音声データの要約結果です：",
                            fontSize = 13.sp,
                            color = Color(0xFF534341),
                            fontWeight = FontWeight.Medium
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF3E5F5), RoundedCornerShape(12.dp)) // Light purple tint
                                .border(1.dp, Color(0xFFE1BEE7), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = summaryResult ?: "",
                                fontSize = 14.sp,
                                color = Color(0xFF201A19),
                                lineHeight = 22.sp
                            )
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
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(summaryResult ?: ""))
                                Toast.makeText(context, "要約テキストをコピーしました", Toast.LENGTH_SHORT).show()
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
        }"""

new_dialog = """        // Summary Result Dialog
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
                                .height(300.dp) // Fixed height to allow scrolling within
                                .background(Color(0xFFF3E5F5), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFE1BEE7), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = if (selectedTabIndex == 0) summaryPart else transcriptPart,
                                fontSize = 14.sp,
                                color = Color(0xFF201A19),
                                lineHeight = 22.sp
                            )
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
        }"""

content = content.replace(old_dialog, new_dialog)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)

import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

old_buttons = """                // Action Button (span 2)
                Button(
                    onClick = { onSavePastAudioClick(selectedRangeMinutes) },
                    enabled = !isUploading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8F4C38),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF8F4C38).copy(alpha = 0.5f),
                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .testTag("save_button"),
                    contentPadding = PaddingValues()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = uploadProgress,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Save Past Minutes icon",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Save Past ${selectedRangeMinutes}m",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Gemini AI Summarize Button (span 2)
                Button(
                    onClick = { onSummarizeClick(selectedRangeMinutes) },
                    enabled = !isSummarizing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF673AB7), // Rich Indigo/Purple for AI/Gemini theme
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF673AB7).copy(alpha = 0.5f),
                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .testTag("summarize_button"),
                    contentPadding = PaddingValues()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSummarizing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = "AI要約を作成中...",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome, // AutoAwesome is perfect for AI!
                                contentDescription = "Summarize icon",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "AIで過去 ${selectedRangeMinutes}分を要約する",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }"""

new_buttons = """                // Save and Summarize Button (span 2)
                Button(
                    onClick = { onSaveAndSummarizeClick(selectedRangeMinutes) },
                    enabled = !isUploading && !isSummarizing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF673AB7), // Rich Indigo/Purple for AI/Gemini theme
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF673AB7).copy(alpha = 0.5f),
                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .testTag("save_and_summarize_button"),
                    contentPadding = PaddingValues()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isUploading || isSummarizing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = "保存＆要約中...",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Save and Summarize icon",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "直近 ${selectedRangeMinutes}分を保存＆AI要約",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }"""

content = content.replace(old_buttons, new_buttons)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)

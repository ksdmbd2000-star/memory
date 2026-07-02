import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# Replace the buttons
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

# Using regex to replace the old two buttons
content = re.sub(r'                // Action Button \(span 2\).*?\}\n                \}\n', new_buttons + '\n', content, flags=re.DOTALL)

# Fix Missing '}' at the end of file (1305:2 Syntax error: Missing '}')
# The replace of the dialog might have removed the final curly brace of the WaveformVisualizer or something else.
# Let's just fix it by appending the needed brackets if missing.

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)

package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_logs")
data class ChatLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sender: String, // "user" or "gemini"
    val text: String
)

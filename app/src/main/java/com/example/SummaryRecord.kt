package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "summary_records")
data class SummaryRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMinutes: Int,
    val summaryText: String
)

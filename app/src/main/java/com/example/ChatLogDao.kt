package com.example

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatLogDao {
    @Query("SELECT * FROM chat_logs ORDER BY timestamp DESC")
    fun getAllChatLogs(): Flow<List<ChatLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatLog(chatLog: ChatLog)

    @Query("DELETE FROM chat_logs WHERE id = :id")
    suspend fun deleteChatLogById(id: Int)

    @Query("DELETE FROM chat_logs")
    suspend fun clearAllChatLogs()
}

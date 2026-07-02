package com.example

import kotlinx.coroutines.flow.Flow

class ChatLogRepository(private val chatLogDao: ChatLogDao) {
    val allLogs: Flow<List<ChatLog>> = chatLogDao.getAllChatLogs()

    suspend fun insert(chatLog: ChatLog) {
        chatLogDao.insertChatLog(chatLog)
    }

    suspend fun deleteById(id: Int) {
        chatLogDao.deleteChatLogById(id)
    }

    suspend fun clearAll() {
        chatLogDao.clearAllChatLogs()
    }
}

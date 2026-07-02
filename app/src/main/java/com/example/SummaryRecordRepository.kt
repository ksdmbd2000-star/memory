package com.example

import kotlinx.coroutines.flow.Flow

class SummaryRecordRepository(private val summaryRecordDao: SummaryRecordDao) {
    val allRecords: Flow<List<SummaryRecord>> = summaryRecordDao.getAllRecords()

    suspend fun insert(record: SummaryRecord) {
        summaryRecordDao.insertRecord(record)
    }

    suspend fun deleteById(id: Int) {
        summaryRecordDao.deleteRecordById(id)
    }

    suspend fun deleteAll() {
        summaryRecordDao.deleteAllRecords()
    }
}

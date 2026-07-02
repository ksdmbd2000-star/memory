package com.example

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryRecordDao {
    @Query("SELECT * FROM summary_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<SummaryRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: SummaryRecord)

    @Query("DELETE FROM summary_records WHERE id = :id")
    suspend fun deleteRecordById(id: Int)

    @Query("DELETE FROM summary_records")
    suspend fun deleteAllRecords()
}

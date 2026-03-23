package com.sleeplock.data.dao

import androidx.room.*
import com.sleeplock.data.entity.SleepRecord

/**
 * 睡眠记录数据访问接口
 */
@Dao
interface SleepRecordDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: SleepRecord)
    
    @Update
    suspend fun update(record: SleepRecord)
    
    @Query("SELECT * FROM sleep_records WHERE date = :date")
    suspend fun getByDate(date: String): SleepRecord?
    
    @Query("SELECT * FROM sleep_records WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    suspend fun getDateRange(startDate: String, endDate: String): List<SleepRecord>
    
    @Query("SELECT * FROM sleep_records ORDER BY date DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 30): List<SleepRecord>
    
    @Query("SELECT AVG(duration) FROM sleep_records WHERE date >= :startDate AND date <= :endDate")
    suspend fun getAverageDuration(startDate: String, endDate: String): Double?
    
    @Query("SELECT COUNT(*) FROM sleep_records")
    suspend fun getCount(): Int
    
    @Query("DELETE FROM sleep_records WHERE date < :date")
    suspend fun deleteOlderThan(date: String)
}

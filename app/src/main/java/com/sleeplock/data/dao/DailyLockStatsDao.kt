package com.sleeplock.data.dao

import androidx.room.*
import com.sleeplock.data.entity.DailyLockStats

/**
 * 每日锁机统计数据访问对象
 */
@Dao
interface DailyLockStatsDao {
    
    @Query("SELECT * FROM daily_lock_stats ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentStats(limit: Int = 30): List<DailyLockStats>
    
    @Query("SELECT * FROM daily_lock_stats WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyLockStats?
    
    @Query("SELECT * FROM daily_lock_stats WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    suspend fun getStatsBetween(startDate: String, endDate: String): List<DailyLockStats>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stats: DailyLockStats)
    
    @Update
    suspend fun update(stats: DailyLockStats)
    
    @Query("DELETE FROM daily_lock_stats WHERE date < :date")
    suspend fun deleteOlderThan(date: String)
    
    @Query("DELETE FROM daily_lock_stats")
    suspend fun deleteAll()
}

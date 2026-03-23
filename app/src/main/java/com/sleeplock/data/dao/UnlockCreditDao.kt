package com.sleeplock.data.dao

import androidx.room.*
import com.sleeplock.data.entity.UnlockCredit
import java.util.*

/**
 * 临时解锁额度数据访问接口
 */
@Dao
interface UnlockCreditDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(credit: UnlockCredit)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(credits: List<UnlockCredit>)
    
    @Query("SELECT SUM(change) FROM unlock_credits WHERE date >= :startDate")
    suspend fun getTotalCredits(startDate: String): Int?
    
    @Query("SELECT * FROM unlock_credits WHERE date >= :startDate ORDER BY date DESC")
    suspend fun getCreditsSince(startDate: String): List<UnlockCredit>
    
    @Query("SELECT * FROM unlock_credits ORDER BY date DESC LIMIT 30")
    suspend fun getRecent(): List<UnlockCredit>
    
    @Query("DELETE FROM unlock_credits WHERE date < :date")
    suspend fun deleteOlderThan(date: String)
    
    /** 获取 7 天前的日期 */
    @Query("SELECT date('now', '-7 days')")
    fun getSevenDaysAgo(): String
}

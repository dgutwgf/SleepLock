package com.sleeplock.data.dao

import androidx.room.*
import com.sleeplock.data.entity.Holiday

/**
 * 节假日数据访问接口
 */
@Dao
interface HolidayDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(holiday: Holiday)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(holidays: List<Holiday>)
    
    @Query("SELECT * FROM holidays WHERE date = :date")
    suspend fun getByDate(date: String): Holiday?
    
    @Query("SELECT * FROM holidays WHERE year = :year ORDER BY date")
    suspend fun getByYear(year: Int): List<Holiday>
    
    @Query("SELECT * FROM holidays WHERE date >= :startDate AND date <= :endDate ORDER BY date")
    suspend fun getDateRange(startDate: String, endDate: String): List<Holiday>
    
    @Query("SELECT EXISTS(SELECT 1 FROM holidays WHERE date = :date AND type = 1)")
    suspend fun isHoliday(date: String): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM holidays WHERE date = :date AND type = 2)")
    suspend fun isWeekend(date: String): Boolean
    
    @Query("DELETE FROM holidays WHERE year < :year")
    suspend fun deleteOlderThan(year: Int)
}

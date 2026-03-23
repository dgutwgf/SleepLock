package com.sleeplock.data.dao

import androidx.room.*
import com.sleeplock.data.entity.ViolationLog

/**
 * 违规日志数据访问接口
 */
@Dao
interface ViolationLogDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ViolationLog)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<ViolationLog>)
    
    @Update
    suspend fun update(log: ViolationLog)
    
    @Query("SELECT * FROM violation_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<ViolationLog>
    
    @Query("SELECT * FROM violation_logs WHERE type = :type ORDER BY timestamp DESC")
    suspend fun getByType(type: String): List<ViolationLog>
    
    @Query("SELECT * FROM violation_logs WHERE handled = 0 ORDER BY timestamp DESC")
    suspend fun getUnhandled(): List<ViolationLog>
    
    @Query("UPDATE violation_logs SET handled = 1 WHERE id = :id")
    suspend fun markAsHandled(id: Long)
    
    @Query("DELETE FROM violation_logs WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
    
    @Query("SELECT COUNT(*) FROM violation_logs WHERE type = :type")
    suspend fun countByType(type: String): Int
}

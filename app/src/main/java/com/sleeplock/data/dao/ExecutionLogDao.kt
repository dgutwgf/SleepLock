package com.sleeplock.data.dao

import androidx.room.*
import com.sleeplock.data.entity.ExecutionLog
import kotlinx.coroutines.flow.Flow

/**
 * 执行日志数据访问对象
 */
@Dao
interface ExecutionLogDao {
    
    /**
     * 插入日志
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: ExecutionLog): Long
    
    /**
     * 批量插入日志
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(logs: List<ExecutionLog>)
    
    /**
     * 获取所有日志（按时间倒序）
     */
    @Query("SELECT * FROM execution_logs ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getLogs(limit: Int = 100, offset: Int = 0): List<ExecutionLog>
    
    /**
     * 获取所有日志（Flow 版本，用于实时观察）
     */
    @Query("SELECT * FROM execution_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsFlow(limit: Int = 100): Flow<List<ExecutionLog>>
    
    /**
     * 根据级别获取日志
     */
    @Query("SELECT * FROM execution_logs WHERE level = :level ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLogsByLevel(level: ExecutionLog.LogLevel, limit: Int = 100): List<ExecutionLog>
    
    /**
     * 根据分类获取日志
     */
    @Query("SELECT * FROM execution_logs WHERE category = :category ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLogsByCategory(category: ExecutionLog.LogCategory, limit: Int = 100): List<ExecutionLog>
    
    /**
     * 根据标签搜索日志
     */
    @Query("SELECT * FROM execution_logs WHERE tag LIKE :tagPattern ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLogsByTag(tagPattern: String, limit: Int = 100): List<ExecutionLog>
    
    /**
     * 搜索日志（按消息内容）
     */
    @Query("SELECT * FROM execution_logs WHERE message LIKE :searchPattern ORDER BY timestamp DESC LIMIT :limit")
    suspend fun searchLogs(searchPattern: String, limit: Int = 100): List<ExecutionLog>
    
    /**
     * 获取指定时间范围内的日志
     */
    @Query("SELECT * FROM execution_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLogsByTimeRange(startTime: Long, endTime: Long, limit: Int = 100): List<ExecutionLog>
    
    /**
     * 获取日志总数
     */
    @Query("SELECT COUNT(*) FROM execution_logs")
    suspend fun getCount(): Int
    
    /**
     * 删除所有日志
     */
    @Query("DELETE FROM execution_logs")
    suspend fun deleteAll()
    
    /**
     * 删除指定时间之前的日志
     */
    @Query("DELETE FROM execution_logs WHERE timestamp < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)
    
    /**
     * 删除指定分类的日志
     */
    @Query("DELETE FROM execution_logs WHERE category = :category")
    suspend fun deleteByCategory(category: ExecutionLog.LogCategory)
    
    /**
     * 导出日志为文本
     */
    @Query("SELECT timestamp || ' [' || level || '] ' || category || ' - ' || tag || ': ' || message || (CASE WHEN extraData IS NOT NULL THEN ' (' || extraData || ')' ELSE '' END) as text FROM execution_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun exportLogs(limit: Int = 500): List<String>
}

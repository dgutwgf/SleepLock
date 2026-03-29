package com.sleeplock.data.dao

import androidx.room.*
import com.sleeplock.data.entity.AppBlacklistItem

/**
 * 应用黑名单数据访问对象
 */
@Dao
interface AppBlacklistDao {
    
    @Query("SELECT * FROM app_blacklist ORDER BY addedTime DESC")
    suspend fun getAll(): List<AppBlacklistItem>
    
    @Query("SELECT * FROM app_blacklist WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): AppBlacklistItem?
    
    @Query("SELECT EXISTS(SELECT 1 FROM app_blacklist WHERE packageName = :packageName)")
    suspend fun isBlacklisted(packageName: String): Boolean
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: AppBlacklistItem)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AppBlacklistItem>)
    
    @Delete
    suspend fun delete(item: AppBlacklistItem)
    
    @Query("DELETE FROM app_blacklist WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)
    
    @Query("DELETE FROM app_blacklist WHERE isCustom = 1")
    suspend fun deleteAllCustom()
    
    @Query("DELETE FROM app_blacklist")
    suspend fun deleteAll()
}

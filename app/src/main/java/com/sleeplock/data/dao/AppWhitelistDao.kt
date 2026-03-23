package com.sleeplock.data.dao

import androidx.room.*
import com.sleeplock.data.entity.AppWhitelist

/**
 * 应用白名单数据访问接口
 */
@Dao
interface AppWhitelistDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: AppWhitelist)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AppWhitelist>)
    
    @Update
    suspend fun update(item: AppWhitelist)
    
    @Delete
    suspend fun delete(item: AppWhitelist)
    
    @Query("SELECT * FROM app_whitelist WHERE packageName = :packageName")
    suspend fun getByPackageName(packageName: String): AppWhitelist?
    
    @Query("SELECT * FROM app_whitelist ORDER BY appName")
    suspend fun getAll(): List<AppWhitelist>
    
    @Query("SELECT packageName FROM app_whitelist")
    suspend fun getAllPackageNames(): List<String>
    
    @Query("SELECT EXISTS(SELECT 1 FROM app_whitelist WHERE packageName = :packageName)")
    suspend fun isWhitelisted(packageName: String): Boolean
    
    @Query("SELECT * FROM app_whitelist WHERE category = :category")
    suspend fun getByCategory(category: String): List<AppWhitelist>
    
    @Query("DELETE FROM app_whitelist WHERE packageName = :packageName AND isLocked = 0")
    suspend fun deleteByPackageName(packageName: String)
}

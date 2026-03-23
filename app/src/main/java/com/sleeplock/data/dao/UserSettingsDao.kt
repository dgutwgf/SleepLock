package com.sleeplock.data.dao

import androidx.room.*
import com.sleeplock.data.entity.UserSettings

/**
 * 用户配置数据访问接口
 */
@Dao
interface UserSettingsDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: UserSettings)
    
    @Update
    suspend fun update(settings: UserSettings)
    
    @Query("SELECT * FROM user_settings WHERE id = 1")
    suspend fun getSettings(): UserSettings?
    
    @Query("UPDATE user_settings SET lockTime = :lockTime WHERE id = 1")
    suspend fun updateLockTime(lockTime: String)
    
    @Query("UPDATE user_settings SET unlockTime = :unlockTime WHERE id = 1")
    suspend fun updateUnlockTime(unlockTime: String)
    
    @Query("UPDATE user_settings SET reminderEnabled = :enabled WHERE id = 1")
    suspend fun updateReminderEnabled(enabled: Boolean)
    
    @Query("UPDATE user_settings SET holidayAdjustEnabled = :enabled WHERE id = 1")
    suspend fun updateHolidayAdjustEnabled(enabled: Boolean)
    
    @Query("UPDATE user_settings SET emergencyUnlockEnabled = :enabled WHERE id = 1")
    suspend fun updateEmergencyUnlockEnabled(enabled: Boolean)
}

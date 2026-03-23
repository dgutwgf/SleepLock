package com.sleeplock.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户配置实体类
 * 存储用户设置和偏好（单例记录）
 */
@Entity(tableName = "user_settings")
data class UserSettings(
    /** 主键，固定为 1 */
    @PrimaryKey
    val id: Int = 1,
    
    /** 锁屏时间，格式为 HH:mm */
    val lockTime: String = "23:40",
    
    /** 解锁时间，格式为 HH:mm */
    val unlockTime: String = "04:00",
    
    /** 是否启用睡前提醒 */
    val reminderEnabled: Boolean = true,
    
    /** 提醒时间，格式为 HH:mm */
    val reminderTime: String = "23:25",
    
    /** 是否启用节假日自动调整 */
    val holidayAdjustEnabled: Boolean = true,
    
    /** 是否启用震动提醒 */
    val vibrationEnabled: Boolean = true,
    
    /** 是否启用闪屏提醒 */
    val flashEnabled: Boolean = true,
    
    /** 是否启用紧急解锁功能 */
    val emergencyUnlockEnabled: Boolean = true
)

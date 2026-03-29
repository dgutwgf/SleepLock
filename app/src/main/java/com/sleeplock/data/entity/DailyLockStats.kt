package com.sleeplock.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 每日锁机统计实体
 */
@Entity(tableName = "daily_lock_stats")
data class DailyLockStats(
    @PrimaryKey val date: String,  // 格式：YYYY-MM-DD
    val lockStartTime: Long = 0,   // 锁机开始时间戳
    val unlockTime: Long = 0,      // 解锁时间戳
    val lockDuration: Long = 0,    // 锁机时长（秒）
    val interceptCount: Int = 0,   // 拦截次数
    val topBlockedApps: String = "", // 被屏蔽最多的应用（JSON 格式）
    val createdAt: Long = System.currentTimeMillis()
)

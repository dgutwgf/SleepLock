package com.sleeplock.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 应用黑名单实体
 */
@Entity(tableName = "app_blacklist")
data class AppBlacklistItem(
    @PrimaryKey val packageName: String,
    val appName: String,
    val addedTime: Long,
    val isCustom: Boolean = false // 是否为用户自定义添加
)

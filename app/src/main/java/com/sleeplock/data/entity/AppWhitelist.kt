package com.sleeplock.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * 应用白名单实体类
 * 存储用户允许使用的应用列表
 */
@Entity(
    tableName = "app_whitelist",
    indices = [Index(value = ["packageName"], unique = true)]
)
data class AppWhitelist(
    /** 应用包名，主键 */
    @PrimaryKey
    val packageName: String,
    
    /** 应用名称 */
    val appName: String,
    
    /** 应用类别：USER, PHONE, MESSAGE, EMERGENCY */
    val category: String,
    
    /** 添加时间戳 */
    val addedTime: Long = System.currentTimeMillis(),
    
    /** 是否锁定，锁定的应用不可删除 */
    val isLocked: Boolean = false
)

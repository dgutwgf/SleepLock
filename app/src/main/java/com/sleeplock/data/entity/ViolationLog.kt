package com.sleeplock.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * 违规日志实体类
 * 记录用户尝试绕过的行为
 */
@Entity(
    tableName = "violation_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["type"])
    ]
)
data class ViolationLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 时间戳 */
    val timestamp: Long = System.currentTimeMillis(),
    
    /** 类型：UNINSTALL_ATTEMPT, PERMISSION_CLOSE, RESTART_UNLOCK 等 */
    val type: String,
    
    /** 描述 */
    val description: String,
    
    /** 是否已处理 */
    val handled: Boolean = false
)

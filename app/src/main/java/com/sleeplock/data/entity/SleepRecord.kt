package com.sleeplock.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * 睡眠记录实体类
 * 存储每日睡眠数据
 */
@Entity(
    tableName = "sleep_records",
    indices = [
        Index(value = ["date"], unique = true),
        Index(value = ["sleepTime"])
    ]
)
data class SleepRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 日期，格式为 yyyy-MM-dd */
    val date: String,
    
    /** 入睡时间戳，Unix 毫秒时间戳 */
    val sleepTime: Long,
    
    /** 起床时间戳，Unix 毫秒时间戳 */
    val wakeTime: Long,
    
    /** 睡眠时长，单位为分钟 */
    val duration: Long,
    
    /** 睡眠质量评分，1-5 分 */
    val quality: Int = 0,
    
    /** 中断次数，夜间醒来的次数 */
    val interruptions: Int = 0,
    
    /** 备注 */
    val note: String = ""
)

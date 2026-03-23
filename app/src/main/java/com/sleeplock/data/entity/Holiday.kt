package com.sleeplock.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * 节假日实体类
 * 存储法定节假日数据
 */
@Entity(
    tableName = "holidays",
    indices = [
        Index(value = ["date"]),
        Index(value = ["year"])
    ]
)
data class Holiday(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 日期，格式为 yyyy-MM-dd */
    val date: String,
    
    /** 节日名称 */
    val name: String,
    
    /** 类型：1=法定假日，2=周末，3=调休 */
    val type: Int,
    
    /** 年份 */
    val year: Int
)

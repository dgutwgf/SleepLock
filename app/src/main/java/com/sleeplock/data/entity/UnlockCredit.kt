package com.sleeplock.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * 临时解锁额度实体类
 * 存储临时解锁额度的获取和消耗记录
 */
@Entity(
    tableName = "unlock_credits",
    indices = [Index(value = ["date"])]
)
data class UnlockCredit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 日期，格式为 yyyy-MM-dd */
    val date: String,
    
    /** 变动额度（正=获得，负=清零） */
    val change: Int,
    
    /** 变动后余额 */
    val balance: Int,
    
    /** 类型：EARN(获得)/UNLOCK_CLEAR(解锁清零)/EXPIRE(过期) */
    val type: String,
    
    /** 备注 */
    val note: String = ""
)

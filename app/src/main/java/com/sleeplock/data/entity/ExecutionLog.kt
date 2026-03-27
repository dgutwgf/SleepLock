package com.sleeplock.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * 执行日志实体 - 记录应用运行时的关键行为和状态变化
 */
@Entity(
    tableName = "execution_logs",
    indices = [Index("timestamp"), Index("level"), Index("category")]
)
data class ExecutionLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * 日志时间戳（毫秒）
     */
    val timestamp: Long = System.currentTimeMillis(),
    
    /**
     * 日志级别
     */
    val level: LogLevel = LogLevel.INFO,
    
    /**
     * 日志分类
     */
    val category: LogCategory = LogCategory.GENERAL,
    
    /**
     * 日志标签（简短标识）
     */
    val tag: String = "",
    
    /**
     * 日志消息（中文描述）
     */
    val message: String,
    
    /**
     * 附加数据（可选，JSON 格式或关键信息）
     */
    val extraData: String? = null
) {
    /**
     * 日志级别枚举
     */
    enum class LogLevel {
        VERBOSE,  // 详细
        DEBUG,    // 调试
        INFO,     // 信息
        WARNING,  // 警告
        ERROR     // 错误
    }
    
    /**
     * 日志分类枚举
     */
    enum class LogCategory {
        GENERAL,        // 通用
        SERVICE,        // 服务
        ACCESSIBILITY,  // 无障碍
        INTERCEPT,      // 拦截
        SCREEN,         // 屏幕状态
        SETTINGS,       // 设置
        SCHEDULER,      // 定时任务
        DATABASE        // 数据库
    }
    
    /**
     * 获取格式化的时间字符串
     */
    fun getFormattedTime(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.CHINA)
        return sdf.format(java.util.Date(timestamp))
    }
    
    /**
     * 获取日志级别图标
     */
    fun getLevelIcon(): String = when (level) {
        LogLevel.VERBOSE -> "🔍"
        LogLevel.DEBUG -> "🐛"
        LogLevel.INFO -> "ℹ️"
        LogLevel.WARNING -> "⚠️"
        LogLevel.ERROR -> "❌"
    }
    
    /**
     * 获取分类图标
     */
    fun getCategoryIcon(): String = when (category) {
        LogCategory.GENERAL -> "📱"
        LogCategory.SERVICE -> "⚙️"
        LogCategory.ACCESSIBILITY -> "♿"
        LogCategory.INTERCEPT -> "🚫"
        LogCategory.SCREEN -> "📵"
        LogCategory.SETTINGS -> "⚙️"
        LogCategory.SCHEDULER -> "⏰"
        LogCategory.DATABASE -> "💾"
    }
}

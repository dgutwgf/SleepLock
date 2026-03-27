package com.sleeplock.util

import android.content.Context
import android.util.Log
import com.sleeplock.data.SleepLockDatabase
import com.sleeplock.data.entity.ExecutionLog
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 日志管理器 - 统一管理应用执行日志
 * 
 * 功能：
 * - 记录中文日志到数据库
 * - 支持不同级别和分类
 * - 自动清理旧日志（保留 7 天）
 * - 导出日志功能
 */
class LogManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "LogManager"
        private const val MAX_LOGS = 1000 // 最多保留 1000 条日志
        private const val LOG_RETENTION_DAYS = 7 // 保留 7 天
        
        @Volatile
        private var instance: LogManager? = null
        
        /**
         * 获取单例实例
         */
        fun getInstance(context: Context): LogManager {
            return instance ?: synchronized(this) {
                instance ?: LogManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
        
        /**
         * 便捷方法：记录信息日志
         */
        fun i(category: ExecutionLog.LogCategory, tag: String, message: String, extra: String? = null) {
            getInstance(AppContextHolder.context).log(ExecutionLog.LogLevel.INFO, category, tag, message, extra)
        }
        
        /**
         * 便捷方法：记录调试日志
         */
        fun d(category: ExecutionLog.LogCategory, tag: String, message: String, extra: String? = null) {
            getInstance(AppContextHolder.context).log(ExecutionLog.LogLevel.DEBUG, category, tag, message, extra)
        }
        
        /**
         * 便捷方法：记录警告日志
         */
        fun w(category: ExecutionLog.LogCategory, tag: String, message: String, extra: String? = null) {
            getInstance(AppContextHolder.context).log(ExecutionLog.LogLevel.WARNING, category, tag, message, extra)
        }
        
        /**
         * 便捷方法：记录错误日志
         */
        fun e(category: ExecutionLog.LogCategory, tag: String, message: String, extra: String? = null) {
            getInstance(AppContextHolder.context).log(ExecutionLog.LogLevel.ERROR, category, tag, message, extra)
        }
        
        /**
         * 便捷方法：记录拦截日志
         */
        fun intercept(tag: String, packageName: String, reason: String) {
            i(ExecutionLog.LogCategory.INTERCEPT, tag, "🚫 拦截应用：$packageName", "原因：$reason")
        }
        
        /**
         * 便捷方法：记录服务状态
         */
        fun serviceStatus(tag: String, status: String) {
            i(ExecutionLog.LogCategory.SERVICE, tag, "⚙️ 服务状态：$status")
        }
        
        /**
         * 便捷方法：记录屏幕状态
         */
        fun screenStatus(tag: String, status: String) {
            i(ExecutionLog.LogCategory.SCREEN, tag, "📵 屏幕状态：$status")
        }
        
        /**
         * 便捷方法：记录无障碍事件
         */
        fun accessibilityEvent(tag: String, event: String) {
            d(ExecutionLog.LogCategory.ACCESSIBILITY, tag, "♿ 无障碍事件：$event")
        }
    }
    
    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA)
    
    /**
     * 记录日志
     */
    fun log(
        level: ExecutionLog.LogLevel = ExecutionLog.LogLevel.INFO,
        category: ExecutionLog.LogCategory = ExecutionLog.LogCategory.GENERAL,
        tag: String = "",
        message: String,
        extraData: String? = null
    ) {
        // 同时输出到 Logcat
        val logTag = "SleepLock-${category.name}"
        val logMessage = "[$tag] $message${extraData?.let { " ($it)" } ?: ""}"
        
        when (level) {
            ExecutionLog.LogLevel.VERBOSE -> Log.v(logTag, logMessage)
            ExecutionLog.LogLevel.DEBUG -> Log.d(logTag, logMessage)
            ExecutionLog.LogLevel.INFO -> Log.i(logTag, logMessage)
            ExecutionLog.LogLevel.WARNING -> Log.w(logTag, logMessage)
            ExecutionLog.LogLevel.ERROR -> Log.e(logTag, logMessage)
        }
        
        // 异步写入数据库
        logScope.launch {
            try {
                val db = SleepLockDatabase.getDatabase(context)
                val log = ExecutionLog(
                    timestamp = System.currentTimeMillis(),
                    level = level,
                    category = category,
                    tag = tag,
                    message = message,
                    extraData = extraData
                )
                db.executionLogDao().insert(log)
                
                // 定期清理旧日志
                cleanupOldLogs()
            } catch (e: Exception) {
                Log.e(TAG, "记录日志失败", e)
            }
        }
    }
    
    /**
     * 获取最近的日志
     */
    suspend fun getRecentLogs(limit: Int = 100): List<ExecutionLog> {
        return try {
            val db = SleepLockDatabase.getDatabase(context)
            db.executionLogDao().getLogs(limit)
        } catch (e: Exception) {
            Log.e(TAG, "获取日志失败", e)
            emptyList()
        }
    }
    
    /**
     * 获取日志总数
     */
    suspend fun getLogCount(): Int {
        return try {
            val db = SleepLockDatabase.getDatabase(context)
            db.executionLogDao().getCount()
        } catch (e: Exception) {
            Log.e(TAG, "获取日志数量失败", e)
            0
        }
    }
    
    /**
     * 清除所有日志
     */
    suspend fun clearAllLogs() {
        try {
            val db = SleepLockDatabase.getDatabase(context)
            db.executionLogDao().deleteAll()
            Log.d(TAG, "已清除所有日志")
        } catch (e: Exception) {
            Log.e(TAG, "清除日志失败", e)
        }
    }
    
    /**
     * 导出日志为文本
     */
    suspend fun exportLogs(limit: Int = 500): String {
        return try {
            val db = SleepLockDatabase.getDatabase(context)
            val logs = db.executionLogDao().exportLogs(limit)
            
            val header = buildString {
                appendLine("=".repeat(60))
                appendLine("专注锁机 - 执行日志导出")
                appendLine("导出时间：${formatTime(System.currentTimeMillis())}")
                appendLine("日志数量：${logs.size} 条")
                appendLine("=".repeat(60))
                appendLine()
            }
            
            header + logs.joinToString("\n")
        } catch (e: Exception) {
            Log.e(TAG, "导出日志失败", e)
            "导出失败：${e.message}"
        }
    }
    
    /**
     * 导出日志为 HTML（可用于分享）
     */
    suspend fun exportAsHtml(): String {
        return try {
            val logs = getRecentLogs(200)
            
            buildString {
                appendLine("<!DOCTYPE html>")
                appendLine("<html><head><meta charset='UTF-8'><title>专注锁机 - 执行日志</title>")
                appendLine("<style>")
                appendLine("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; padding: 20px; background: #f5f5f5; }")
                appendLine(".log { background: white; padding: 10px; margin: 5px 0; border-radius: 5px; font-family: monospace; font-size: 12px; }")
                appendLine(".timestamp { color: #666; }")
                appendLine(".level-INFO { border-left: 3px solid #4CAF50; }")
                appendLine(".level-WARNING { border-left: 3px solid #FF9800; }")
                appendLine(".level-ERROR { border-left: 3px solid #F44336; }")
                appendLine(".category { background: #e3f2fd; padding: 2px 6px; border-radius: 3px; margin-left: 5px; }")
                appendLine("</style></head><body>")
                appendLine("<h1>🔒 专注锁机 - 执行日志</h1>")
                appendLine("<p>导出时间：${formatTime(System.currentTimeMillis())}</p>")
                appendLine("<p>日志数量：${logs.size} 条</p>")
                
                logs.reversed().forEach { log ->
                    appendLine("<div class='log level-${log.level.name}'>")
                    appendLine("<span class='timestamp'>${log.getFormattedTime()}</span> ")
                    appendLine("<span>${log.getLevelIcon()}</span> ")
                    appendLine("<span class='category'>${log.category.name}</span> ")
                    appendLine("<strong>[${log.tag}]</strong> ${log.message}")
                    if (log.extraData != null) {
                        appendLine("<br><span style='color: #999;'>${log.extraData}</span>")
                    }
                    appendLine("</div>")
                }
                
                appendLine("</body></html>")
            }
        } catch (e: Exception) {
            Log.e(TAG, "导出 HTML 失败", e)
            "<html><body>导出失败：${e.message}</body></html>"
        }
    }
    
    /**
     * 清理旧日志
     */
    private suspend fun cleanupOldLogs() {
        try {
            val db = SleepLockDatabase.getDatabase(context)
            val count = db.executionLogDao().getCount()
            
            // 如果超过最大数量，删除旧的
            if (count > MAX_LOGS) {
                val deleteTime = System.currentTimeMillis() - (LOG_RETENTION_DAYS * 24 * 60 * 60 * 1000L)
                db.executionLogDao().deleteOlderThan(deleteTime)
                Log.d(TAG, "已清理 ${LOG_RETENTION_DAYS} 天前的日志")
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理日志失败", e)
        }
    }
    
    /**
     * 格式化时间
     */
    private fun formatTime(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }
}

/**
 * 应用上下文持有者 - 用于在静态方法中获取 Context
 */
object AppContextHolder {
    @Volatile
    lateinit var context: Context
        private set
    
    fun init(context: Context) {
        this.context = context.applicationContext
    }
}

package com.sleeplock.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.sleeplock.data.SleepLockDatabase
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 无障碍监控服务 - 监控前台应用，拦截非白名单应用
 */
class MonitorAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "MonitorAccessibility"
        
        @Volatile
        private var instance: MonitorAccessibilityService? = null
        
        fun getInstance(): MonitorAccessibilityService? = instance
        
        /**
         * 检查服务是否正在运行
         */
        fun isRunning(): Boolean = instance != null
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)
    
    private var isLockPeriod = false
    private var currentPackageName: String = ""
    
    // 基础白名单（始终允许）
    private val baseWhitelist = setOf(
        "com.android.phone",           // 电话
        "com.android.incallui",         // 来电界面
        "com.android.mms",              // 短信
        "com.android.settings",         // 设置
        "com.sleeplock",                // 本应用
        "android",                      // 系统
        "com.android.systemui"          // 系统 UI
    )
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "无障碍服务已连接")
        
        // 配置服务
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                   AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                   AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            // 包名变化时检查
            if (packageName != currentPackageName) {
                currentPackageName = packageName
                serviceScope.launch {
                    checkAndIntercept(packageName)
                }
            }
        }
    }
    
    override fun onInterrupt() {
        Log.w(TAG, "无障碍服务被中断")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        Log.d(TAG, "无障碍服务已销毁")
    }
    
    /**
     * 检查并拦截应用
     */
    private suspend fun checkAndIntercept(packageName: String) {
        // 检查是否在锁机时段
        if (!isLockPeriod) {
            return
        }
        
        // 检查是否在基础白名单
        if (packageName in baseWhitelist) {
            Log.d(TAG, "基础白名单应用，允许：$packageName")
            return
        }
        
        // 检查用户白名单
        val db = SleepLockDatabase.getDatabase(this@MonitorAccessibilityService)
        val isWhitelisted = db.appWhitelistDao().isWhitelisted(packageName)
        
        if (isWhitelisted) {
            Log.d(TAG, "用户白名单应用，允许：$packageName")
            return
        }
        
        // 拦截非白名单应用
        Log.w(TAG, "拦截非白名单应用：$packageName")
        interceptApp(packageName)
    }
    
    /**
     * 拦截应用 - 强制返回桌面
     */
    private fun interceptApp(packageName: String) {
        // 模拟按下 Home 键
        performGlobalAction(GLOBAL_ACTION_HOME)
        
        // 记录违规日志
        serviceScope.launch {
            try {
                val db = SleepLockDatabase.getDatabase(this@MonitorAccessibilityService)
                db.violationLogDao().insert(
                    com.sleeplock.data.entity.ViolationLog(
                        type = "APP_INTERCEPT",
                        description = "尝试打开非白名单应用：$packageName"
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "记录违规日志失败", e)
            }
        }
    }
    
    /**
     * 设置锁机时段
     */
    fun setLockPeriod(isLock: Boolean) {
        isLockPeriod = isLock
        Log.d(TAG, "锁机时段：$isLock")
    }
    
    /**
     * 检查当前时间是否在锁机时段
     */
    suspend fun updateLockPeriod() {
        try {
            val db = SleepLockDatabase.getDatabase(this)
            val settings = db.userSettingsDao().getSettings() ?: return
            
            val currentTime = Calendar.getInstance()
            val lockTime = parseTime(settings.lockTime)
            val unlockTime = parseTime(settings.unlockTime)
            
            val currentMinutes = currentTime.get(Calendar.HOUR_OF_DAY) * 60 + currentTime.get(Calendar.MINUTE)
            val lockMinutes = lockTime.first * 60 + lockTime.second
            val unlockMinutes = unlockTime.first * 60 + unlockTime.second
            
            // 处理跨夜情况
            isLockPeriod = if (lockMinutes > unlockMinutes) {
                currentMinutes >= lockMinutes || currentMinutes < unlockMinutes
            } else {
                currentMinutes >= lockMinutes && currentMinutes < unlockMinutes
            }
            
            Log.d(TAG, "锁机时段更新：$isLockPeriod")
        } catch (e: Exception) {
            Log.e(TAG, "更新锁机时段失败", e)
        }
    }
    
    /**
     * 解析时间字符串
     */
    private fun parseTime(timeStr: String): Pair<Int, Int> {
        val parts = timeStr.split(":")
        return Pair(parts[0].toInt(), parts[1].toInt())
    }
}

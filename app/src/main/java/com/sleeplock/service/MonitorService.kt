package com.sleeplock.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sleeplock.R
import com.sleeplock.data.SleepLockDatabase
import com.sleeplock.data.entity.ExecutionLog
import com.sleeplock.util.LogManager
import kotlinx.coroutines.*
import java.util.Calendar

/**
 * 前台监控服务 - 保持后台运行，监控锁机状态
 * 
 * 职责：
 * - 作为前台服务保持后台运行
 * - 定期检查锁机时段
 * - 通知无障碍服务更新状态
 * - 显示锁机状态通知
 */
class MonitorService : Service() {
    
    companion object {
        private const val TAG = "MonitorService"
        private const val CHANNEL_ID = "lock_status_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS_NAME = "SleepLock"
        private const val KEY_LOCK_ACTIVE = "is_lock_active"
        private const val CHECK_INTERVAL = 5 * 1000L // 5 秒检查一次（加快响应）
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private var checkRunnable: Runnable? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🔧 监控服务已创建")
        LogManager.serviceStatus("MonitorService", "🔧 监控服务已创建")
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🚀 监控服务已启动 - 前台服务")
        LogManager.serviceStatus("MonitorService", "🚀 监控服务已启动 - 前台服务")
        
        // 启动前台服务
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // 立即更新锁机时段
        updateLockPeriod()
        
        // 启动定期检查
        startPeriodicCheck()
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        Log.w(TAG, "⚠️ 监控服务已销毁 - 可能被系统杀死")
        LogManager.e(ExecutionLog.LogCategory.SERVICE, "MonitorService", "⚠️ 监控服务已销毁 - 可能被系统杀死")
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "⚠️ 监控服务被后台挂起 - 任务移除")
        LogManager.e(ExecutionLog.LogCategory.SERVICE, "MonitorService", "⚠️ 监控服务被后台挂起 - 任务移除")
        
        // 尝试重启服务
        try {
            val restartIntent = Intent(applicationContext, this::class.java)
            startForegroundService(restartIntent)
            Log.d(TAG, "🔄 尝试重启监控服务")
            LogManager.serviceStatus("MonitorService", "🔄 监控服务被挂起后尝试重启")
        } catch (e: Exception) {
            Log.e(TAG, "重启监控服务失败", e)
            LogManager.e(ExecutionLog.LogCategory.SERVICE, "MonitorService", "重启监控服务失败：${e.message}")
        }
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "锁机状态",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示当前锁机状态"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建前台通知
     */
    private fun createNotification(): Notification {
        val isLockActive = prefs.getBoolean(KEY_LOCK_ACTIVE, false)
        val statusText = if (isLockActive) "锁机中 - 娱乐应用已拦截" else "待机中"
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("专注锁机")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    /**
     * 启动定期检查
     */
    private fun startPeriodicCheck() {
        checkRunnable = object : Runnable {
            override fun run() {
                updateLockPeriod()
                updateNotification()
                mainHandler.postDelayed(this, CHECK_INTERVAL)
            }
        }
        
        mainHandler.post(checkRunnable!!)
    }
    
    /**
     * 更新锁机时段 - 定时监控核心逻辑
     */
    private fun updateLockPeriod() {
        serviceScope.launch {
            try {
                val db = SleepLockDatabase.getDatabase(this@MonitorService)
                val settings = db.userSettingsDao().getSettings() ?: return@launch
                
                val currentTime = Calendar.getInstance()
                val lockTime = parseTime(settings.lockTime)
                val unlockTime = parseTime(settings.unlockTime)
                
                val currentMinutes = currentTime.get(Calendar.HOUR_OF_DAY) * 60 + currentTime.get(Calendar.MINUTE)
                val lockMinutes = lockTime.first * 60 + lockTime.second
                val unlockMinutes = unlockTime.first * 60 + unlockTime.second
                
                // 处理跨夜情况
                val isInTimePeriod = if (lockMinutes > unlockMinutes) {
                    currentMinutes >= lockMinutes || currentMinutes < unlockMinutes
                } else {
                    currentMinutes >= lockMinutes && currentMinutes < unlockMinutes
                }
                
                // 检查是否是测试模式
                val testMode = prefs.getBoolean("test_mode", false)
                val isLockActive = prefs.getBoolean(KEY_LOCK_ACTIVE, false)
                
                // 测试模式：忽略时间段，直接锁定
                // 正常模式：必须同时满足：在锁机时段内 + 锁机服务已激活
                val shouldBeLocked = if (testMode) {
                    true  // 测试模式下始终锁定
                } else {
                    isInTimePeriod && isLockActive
                }
                
                // 更新无障碍服务状态
                mainHandler.post {
                    MonitorAccessibilityService.getInstance()?.let { service ->
                        service.setLockPeriod(shouldBeLocked)
                        val logMsg = "🔄 定时监控更新：锁机=$shouldBeLocked (时间=$isInTimePeriod, 激活=$isLockActive, 测试=$testMode)"
                        Log.d(TAG, logMsg)
                        LogManager.d(ExecutionLog.LogCategory.SCHEDULER, "PeriodicUpdate", logMsg)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "更新锁机时段失败", e)
                LogManager.e(ExecutionLog.LogCategory.SCHEDULER, "PeriodicUpdate", "❌ 更新锁机时段失败：${e.message}")
            }
        }
    }
    
    /**
     * 更新通知
     */
    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }
    
    /**
     * 解析时间字符串
     */
    private fun parseTime(timeStr: String): Pair<Int, Int> {
        val parts = timeStr.split(":")
        return Pair(parts[0].toInt(), parts[1].toInt())
    }
}

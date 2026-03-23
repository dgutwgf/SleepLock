package com.sleeplock.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sleeplock.R
import com.sleeplock.data.SleepLockDatabase
import kotlinx.coroutines.*

/**
 * 前台监控服务 - 保持后台运行
 */
class MonitorService : Service() {
    
    companion object {
        private const val TAG = "MonitorService"
        private const val CHANNEL_ID = "lock_status_channel"
        private const val NOTIFICATION_ID = 1001
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "监控服务已创建")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "监控服务已启动")
        
        // 启动前台服务
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // 更新锁机时段
        serviceScope.launch {
            MonitorAccessibilityService.getInstance()?.updateLockPeriod()
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "监控服务已销毁")
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("专注锁机")
            .setContentText("锁机模式运行中")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}

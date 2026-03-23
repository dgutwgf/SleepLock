package com.sleeplock.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.sleeplock.R
import com.sleeplock.ui.MainActivity

/**
 * 睡前提醒 Worker - 使用 WorkManager 定时触发睡前提醒
 */
class SleepReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    
    companion object {
        private const val TAG = "SleepReminderWorker"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "reminder_channel"
    }
    
    override fun doWork(): Result {
        Log.d(TAG, "执行睡前提醒任务")
        
        try {
            sendReminderNotification()
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "发送提醒失败", e)
            return Result.retry()
        }
    }
    
    /**
     * 发送提醒通知
     */
    private fun sendReminderNotification() {
        val context = applicationContext
        
        // 创建通知
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("该睡觉了！")
            .setContentText("还有 15 分钟就要锁屏了，准备休息吧")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
        
        // 添加稍后提醒按钮
        val snoozeIntent = Intent(context, ReminderSnoozeReceiver::class.java).apply {
            action = "com.sleeplock.action.SNOOZE"
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            2001,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_menu_recent_history, "稍后提醒", snoozePendingIntent)
        
        // 添加打开应用按钮
        val appIntent = Intent(context, MainActivity::class.java)
        val appPendingIntent = PendingIntent.getActivity(
            context,
            2002,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(appPendingIntent)
        
        // 发送通知
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Android 8.0+ 需要创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "睡前提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "睡前 15 分钟提醒"
                enableVibration(true)
                vibrationPattern = longArrayOf(1000, 500, 1000, 500, 2000)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        notificationManager.notify(NOTIFICATION_ID, builder.build())
        Log.d(TAG, "睡前提醒通知已发送")
    }
}

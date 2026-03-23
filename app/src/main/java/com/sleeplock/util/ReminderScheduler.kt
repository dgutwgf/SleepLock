package com.sleeplock.util

import android.content.Context
import android.util.Log
import androidx.work.*
import com.sleeplock.worker.SleepReminderWorker
import java.util.concurrent.TimeUnit

/**
 * 提醒任务调度器
 */
class ReminderScheduler(private val context: Context) {
    
    companion object {
        private const val TAG = "ReminderScheduler"
        private const val WORK_NAME = "sleep_reminder"
    }
    
    private val workManager = WorkManager.getInstance(context)
    
    /**
     * 设置每日定时提醒
     */
    fun scheduleDailyReminder(hour: Int, minute: Int) {
        Log.d(TAG, "设置每日提醒：$hour:$minute")
        
        // 计算触发时间
        val now = java.util.Calendar.getInstance()
        val triggerTime = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            
            // 如果时间已过，设置为明天
            if (before(now)) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }
        
        val delay = (triggerTime.timeInMillis - System.currentTimeMillis()) / 1000
        
        // 创建定时任务
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        
        val workRequest = OneTimeWorkRequestBuilder<SleepReminderWorker>()
            .setInitialDelay(delay, TimeUnit.SECONDS)
            .setConstraints(constraints)
            .addTag(WORK_NAME)
            .build()
        
        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        
        Log.d(TAG, "提醒任务已设置，延迟：${delay}秒")
    }
    
    /**
     * 设置稍后提醒
     */
    fun scheduleSnooze(delayMinutes: Int = 5) {
        Log.d(TAG, "设置稍后提醒：$delayMinutes 分钟后")
        
        val workRequest = OneTimeWorkRequestBuilder<SleepReminderWorker>()
            .setInitialDelay(delayMinutes.toLong(), TimeUnit.MINUTES)
            .addTag("snooze_reminder")
            .build()
        
        workManager.enqueue(workRequest)
    }
    
    /**
     * 取消所有提醒任务
     */
    fun cancelAllReminders() {
        workManager.cancelUniqueWork(WORK_NAME)
        workManager.cancelAllWorkByTag("snooze_reminder")
        Log.d(TAG, "已取消所有提醒任务")
    }
}

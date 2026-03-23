package com.sleeplock.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.sleeplock.receiver.LockReceiver
import com.sleeplock.data.SleepLockDatabase
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 定时任务管理器 - 管理锁屏/解锁定时任务
 */
class SchedulerManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SchedulerManager"
        private const val REQUEST_CODE_LOCK = 1001
        private const val REQUEST_CODE_UNLOCK = 1002
    }
    
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 设置所有定时任务
     */
    fun scheduleAllTasks() {
        ioScope.launch {
            try {
                val db = SleepLockDatabase.getDatabase(context)
                val settings = db.userSettingsDao().getSettings() ?: return@launch
                
                // 计算锁屏和解锁时间（考虑节假日调整）
                val holidayManager = HolidayManager(context)
                val lockTime = holidayManager.getAdjustedLockTime(settings.lockTime)
                val unlockTime = holidayManager.getAdjustedUnlockTime(settings.unlockTime)
                
                Log.d(TAG, "设置定时任务：锁屏=$lockTime, 解锁=$unlockTime")
                
                // 设置锁屏定时任务
                scheduleLock(lockTime)
                
                // 设置解锁定时任务
                scheduleUnlock(unlockTime)
                
            } catch (e: Exception) {
                Log.e(TAG, "设置定时任务失败", e)
            }
        }
    }
    
    /**
     * 设置锁屏定时任务
     */
    private fun scheduleLock(lockTime: String) {
        val triggerTime = calculateNextTriggerTime(lockTime)
        Log.d(TAG, "下次锁屏时间：${formatDateTime(triggerTime)}")
        
        val intent = Intent(context, LockReceiver::class.java).apply {
            action = LockReceiver.ACTION_LOCK
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_LOCK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 使用精确闹钟，支持待机触发
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }
    
    /**
     * 设置解锁定时任务
     */
    private fun scheduleUnlock(unlockTime: String) {
        val triggerTime = calculateNextTriggerTime(unlockTime)
        Log.d(TAG, "下次解锁时间：${formatDateTime(triggerTime)}")
        
        val intent = Intent(context, LockReceiver::class.java).apply {
            action = LockReceiver.ACTION_UNLOCK
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_UNLOCK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }
    
    /**
     * 计算下次触发时间
     */
    private fun calculateNextTriggerTime(timeStr: String): Long {
        val parts = timeStr.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        // 如果时间已过，设置为明天
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        return calendar.timeInMillis
    }
    
    /**
     * 取消所有定时任务
     */
    fun cancelAllTasks() {
        val lockIntent = Intent(context, LockReceiver::class.java).apply {
            action = LockReceiver.ACTION_LOCK
        }
        val lockPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_LOCK,
            lockIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        lockPendingIntent?.let { alarmManager.cancel(it) }
        
        val unlockIntent = Intent(context, LockReceiver::class.java).apply {
            action = LockReceiver.ACTION_UNLOCK
        }
        val unlockPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_UNLOCK,
            unlockIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        unlockPendingIntent?.let { alarmManager.cancel(it) }
        
        Log.d(TAG, "已取消所有定时任务")
    }
    
    /**
     * 格式化日期时间
     */
    private fun formatDateTime(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
    }
}

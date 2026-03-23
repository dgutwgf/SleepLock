package com.sleeplock.service

import android.content.Context
import android.util.Log
import com.sleeplock.data.SleepLockDatabase
import com.sleeplock.data.entity.SleepRecord
import com.sleeplock.util.UnlockCreditManager
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 睡眠监测服务 - 记录睡眠数据
 */
object SleepMonitorService {
    
    private const val TAG = "SleepMonitorService"
    
    @Volatile
    private var lastScreenOffTime: Long = 0
    
    @Volatile
    private var isSleeping: Boolean = false
    
    @Volatile
    private var sleepStartTime: Long = 0
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 屏幕关闭时调用
     */
    fun onScreenOff(context: Context) {
        Log.d(TAG, "屏幕关闭，记录时间")
        lastScreenOffTime = System.currentTimeMillis()
        
        // 延迟 5 分钟判断是否真正入睡
        ioScope.launch {
            delay(5 * 60 * 1000) // 5 分钟
            
            // 检查是否仍然息屏（这里简化处理，实际需要通过其他方式检测）
            if (!isSleeping) {
                isSleeping = true
                sleepStartTime = System.currentTimeMillis()
                Log.d(TAG, "判定为入睡：${formatTime(sleepStartTime)}")
                
                // 检查是否提前睡觉，计算额度奖励
                checkEarlySleepAndAddCredits(context, sleepStartTime)
            }
        }
    }
    
    /**
     * 用户解锁（亮屏）时调用
     */
    fun onUserPresent(context: Context) {
        Log.d(TAG, "用户解锁")
        
        if (isSleeping) {
            val wakeTime = System.currentTimeMillis()
            val duration = (wakeTime - sleepStartTime) / 60000 // 转换为分钟
            
            Log.d(TAG, "判定为起床，睡眠时长：$duration 分钟")
            
            // 保存睡眠记录
            saveSleepRecord(context, sleepStartTime, wakeTime, duration)
            
            isSleeping = false
            sleepStartTime = 0
        }
    }
    
    /**
     * 检查是否提前睡觉并添加额度
     */
    private suspend fun checkEarlySleepAndAddCredits(context: Context, sleepTime: Long) {
        try {
            val db = SleepLockDatabase.getDatabase(context)
            val settings = db.userSettingsDao().getSettings() ?: return
            
            // 计算规定锁屏时间
            val lockTimeParts = settings.lockTime.split(":")
            val lockHour = lockTimeParts[0].toInt()
            val lockMinute = lockTimeParts[1].toInt()
            
            val calendar = Calendar.getInstance().apply {
                timeInMillis = sleepTime
            }
            
            val scheduledLockTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, lockHour)
                set(Calendar.MINUTE, lockMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                
                // 如果锁屏时间已过（跨夜），设置为前一天
                if (after(calendar)) {
                    add(Calendar.DAY_OF_YEAR, -1)
                }
            }
            
            // 计算提前时间（分钟）
            val earlyMinutes = (scheduledLockTime.timeInMillis - sleepTime) / 60000
            
            if (earlyMinutes > 0) {
                Log.d(TAG, "提前睡觉：$earlyMinutes 分钟")
                
                // 添加额度
                val creditManager = UnlockCreditManager(context)
                creditManager.addCredits(earlyMinutes.toInt())
            } else {
                Log.d(TAG, "未提前睡觉")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "检查提前睡觉失败", e)
        }
    }
    
    /**
     * 保存睡眠记录到数据库
     */
    private fun saveSleepRecord(context: Context, sleepTime: Long, wakeTime: Long, duration: Long) {
        ioScope.launch {
            try {
                val db = SleepLockDatabase.getDatabase(context)
                val date = dateFormat.format(Date(sleepTime))
                
                val record = SleepRecord(
                    date = date,
                    sleepTime = sleepTime,
                    wakeTime = wakeTime,
                    duration = duration,
                    quality = 0,
                    interruptions = 0
                )
                
                db.sleepRecordDao().insert(record)
                Log.d(TAG, "睡眠记录已保存：$date, 时长=$duration 分钟")
            } catch (e: Exception) {
                Log.e(TAG, "保存睡眠记录失败", e)
            }
        }
    }
    
    /**
     * 格式化时间
     */
    private fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))
    }
}

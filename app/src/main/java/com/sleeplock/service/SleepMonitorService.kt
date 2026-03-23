package com.sleeplock.service

import android.content.Context
import android.util.Log
import com.sleeplock.data.SleepLockDatabase
import com.sleeplock.data.entity.SleepRecord
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

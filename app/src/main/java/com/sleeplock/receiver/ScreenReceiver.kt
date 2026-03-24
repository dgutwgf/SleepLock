package com.sleeplock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.sleeplock.service.LockService
import com.sleeplock.service.SleepMonitorService
import kotlinx.coroutines.*

/**
 * 屏幕状态接收器 - 监听息屏/亮屏事件，实现持续锁屏
 */
class ScreenReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ScreenReceiver"
        private const val PREFS_NAME = "SleepLock"
        private const val KEY_LOCK_ACTIVE = "is_lock_active"
        private const val KEY_LOCK_TIME = "lock_time"
        private const val KEY_UNLOCK_TIME = "unlock_time"
        
        // 测试模式：锁屏持续时间（毫秒）
        private const val TEST_LOCK_DURATION = 2 * 60 * 1000L // 2 分钟
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "屏幕状态变化：$action")
        
        when (action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "屏幕关闭")
                SleepMonitorService.onScreenOff(context)
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "用户解锁")
                SleepMonitorService.onUserPresent(context)
                
                // 检查是否需要持续锁屏
                checkAndRelock(context)
            }
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "屏幕开启")
            }
        }
    }
    
    /**
     * 检查是否需要重新锁屏
     */
    private fun checkAndRelock(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isLockActive = prefs.getBoolean(KEY_LOCK_ACTIVE, false)
        
        Log.d(TAG, "检查锁机状态: isLockActive=$isLockActive")
        
        if (!isLockActive) {
            Log.d(TAG, "锁机服务未激活，不重新锁屏")
            return
        }
        
        // 检查当前时间是否在锁机时段
        if (isInLockPeriod(context, prefs)) {
            Log.d(TAG, "当前在锁机时段，延迟500ms后重新锁屏")
            
            // 延迟一小段时间后重新锁屏，让用户看到解锁的瞬间
            scope.launch {
                delay(500)
                
                // 再次检查（防止用户手动停止）
                if (prefs.getBoolean(KEY_LOCK_ACTIVE, false)) {
                    Log.d(TAG, "执行重新锁屏")
                    val success = LockService.lockScreen(context)
                    Log.d(TAG, "重新锁屏结果: $success")
                }
            }
        } else {
            Log.d(TAG, "当前不在锁机时段，不重新锁屏")
        }
    }
    
    /**
     * 检查当前时间是否在锁机时段
     */
    private fun isInLockPeriod(context: Context, prefs: SharedPreferences): Boolean {
        // 测试模式：锁机后2分钟内持续锁屏
        val testMode = prefs.getBoolean("test_mode", false)
        if (testMode) {
            val lockStartTime = prefs.getLong("lock_start_time", 0)
            val elapsed = System.currentTimeMillis() - lockStartTime
            Log.d(TAG, "测试模式: 已过 ${elapsed/1000} 秒，限制 ${TEST_LOCK_DURATION/1000} 秒")
            return elapsed < TEST_LOCK_DURATION
        }
        
        // 正常模式：检查时间段
        val lockTime = prefs.getString(KEY_LOCK_TIME, "23:40") ?: "23:40"
        val unlockTime = prefs.getString(KEY_UNLOCK_TIME, "06:00") ?: "06:00"
        
        return isInTimePeriod(lockTime, unlockTime)
    }
    
    /**
     * 检查当前时间是否在指定时间段内
     */
    private fun isInTimePeriod(startTime: String, endTime: String): Boolean {
        try {
            val now = java.util.Calendar.getInstance()
            val currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
            
            val startParts = startTime.split(":")
            val startMinutes = startParts[0].toInt() * 60 + startParts[1].toInt()
            
            val endParts = endTime.split(":")
            val endMinutes = endParts[0].toInt() * 60 + endParts[1].toInt()
            
            // 处理跨夜情况（如 23:40 - 06:00）
            val inPeriod = if (startMinutes > endMinutes) {
                // 跨夜：当前时间 >= 开始时间 或 当前时间 < 结束时间
                currentMinutes >= startMinutes || currentMinutes < endMinutes
            } else {
                // 同一天：开始时间 <= 当前时间 < 结束时间
                currentMinutes >= startMinutes && currentMinutes < endMinutes
            }
            
            Log.d(TAG, "时间检查: 当前=$currentMinutes 分钟, 时段=$startMinutes-$endMinutes, 在时段内=$inPeriod")
            return inPeriod
            
        } catch (e: Exception) {
            Log.e(TAG, "解析时间失败", e)
            return false
        }
    }
}
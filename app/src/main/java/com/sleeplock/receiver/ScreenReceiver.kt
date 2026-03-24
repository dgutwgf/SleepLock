package com.sleeplock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sleeplock.service.LockService
import com.sleeplock.service.SleepMonitorService
import com.sleeplock.ui.LockScreenActivity

/**
 * 屏幕状态接收器 - 监听息屏/亮屏事件，实现强制锁屏
 */
class ScreenReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ScreenReceiver"
        private const val PREFS_NAME = "SleepLock"
        private const val KEY_LOCK_ACTIVE = "is_lock_active"
        
        // 测试模式：锁屏持续时间（毫秒）
        private const val TEST_LOCK_DURATION = 2 * 60 * 1000L // 2 分钟
    }
    
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "屏幕状态变化：$action")
        
        when (action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "屏幕关闭")
                SleepMonitorService.onScreenOff(context)
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "⚠️ 用户解锁 - 立即执行强制锁屏")
                SleepMonitorService.onUserPresent(context)
                
                // 立即执行强制锁屏（无延迟）
                forceRelock(context)
            }
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "屏幕开启")
                // 屏幕亮起时也检查（防止用户在锁屏界面操作）
                checkAndShowLockOverlay(context)
            }
        }
    }
    
    /**
     * 强制重新锁屏 - 多重防护
     */
    private fun forceRelock(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isLockActive = prefs.getBoolean(KEY_LOCK_ACTIVE, false)
        
        Log.d(TAG, "检查锁机状态: isLockActive=$isLockActive")
        
        if (!isLockActive) {
            Log.d(TAG, "锁机服务未激活，不重新锁屏")
            return
        }
        
        // 检查是否在锁机时段
        if (!isInLockPeriod(context, prefs)) {
            Log.d(TAG, "当前不在锁机时段，不重新锁屏")
            return
        }
        
        Log.d(TAG, "🔒 执行强制锁屏...")
        
        // 方法1: 立即启动锁屏 Activity（最快响应用户看到）
        try {
            val intent = Intent(context, LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or 
                         Intent.FLAG_ACTIVITY_CLEAR_TASK or
                         Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                         Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
            Log.d(TAG, "✅ 已启动 LockScreenActivity")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动 LockScreenActivity 失败", e)
        }
        
        // 方法2: 立即锁屏
        handler.post {
            try {
                val success = LockService.lockScreen(context)
                Log.d(TAG, "锁屏结果: $success")
            } catch (e: Exception) {
                Log.e(TAG, "锁屏失败", e)
            }
        }
        
        // 方法3: 200ms 后再次检查并锁屏（确保成功）
        handler.postDelayed({
            if (prefs.getBoolean(KEY_LOCK_ACTIVE, false) && isInLockPeriod(context, prefs)) {
                Log.d(TAG, "二次确认锁屏")
                LockService.lockScreen(context)
            }
        }, 200)
        
        // 方法4: 500ms 后再次检查
        handler.postDelayed({
            if (prefs.getBoolean(KEY_LOCK_ACTIVE, false) && isInLockPeriod(context, prefs)) {
                Log.d(TAG, "三次确认锁屏")
                LockService.lockScreen(context)
            }
        }, 500)
    }
    
    /**
     * 检查并显示锁屏覆盖层
     */
    private fun checkAndShowLockOverlay(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isLockActive = prefs.getBoolean(KEY_LOCK_ACTIVE, false)
        
        if (isLockActive && isInLockPeriod(context, prefs)) {
            // 延迟启动覆盖层（给系统时间完成亮屏）
            handler.postDelayed({
                try {
                    val intent = Intent(context, LockScreenActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                 Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "启动覆盖层失败", e)
                }
            }, 100)
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
            val remaining = TEST_LOCK_DURATION - elapsed
            Log.d(TAG, "测试模式: 已过 ${elapsed/1000}秒, 剩余 ${remaining/1000}秒")
            return elapsed < TEST_LOCK_DURATION
        }
        
        // 正常模式：检查时间段
        val lockTime = prefs.getString("lock_time", "23:40") ?: "23:40"
        val unlockTime = prefs.getString("unlock_time", "06:00") ?: "06:00"
        
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
                currentMinutes >= startMinutes || currentMinutes < endMinutes
            } else {
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
package com.sleeplock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sleeplock.data.entity.ExecutionLog
import com.sleeplock.service.LockService
import com.sleeplock.service.MonitorAccessibilityService
import com.sleeplock.service.MonitorService
import com.sleeplock.service.SleepMonitorService
import com.sleeplock.util.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 屏幕状态接收器 - 监听息屏/亮屏/解锁事件
 * 
 * 职责：
 * - 监听屏幕状态变化
 * - 通知监控服务更新状态
 * - 不再负责应用拦截（由 MonitorAccessibilityService 负责）
 */
class ScreenReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ScreenReceiver"
        private const val PREFS_NAME = "SleepLock"
        private const val KEY_LOCK_ACTIVE = "is_lock_active"
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "📱 屏幕状态：$action")
        
        when (action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "⚫ 屏幕关闭")
                LogManager.screenStatus("ScreenReceiver", "⚫ 屏幕关闭")
                SleepMonitorService.onScreenOff(context)
            }
            
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "🔓 用户解锁")
                LogManager.screenStatus("ScreenReceiver", "🔓 用户解锁 - 准备恢复监控")
                SleepMonitorService.onUserPresent(context)
                
                // 用户解锁后，确保监控服务正在运行
                handler.postDelayed({
                    ensureMonitoringActive(context)
                }, 500)
            }
            
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "💡 屏幕开启")
                LogManager.screenStatus("ScreenReceiver", "💡 屏幕开启")
            }
        }
    }
    
    /**
     * 确保监控服务活跃 - 解锁后立即恢复监控
     */
    private fun ensureMonitoringActive(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isLockActive = prefs.getBoolean(KEY_LOCK_ACTIVE, false)
        
        Log.d(TAG, "🔍 检查监控状态：isLockActive=$isLockActive")
        
        if (!isLockActive) {
            Log.d(TAG, "锁机服务未激活，跳过")
            return
        }
        
        // 检查无障碍服务是否运行
        val accessibilityService = MonitorAccessibilityService.getInstance()
        if (accessibilityService == null) {
            Log.w(TAG, "⚠️ 无障碍服务未运行，尝试恢复")
            // 尝试启动监控服务来恢复无障碍服务
            try {
                val monitorIntent = Intent(context, MonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(monitorIntent)
                } else {
                    context.startService(monitorIntent)
                }
                Log.d(TAG, "🚀 已尝试启动监控服务")
            } catch (e: Exception) {
                Log.e(TAG, "启动监控服务失败", e)
            }
            return
        }
        
        // 立即更新锁机时段状态（强制刷新）
        scope.launch {
            Log.d(TAG, "🔄 解锁后立即更新锁机时段")
            accessibilityService.updateLockPeriod()
            
            // 确保锁机时段被正确设置
            delay(500)
            accessibilityService.updateLockPeriod()
        }
        
        Log.d(TAG, "✅ 监控服务已确认活跃")
    }
}

package com.sleeplock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sleeplock.service.LockService
import com.sleeplock.service.MonitorAccessibilityService

/**
 * 锁屏/解锁广播接收器
 */
class LockReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "LockReceiver"
        const val ACTION_LOCK = "com.sleeplock.action.LOCK"
        const val ACTION_UNLOCK = "com.sleeplock.action.UNLOCK"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "收到广播：$action")
        
        when (action) {
            ACTION_LOCK -> handleLock(context)
            ACTION_UNLOCK -> handleUnlock(context)
        }
    }
    
    /**
     * 处理锁屏
     */
    private fun handleLock(context: Context) {
        Log.d(TAG, "执行锁屏")
        
        // 锁定屏幕
        LockService.lockScreen(context)
        
        // 启动监控服务
        val monitorIntent = Intent(context, MonitorService::class.java)
        context.startForegroundService(monitorIntent)
        
        // 更新无障碍服务的锁机时段
        MonitorAccessibilityService.getInstance()?.setLockPeriod(true)
        
        Log.d(TAG, "锁屏完成")
    }
    
    /**
     * 处理解锁
     */
    private fun handleUnlock(context: Context) {
        Log.d(TAG, "执行解锁")
        
        // 停止监控服务
        val monitorIntent = Intent(context, MonitorService::class.java)
        context.stopService(monitorIntent)
        
        // 更新无障碍服务的锁机时段
        MonitorAccessibilityService.getInstance()?.setLockPeriod(false)
        
        Log.d(TAG, "解锁完成")
    }
}

package com.sleeplock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sleeplock.service.LockService
import com.sleeplock.service.MonitorAccessibilityService
import com.sleeplock.service.MonitorService

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
        
        // 检查设备管理员权限
        if (!LockService.isAdminActive(context)) {
            Log.e(TAG, "设备管理员权限未激活，无法锁屏")
            android.widget.Toast.makeText(context, "请先激活设备管理员权限", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        
        // 锁定屏幕
        val success = LockService.lockScreen(context)
        if (success) {
            Log.d(TAG, "锁屏成功")
        } else {
            Log.w(TAG, "锁屏可能失败，用户需要手动确认")
        }
        
        // 启动监控服务
        try {
            val monitorIntent = Intent(context, MonitorService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(monitorIntent)
            } else {
                context.startService(monitorIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动监控服务失败", e)
        }
        
        // 更新无障碍服务的锁机时段
        MonitorAccessibilityService.getInstance()?.setLockPeriod(true)
        
        Log.d(TAG, "锁屏处理完成")
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

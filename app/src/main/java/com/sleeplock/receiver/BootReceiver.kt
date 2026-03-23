package com.sleeplock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sleeplock.service.LockService
import com.sleeplock.util.SchedulerManager

/**
 * 开机启动接收器
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "开机启动广播已接收")
            
            // 检查设备管理员权限
            if (!LockService.isAdminActive(context)) {
                Log.w(TAG, "设备管理员权限未激活，请求权限")
                LockService.requestAdminPermission(context)
                return
            }
            
            // 恢复定时任务
            val schedulerManager = SchedulerManager(context)
            schedulerManager.scheduleAllTasks()
            
            Log.d(TAG, "定时任务已恢复")
        }
    }
}

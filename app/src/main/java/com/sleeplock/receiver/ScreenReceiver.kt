package com.sleeplock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sleeplock.service.SleepMonitorService

/**
 * 屏幕状态接收器 - 监听息屏/亮屏事件
 */
class ScreenReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ScreenReceiver"
    }
    
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
            }
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "屏幕开启")
            }
        }
    }
}

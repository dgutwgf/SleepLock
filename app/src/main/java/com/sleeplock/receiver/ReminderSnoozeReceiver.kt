package com.sleeplock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sleeplock.util.ReminderScheduler

/**
 * 提醒稍后处理接收器
 */
class ReminderSnoozeReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ReminderSnoozeReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.sleeplock.action.SNOOZE") {
            Log.d(TAG, "用户选择稍后提醒")
            
            // 5 分钟后再次提醒
            val reminderScheduler = ReminderScheduler(context)
            reminderScheduler.scheduleSnooze(5)
            
            Log.d(TAG, "已设置 5 分钟后再次提醒")
        }
    }
}

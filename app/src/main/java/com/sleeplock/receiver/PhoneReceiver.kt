package com.sleeplock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

/**
 * 电话状态接收器 - 监听来电，允许锁机时段接听电话
 */
class PhoneReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "PhoneReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            
            Log.d(TAG, "电话状态：$state, 号码：$incomingNumber")
            
            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    Log.d(TAG, "来电：$incomingNumber")
                    // 允许接听电话，不做拦截
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    Log.d(TAG, "通话中")
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    Log.d(TAG, "通话结束")
                }
            }
        }
    }
}

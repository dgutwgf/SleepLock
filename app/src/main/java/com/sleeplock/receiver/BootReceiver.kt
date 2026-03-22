package com.sleeplock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log

/**
 * 开机启动接收器 - 连续重启解锁逻辑
 * 
 * 功能：
 * 1. 监听开机完成
 * 2. 计算连续重启次数
 * 3. 触发临时解锁（3 次重启 + 额度>0）
 * 4. 清零所有额度
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val PREF_NAME = "lock_prefs"
        private const val KEY_RESTART_COUNT = "restart_count"
        private const val KEY_LAST_RESTART_TIME = "last_restart_time"
        private const val KEY_UNLOCK_UNTIL = "unlock_until"
        private const val KEY_UNLOCK_CREDITS = "unlock_credits"
        
        private const val MAX_RESTART_COUNT = 3  // 3 次重启触发解锁
        private const val RESTART_WINDOW_MS = 5 * 60 * 1000L  // 5 分钟内
        private const val TEMP_UNLOCK_DURATION = 15 * 60 * 1000L  // 解锁 15 分钟
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            handleBootComplete(context)
        }
    }

    private fun handleBootComplete(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        var restartCount = prefs.getInt(KEY_RESTART_COUNT, 0)
        val lastRestartTime = prefs.getLong(KEY_LAST_RESTART_TIME, 0)
        val currentTime = System.currentTimeMillis()
        
        // 判断是否为连续重启（5 分钟内）
        if (currentTime - lastRestartTime < RESTART_WINDOW_MS) {
            restartCount++
            Log.d(TAG, "连续重启：$restartCount 次")
        } else {
            restartCount = 1
            Log.d(TAG, "首次重启（重置计数）")
        }
        
        // 保存计数
        prefs.edit().apply {
            putInt(KEY_RESTART_COUNT, restartCount)
            putLong(KEY_LAST_RESTART_TIME, currentTime)
            apply()
        }
        
        // 检查是否达到 3 次
        if (restartCount >= MAX_RESTART_COUNT) {
            val credits = prefs.getInt(KEY_UNLOCK_CREDITS, 0)
            
            if (credits > 0) {
                // 有额度，触发临时解锁
                triggerTempUnlock(context, prefs, credits)
            } else {
                // 额度不足，提示用户
                showInsufficientCreditsNotification(context)
            }
        }
        
        // 重新设置定时任务
        // LockService().scheduleLock(context, lockTime, unlockTime)
    }

    /**
     * 触发临时解锁
     */
    private fun triggerTempUnlock(context: Context, prefs: SharedPreferences, credits: Int) {
        Log.d(TAG, "触发临时解锁，原有额度：$credits 分钟")
        
        // 设置临时解锁时间
        val unlockUntil = System.currentTimeMillis() + TEMP_UNLOCK_DURATION
        prefs.edit().apply {
            putLong(KEY_UNLOCK_UNTIL, unlockUntil)
            putInt(KEY_UNLOCK_CREDITS, 0)  // 清零所有额度 ⭐
            putInt(KEY_RESTART_COUNT, 0)   // 重置重启计数
            apply()
        }
        
        Log.d(TAG, "临时解锁 15 分钟，额度已清零")
        
        // 停止监控服务
        // val monitorIntent = Intent(context, MonitorService::class.java)
        // context.stopService(monitorIntent)
        
        // 发送通知
        sendUnlockNotification(context, unlockUntil)
        
        // 记录日志
        logUnlockEvent(context, credits)
    }

    /**
     * 发送解锁通知
     */
    private fun sendUnlockNotification(context: Context, unlockUntil: Long) {
        // TODO: 发送通知告知用户已临时解锁 15 分钟
        Log.d(TAG, "发送解锁通知")
    }

    /**
     * 额度不足通知
     */
    private fun showInsufficientCreditsNotification(context: Context) {
        // TODO: 发送通知告知用户额度不足
        Log.w(TAG, "额度不足，无法解锁")
    }

    /**
     * 记录解锁事件
     */
    private fun logUnlockEvent(context: Context, credits: Int) {
        // TODO: 保存到额度表：type=UNLOCK_CLEAR, change=-credits, note="重启 3 次解锁，清零$credits 分钟"
        Log.d(TAG, "记录解锁事件：清零$credits 分钟")
    }

    /**
     * 检查是否为临时解锁状态
     */
    fun isTempUnlocked(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val unlockUntil = prefs.getLong(KEY_UNLOCK_UNTIL, 0)
        return System.currentTimeMillis() < unlockUntil
    }

    /**
     * 获取当前额度
     */
    fun getUnlockCredits(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_UNLOCK_CREDITS, 0)
    }

    /**
     * 增加额度（提前睡觉奖励）
     */
    fun addUnlockCredits(context: Context, minutes: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = prefs.getInt(KEY_UNLOCK_CREDITS, 0)
        val newCredits = minOf(current + minutes, 60 * 7)  // 最多累计 7 天*60 分钟
        
        prefs.edit().apply {
            putInt(KEY_UNLOCK_CREDITS, newCredits)
            apply()
        }
        
        Log.d(TAG, "增加额度：+$minutes 分钟，当前：$newCredits 分钟")
        
        // TODO: 保存到额度表：type=EARN, change=+minutes
    }
}

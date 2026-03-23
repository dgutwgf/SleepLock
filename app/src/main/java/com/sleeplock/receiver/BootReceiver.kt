package com.sleeplock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sleeplock.data.SleepLockDatabase
import com.sleeplock.data.entity.UserSettings
import com.sleeplock.service.LockService
import com.sleeplock.util.SchedulerManager
import com.sleeplock.util.UnlockCreditManager
import kotlinx.coroutines.*
import java.util.Calendar

/**
 * 开机启动接收器
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "开机启动广播已接收")
            
            ioScope.launch {
                try {
                    // 检查设备管理员权限
                    if (!LockService.isAdminActive(context)) {
                        Log.w(TAG, "设备管理员权限未激活")
                        return@launch
                    }
                    
                    // 恢复定时任务
                    val schedulerManager = SchedulerManager(context)
                    schedulerManager.scheduleAllTasks()
                    
                    // 处理重启解锁逻辑
                    handleRestartUnlock(context)
                    
                    // 每日重置（如果是早上 4 点后首次启动）
                    checkDailyReset(context)
                    
                    Log.d(TAG, "开机启动处理完成")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "开机启动处理失败", e)
                }
            }
        }
    }
    
    /**
     * 处理重启解锁逻辑
     */
    private suspend fun handleRestartUnlock(context: Context) {
        val creditManager = UnlockCreditManager(context)
        val restartCount = creditManager.recordRestart()
        
        Log.d(TAG, "重启计数：$restartCount")
        
        // 检查是否达到 3 次重启
        if (restartCount >= 3) {
            // 检查是否可以解锁
            if (creditManager.canUnlock()) {
                Log.d(TAG, "满足解锁条件，执行解锁")
                
                val success = creditManager.unlock()
                
                if (success) {
                    // 临时解锁 15 分钟
                    setTemporaryUnlock(context, 15)
                    Log.d(TAG, "解锁成功，临时解锁 15 分钟")
                } else {
                    Log.w(TAG, "解锁失败（额度不足或其他原因）")
                }
            } else {
                // 检查是否因为额度不足
                val balance = creditManager.getCurrentBalance()
                if (balance < UnlockCreditManager.CREDIT_PER_30MIN) {
                    Log.w(TAG, "额度不足：当前=$balance, 需要=${UnlockCreditManager.CREDIT_PER_30MIN}")
                    // TODO: 发送通知提示用户额度不足
                }
                
                // 重置重启计数（不解锁）
                creditManager.resetRestartCount()
            }
        }
    }
    
    /**
     * 设置临时解锁
     */
    private fun setTemporaryUnlock(context: Context, durationMinutes: Int) {
        // 这里可以设置一个定时任务，在 durationMinutes 分钟后恢复锁机
        // 简化处理：暂时不实现自动恢复，用户手动停止
        Log.d(TAG, "临时解锁 $durationMinutes 分钟")
    }
    
    /**
     * 检查是否需要每日重置
     */
    private suspend fun checkDailyReset(context: Context) {
        try {
            val db = SleepLockDatabase.getDatabase(context)
            val settings = db.userSettingsDao().getSettings() ?: return
            
            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            
            // 如果是凌晨 4-6 点之间，执行每日重置
            if (currentHour in 4..6) {
                val prefs = context.getSharedPreferences("unlock_credit_prefs", Context.MODE_PRIVATE)
                val lastResetDate = prefs.getString("last_reset_date", "")
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date())
                
                if (lastResetDate != today) {
                    val creditManager = UnlockCreditManager(context)
                    creditManager.dailyReset()
                    
                    prefs.edit().putString("last_reset_date", today).apply()
                    Log.d(TAG, "已执行每日重置")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "每日重置检查失败", e)
        }
    }
}

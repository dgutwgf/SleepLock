package com.sleeplock.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.sleeplock.data.SleepLockDatabase
import com.sleeplock.data.entity.UnlockCredit
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 解锁额度管理器 - 管理临时解锁额度的获取和消耗
 */
class UnlockCreditManager(private val context: Context) {
    
    companion object {
        private const val TAG = "UnlockCreditManager"
        private const val PREF_NAME = "unlock_credit_prefs"
        private const val KEY_RESTART_COUNT = "restart_count"
        private const val KEY_LAST_RESTART_TIME = "last_restart_time"
        private const val KEY_DAILY_UNLOCK_COUNT = "daily_unlock_count"
        private const val KEY_LAST_UNLOCK_DATE = "last_unlock_date"
        
        // 配置参数
        const val MAX_DAILY_UNLOCKS = 2           // 每天最多解锁 2 次
        const val RESTART_TIME_WINDOW = 5 * 60 * 1000L  // 5 分钟内重启算连续
        const val UNLOCK_DURATION = 15 * 60 * 1000L     // 每次解锁 15 分钟
        const val CREDIT_PER_30MIN = 15           // 每提前 30 分钟睡觉获得 15 分钟额度
        const val CREDIT_EXPIRY_DAYS = 7          // 额度 7 天过期
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 获取重启计数
     */
    fun getRestartCount(): Int {
        return prefs.getInt(KEY_RESTART_COUNT, 0)
    }
    
    /**
     * 获取上次重启时间
     */
    fun getLastRestartTime(): Long {
        return prefs.getLong(KEY_LAST_RESTART_TIME, 0)
    }
    
    /**
     * 获取今日解锁次数
     */
    fun getTodayUnlockCount(): Int {
        val lastDate = prefs.getString(KEY_LAST_UNLOCK_DATE, "")
        val today = dateFormat.format(Date())
        
        return if (lastDate == today) {
            prefs.getInt(KEY_DAILY_UNLOCK_COUNT, 0)
        } else {
            0
        }
    }
    
    /**
     * 记录重启
     * @return 当前重启计数
     */
    fun recordRestart(): Int {
        val currentTime = System.currentTimeMillis()
        val lastRestartTime = getLastRestartTime()
        
        // 检查是否在锁机时段（23:40 - 04:00）
        if (!isInLockPeriod()) {
            Log.d(TAG, "非锁机时段重启，不计数")
            resetRestartCount()
            return 0
        }
        
        // 判断是否为连续重启（5 分钟内）
        val count = if (currentTime - lastRestartTime < RESTART_TIME_WINDOW) {
            getRestartCount() + 1
        } else {
            1  // 非连续重启，重置为 1
        }
        
        // 保存计数
        prefs.edit().apply {
            putInt(KEY_RESTART_COUNT, count)
            putLong(KEY_LAST_RESTART_TIME, currentTime)
            apply()
        }
        
        Log.d(TAG, "记录重启，当前计数：$count")
        return count
    }
    
    /**
     * 重置重启计数
     */
    fun resetRestartCount() {
        prefs.edit().apply {
            putInt(KEY_RESTART_COUNT, 0)
            putLong(KEY_LAST_RESTART_TIME, 0)
            apply()
        }
        Log.d(TAG, "重启计数已重置")
    }
    
    /**
     * 检查是否可以解锁
     * @return 可解锁返回 true，否则返回 false
     */
    suspend fun canUnlock(): Boolean {
        val restartCount = getRestartCount()
        val todayUnlockCount = getTodayUnlockCount()
        
        Log.d(TAG, "检查解锁条件：重启次数=$restartCount, 今日解锁=$todayUnlockCount")
        
        // 检查重启次数是否达到 3 次
        if (restartCount < 3) {
            Log.d(TAG, "重启次数不足 3 次")
            return false
        }
        
        // 检查每日解锁次数是否超过上限
        if (todayUnlockCount >= MAX_DAILY_UNLOCKS) {
            Log.d(TAG, "今日解锁次数已达上限 ($MAX_DAILY_UNLOCKS)")
            return false
        }
        
        // 检查额度是否充足
        val currentBalance = getCurrentBalance()
        if (currentBalance < CREDIT_PER_30MIN) {
            Log.d(TAG, "额度不足：当前=$currentBalance, 需要=$CREDIT_PER_30MIN")
            return false
        }
        
        return true
    }
    
    /**
     * 执行解锁（扣除额度）
     * @return 解锁成功返回 true，失败返回 false
     */
    suspend fun unlock(): Boolean {
        if (!canUnlock()) {
            return false
        }
        
        try {
            val db = SleepLockDatabase.getDatabase(context)
            val today = dateFormat.format(Date())
            val currentBalance = getCurrentBalance()
            
            // 记录额度消耗（清零所有额度）
            val creditRecord = UnlockCredit(
                date = today,
                change = -currentBalance,  // 清零所有额度
                balance = 0,
                type = "UNLOCK_CLEAR",
                note = "连续重启 3 次解锁，清零 $currentBalance 分钟额度"
            )
            db.unlockCreditDao().insert(creditRecord)
            
            // 更新每日解锁次数
            val todayUnlockCount = getTodayUnlockCount()
            prefs.edit().apply {
                putInt(KEY_DAILY_UNLOCK_COUNT, todayUnlockCount + 1)
                putString(KEY_LAST_UNLOCK_DATE, today)
                apply()
            }
            
            // 重置重启计数
            resetRestartCount()
            
            Log.d(TAG, "解锁成功：扣除 $currentBalance 分钟额度，剩余额度=0")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "解锁失败", e)
            return false
        }
    }
    
    /**
     * 获取当前余额（7 天内累计）
     */
    suspend fun getCurrentBalance(): Int {
        return try {
            val db = SleepLockDatabase.getDatabase(context)
            val sevenDaysAgo = db.unlockCreditDao().getSevenDaysAgo()
            db.unlockCreditDao().getTotalCredits(sevenDaysAgo) ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "获取余额失败", e)
            0
        }
    }
    
    /**
     * 添加额度（提前睡觉奖励）
     * @param minutes 提前的分钟数
     */
    suspend fun addCredits(minutes: Int) {
        if (minutes < 30) {
            Log.d(TAG, "提前时间不足 30 分钟，不奖励额度")
            return
        }
        
        try {
            val db = SleepLockDatabase.getDatabase(context)
            val today = dateFormat.format(Date())
            
            // 计算奖励额度（每 30 分钟奖励 15 分钟，封顶 60 分钟/天）
            val earnedCredits = minOf((minutes / 30) * 15, 60)
            val currentBalance = getCurrentBalance()
            val newBalance = currentBalance + earnedCredits
            
            // 记录额度获取
            val creditRecord = UnlockCredit(
                date = today,
                change = earnedCredits,
                balance = newBalance,
                type = "EARN",
                note = "提前 $minutes 分钟睡觉，获得 $earnedCredits 分钟额度"
            )
            db.unlockCreditDao().insert(creditRecord)
            
            Log.d(TAG, "添加额度：提前=$minutes 分钟，获得=$earnedCredits 分钟，新余额=$newBalance")
            
        } catch (e: Exception) {
            Log.e(TAG, "添加额度失败", e)
        }
    }
    
    /**
     * 检查是否在锁机时段（23:40 - 04:00）
     */
    private fun isInLockPeriod(): Boolean {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentMinutes = currentHour * 60 + currentMinute
        
        val lockHour = 23
        val lockMinute = 40
        val unlockHour = 4
        val unlockMinute = 0
        
        val lockMinutes = lockHour * 60 + lockMinute  // 1420
        val unlockMinutes = unlockHour * 60 + unlockMinute  // 240
        
        // 跨夜情况：23:40 - 04:00
        return currentMinutes >= lockMinutes || currentMinutes < unlockMinutes
    }
    
    /**
     * 每日重置（早上 4 点调用）
     */
    fun dailyReset() {
        ioScope.launch {
            try {
                // 重置重启计数
                resetRestartCount()
                
                // 重置每日解锁次数
                prefs.edit().apply {
                    putInt(KEY_DAILY_UNLOCK_COUNT, 0)
                    putString(KEY_LAST_UNLOCK_DATE, dateFormat.format(Date()))
                    apply()
                }
                
                // 清理过期的额度记录
                val db = SleepLockDatabase.getDatabase(context)
                val expiryDate = dateFormat.format(Date(System.currentTimeMillis() - CREDIT_EXPIRY_DAYS * 24 * 60 * 60 * 1000))
                db.unlockCreditDao().deleteOlderThan(expiryDate)
                
                Log.d(TAG, "每日重置完成")
            } catch (e: Exception) {
                Log.e(TAG, "每日重置失败", e)
            }
        }
    }
    
    /**
     * 获取额度明细（用于显示）
     */
    suspend fun getCreditHistory(limit: Int = 30): List<UnlockCredit> {
        return try {
            val db = SleepLockDatabase.getDatabase(context)
            db.unlockCreditDao().getRecent()
        } catch (e: Exception) {
            Log.e(TAG, "获取历史记录失败", e)
            emptyList()
        }
    }
}

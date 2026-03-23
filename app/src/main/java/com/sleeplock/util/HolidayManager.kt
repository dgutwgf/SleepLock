package com.sleeplock.util

import android.content.Context
import android.util.Log
import com.sleeplock.data.SleepLockDatabase
import com.sleeplock.data.entity.Holiday
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 节假日管理器 - 管理节假日数据和锁屏时间调整
 */
class HolidayManager(private val context: Context) {
    
    companion object {
        private const val TAG = "HolidayManager"
    }
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 获取调整后的锁屏时间
     */
    fun getAdjustedLockTime(normalLockTime: String): String {
        // 检查是否需要推迟锁屏
        if (shouldDelayLock()) {
            // 推迟 1 小时
            val parts = normalLockTime.split(":")
            var hour = parts[0].toInt()
            val minute = parts[1].toInt()
            
            hour = (hour + 1) % 24
            return String.format("%02d:%02d", hour, minute)
        }
        
        return normalLockTime
    }
    
    /**
     * 获取调整后的解锁时间
     */
    fun getAdjustedUnlockTime(normalUnlockTime: String): String {
        // 检查是否需要推迟解锁
        if (shouldDelayLock()) {
            // 推迟 1 小时
            val parts = normalUnlockTime.split(":")
            var hour = parts[0].toInt()
            val minute = parts[1].toInt()
            
            hour = (hour + 1) % 24
            return String.format("%02d:%02d", hour, minute)
        }
        
        return normalUnlockTime
    }
    
    /**
     * 检查是否需要推迟锁屏
     */
    private fun shouldDelayLock(): Boolean {
        val calendar = Calendar.getInstance()
        val dateStr = dateFormat.format(calendar.time)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
        // 检查是否为周五或周六
        if (dayOfWeek == Calendar.FRIDAY || dayOfWeek == Calendar.SATURDAY) {
            Log.d(TAG, "周末，推迟锁屏")
            return true
        }
        
        // 检查是否为节假日
        ioScope.launch {
            try {
                val db = SleepLockDatabase.getDatabase(context)
                val isHoliday = db.holidayDao().isHoliday(dateStr)
                if (isHoliday) {
                    Log.d(TAG, "节假日，推迟锁屏")
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查节假日失败", e)
            }
        }
        
        return false
    }
    
    /**
     * 初始化节假日数据
     */
    fun initializeHolidays() {
        ioScope.launch {
            try {
                val db = SleepLockDatabase.getDatabase(context)
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                
                // 检查数据库是否为空
                val holidays = db.holidayDao().getByYear(currentYear)
                if (holidays.isEmpty()) {
                    Log.d(TAG, "加载本地预置节假日数据")
                    loadDefaultHolidays(currentYear)
                }
            } catch (e: Exception) {
                Log.e(TAG, "初始化节假日数据失败", e)
            }
        }
    }
    
    /**
     * 加载默认节假日数据
     */
    private suspend fun loadDefaultHolidays(year: Int) {
        val db = SleepLockDatabase.getDatabase(context)
        val holidays = mutableListOf<Holiday>()
        
        // 添加 2026 年节假日（示例数据）
        holidays.add(Holiday(date = "$year-01-01", name = "元旦", type = 1, year = year))
        holidays.add(Holiday(date = "$year-01-02", name = "元旦", type = 1, year = year))
        holidays.add(Holiday(date = "$year-01-03", name = "元旦", type = 1, year = year))
        
        // 春节（农历正月初一，这里简化为公历日期）
        holidays.add(Holiday(date = "$year-02-17", name = "春节", type = 1, year = year))
        holidays.add(Holiday(date = "$year-02-18", name = "春节", type = 1, year = year))
        holidays.add(Holiday(date = "$year-02-19", name = "春节", type = 1, year = year))
        holidays.add(Holiday(date = "$year-02-20", name = "春节", type = 1, year = year))
        holidays.add(Holiday(date = "$year-02-21", name = "春节", type = 1, year = year))
        holidays.add(Holiday(date = "$year-02-22", name = "春节", type = 1, year = year))
        holidays.add(Holiday(date = "$year-02-23", name = "春节", type = 1, year = year))
        
        // 清明节
        holidays.add(Holiday(date = "$year-04-05", name = "清明节", type = 1, year = year))
        
        // 劳动节
        holidays.add(Holiday(date = "$year-05-01", name = "劳动节", type = 1, year = year))
        holidays.add(Holiday(date = "$year-05-02", name = "劳动节", type = 1, year = year))
        holidays.add(Holiday(date = "$year-05-03", name = "劳动节", type = 1, year = year))
        holidays.add(Holiday(date = "$year-05-04", name = "劳动节", type = 1, year = year))
        holidays.add(Holiday(date = "$year-05-05", name = "劳动节", type = 1, year = year))
        
        // 端午节
        holidays.add(Holiday(date = "$year-05-31", name = "端午节", type = 1, year = year))
        
        // 中秋节
        holidays.add(Holiday(date = "$year-10-06", name = "中秋节", type = 1, year = year))
        
        // 国庆节
        holidays.add(Holiday(date = "$year-10-01", name = "国庆节", type = 1, year = year))
        holidays.add(Holiday(date = "$year-10-02", name = "国庆节", type = 1, year = year))
        holidays.add(Holiday(date = "$year-10-03", name = "国庆节", type = 1, year = year))
        holidays.add(Holiday(date = "$year-10-04", name = "国庆节", type = 1, year = year))
        holidays.add(Holiday(date = "$year-10-05", name = "国庆节", type = 1, year = year))
        holidays.add(Holiday(date = "$year-10-06", name = "国庆节", type = 1, year = year))
        holidays.add(Holiday(date = "$year-10-07", name = "国庆节", type = 1, year = year))
        holidays.add(Holiday(date = "$year-10-08", name = "国庆节", type = 1, year = year))
        
        db.holidayDao().insertAll(holidays)
        Log.d(TAG, "已加载 ${holidays.size} 条节假日数据")
    }
}

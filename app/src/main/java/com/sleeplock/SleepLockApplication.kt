package com.sleeplock

import android.app.Application
import com.sleeplock.data.entity.ExecutionLog
import com.sleeplock.util.AppContextHolder
import com.sleeplock.util.LogManager

/**
 * 应用全局上下文
 */
class SleepLockApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化应用上下文持有者
        AppContextHolder.init(this)
        
        // 记录应用启动日志
        LogManager.i(
            ExecutionLog.LogCategory.GENERAL,
            "AppStartup",
            "🚀 应用启动",
            "版本：${buildVersionName()}, Android: ${android.os.Build.VERSION.RELEASE}"
        )
    }
    
    private fun buildVersionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "未知"
        }
    }
}

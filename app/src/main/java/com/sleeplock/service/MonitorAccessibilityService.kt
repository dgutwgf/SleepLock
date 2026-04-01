package com.sleeplock.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.sleeplock.data.SleepLockDatabase
import com.sleeplock.data.entity.ExecutionLog
import com.sleeplock.ui.LockScreenActivity
import com.sleeplock.util.LogManager
import kotlinx.coroutines.*
import java.util.*

/**
 * 无障碍监控服务 - 严格监控前台应用，拦截非白名单应用
 * 
 * 核心功能：
 * - 实时监控前台应用变化
 * - 检测到非白名单应用立即拦截
 * - 显示拦截界面并强制返回桌面
 * - 记录违规日志
 */
class MonitorAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "MonitorAccessibility"
        private const val INTERCEPT_DELAY = 500L // 拦截延迟（防止误判）
        
        @Volatile
        private var instance: MonitorAccessibilityService? = null
        
        fun getInstance(): MonitorAccessibilityService? = instance
        fun isRunning(): Boolean = instance != null
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var isLockPeriod = false
    private var currentPackageName: String = ""
    private var lastInterceptTime = 0L
    private var interceptCount = 0 // 拦截次数统计
    private var isIntercepting = false // 是否正在拦截中
    
    // 基础白名单（始终允许）
    private val baseWhitelist = setOf(
        "com.android.phone",              // 电话
        "com.android.incallui",            // 来电界面
        "com.android.mms",                 // 短信
        "com.android.messaging",           // 短信（新版本）
        "com.android.settings",            // 设置
        "com.android.contacts",            // 联系人
        "com.android.dialer",              // 拨号器
        "com.sleeplock",                   // 本应用
        "com.sleeplock.ui",                // 本应用 UI
        "android",                         // 系统
        "com.android.systemui",            // 系统 UI
        "com.google.android.dialer",       // Google 拨号器
        "com.google.android.apps.messaging" // Google 短信
    )
    
    // 娱乐应用黑名单（这些应用会被严格拦截）
    private val entertainmentBlacklist = setOf(
        // 视频类
        "com.tencent.qqlive",              // 腾讯视频
        "com.qiyi.video",                  // 爱奇艺
        "com.youku.phone",                 // 优酷
        "com.baidu.video",                 // 百度视频
        "tv.danmaku.bili",                 // 哔哩哔哩
        "com.google.android.youtube",      // YouTube
        "com.ss.android.ugc.aweme",        // 抖音
        "com.ss.android.ugc.live",         // 抖音直播
        "com.kuaishou.nebula",             // 快手
        "com.xunmeng.pinduoduo",           // 拼多多（娱乐购物）
        
        // 游戏类
        "com.tencent.tmgp",                // 腾讯游戏
        "com.netease",                     // 网易游戏
        "com.miHoYo",                      // 米哈游
        "com.tencent.ig",                  // PUBG
        "com.tencent.tmgp.sgame",          // 王者荣耀
        "com.tencent.tmgp.cf",             // 穿越火线
        "com.epicgames.fortnite",          // 堡垒之夜
        "com.mojang.minecraftpe",          // 我的世界
        "com.roblox.client",               // Roblox
        "com.garena.game.freefire",        // Free Fire
        
        // 社交类（微信已移除，不再拦截）
        "com.tencent.mobileqq",            // QQ
        "com.sina.weibo",                  // 微博
        "com.instagram.android",           // Instagram
        "com.facebook.katana",             // Facebook
        "com.twitter.android",             // Twitter
        "com.snapchat.android",            // Snapchat
        "com.zhihu.android",               // 知乎
        "com.xiaohongshu.android",         // 小红书
        "com.douban.frodo",                // 豆瓣
        
        // 音乐类
        "com.netease.cloudmusic",          // 网易云音乐
        "com.tencent.qqmusic",             // QQ 音乐
        "com.kuwo.player",                 // 酷我音乐
        "music.163.com",                   // 网易云音乐
        "fm.xiami.main",                  // 虾米音乐
        
        // 阅读/小说类
        "com.qidian.QDReader",            // 起点读书
        "com.flyread.reader",             // 飞读小说
        "com.qq.reader",                  // QQ 阅读
        "com.dangdang.reader",            // 当当阅读
        
        // 直播类
        "com.douyu.live",                 // 斗鱼
        "com.huya.live",                  // 虎牙
        "com.live.bilibili"               // B 站直播
    )
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "🛡️ 无障碍服务已连接 - 严格监控模式")
        
        // 记录日志
        LogManager.serviceStatus("AccessibilityService", "✅ 服务已连接 - 严格监控模式")
        
        // 配置服务 - 最大化监控
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                   AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                   AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                   AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 50
        }
        serviceInfo = info
        
        // 启动定期检查
        startPeriodicCheck()
    }
    
    /**
     * 启动定期检查任务 - 持续监控，无论是否锁屏
     */
    private fun startPeriodicCheck() {
        serviceScope.launch {
            var checkCount = 0
            var lastLogTime = 0L
            while (isActive) {
                delay(10000) // 每 10 秒检查一次
                checkCount++
                
                // 每次检查前都更新锁机时段（确保状态同步）
                updateLockPeriod()
                
                // 只在屏幕亮起时输出日志（息屏时不输出，避免误以为在工作）
                val isScreenOn = withContext(Dispatchers.Main) {
                    val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                    powerManager.isInteractive
                }
                
                // 只在屏幕亮起时输出日志（最多每分钟一次）
                val now = System.currentTimeMillis()
                if (isScreenOn && now - lastLogTime > 60000) {
                    val prefs = getSharedPreferences("SleepLock", Context.MODE_PRIVATE)
                    val isLockActive = prefs.getBoolean("is_lock_active", false)
                    val testMode = prefs.getBoolean("test_mode", false)
                    Log.d(TAG, "📊 定时监控检查 #${checkCount} - 锁机=$isLockPeriod, 激活=$isLockActive, 测试=$testMode")
                    LogManager.d(ExecutionLog.LogCategory.SERVICE, "PeriodicCheck", 
                        "📊 定时监控检查 #${checkCount} - 锁机=$isLockPeriod, 激活=$isLockActive, 测试=$testMode")
                    lastLogTime = now
                }
                
                // 只要当前有应用就检查（不依赖 isLockPeriod，因为 updateLockPeriod 已更新它）
                checkCurrentApp()
            }
        }
    }
    
    /**
     * 定期检查当前应用
     */
    private suspend fun checkCurrentApp() {
        // 检查屏幕状态，锁屏时暂停监控
        val isScreenOn = withContext(Dispatchers.Main) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            powerManager.isInteractive
        }
        
        // 只在屏幕亮起时检查（锁屏时无法使用应用，无需监控）
        if (isScreenOn && currentPackageName.isNotEmpty() && !isIntercepting && isLockPeriod) {
            checkAndInterceptInternal(currentPackageName)
        }
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val packageName = event.packageName?.toString() ?: return
                
                // 包名变化时立即检查（排除正在拦截的情况）
                if (packageName != currentPackageName && !isIntercepting) {
                    val oldPackage = currentPackageName
                    currentPackageName = packageName
                    
                    Log.d(TAG, "🔄 窗口变化：$oldPackage → $packageName")
                    LogManager.d(ExecutionLog.LogCategory.ACCESSIBILITY, "WindowChange", 
                        "🔄 窗口变化：$oldPackage → $packageName")
                    
                    // 每次窗口变化时都更新锁机时段（确保解锁后立即恢复）
                    serviceScope.launch {
                        updateLockPeriod()
                        Log.d(TAG, "🔍 窗口变化后检查锁机状态：isLockPeriod=$isLockPeriod")
                        checkAndInterceptInternal(packageName)
                    }
                }
            }
        }
    }
    
    override fun onInterrupt() {
        Log.w(TAG, "⚠️ 无障碍服务被中断")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        Log.d(TAG, "无障碍服务已销毁")
    }
    
    /**
     * 检查并拦截应用 - 纯黑名单模式
     * 只拦截黑名单中的应用，其他应用全部放行
     */
    private suspend fun checkAndInterceptInternal(packageName: String) {
        // 详细记录每次检查
        Log.v(TAG, "🔍 检查应用：$packageName (isLockPeriod=$isLockPeriod)")
        
        // 检查是否在锁机时段
        if (!isLockPeriod) {
            Log.v(TAG, "⏭️ 非锁机时段，跳过拦截")
            return
        }
        
        // 检查是否在基础白名单（系统必要应用，直接放行）
        if (packageName in baseWhitelist) {
            Log.d(TAG, "✅ 系统白名单应用：$packageName")
            return
        }
        
        // 检查是否为系统应用（系统应用不拦截）
        if (isSystemApp(packageName)) {
            Log.d(TAG, "✅ 系统应用，放行：$packageName")
            return
        }
        
        // 检查是否在黑名单中（严格拦截）
        val isBlacklisted = isBlacklistedApp(packageName)
        if (isBlacklisted) {
            Log.w(TAG, "🚫 黑名单应用，立即拦截：$packageName")
            val appName = getAppName(packageName)
            LogManager.intercept("AppIntercept", packageName, "黑名单应用 - $appName")
            interceptApp(packageName, "黑名单应用", appName)
            return
        }
        
        // 其他应用：不在黑名单中，放行
        Log.d(TAG, "✅ 非黑名单应用，放行：$packageName")
    }
    
    /**
     * 判断是否为系统应用
     */
    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取应用名称
     */
    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
    
    /**
     * 判断是否为黑名单应用
     * 包括：明确黑名单 + 关键词匹配 + 数据库自定义黑名单
     */
    private suspend fun isBlacklistedApp(packageName: String): Boolean {
        Log.d(TAG, "🔍 检查应用是否在黑名单：$packageName")
        
        // 1. 检查是否在明确黑名单中（强制黑名单）
        if (packageName in entertainmentBlacklist) {
            Log.w(TAG, "⚠️ 明确黑名单拦截：$packageName")
            return true
        }
        
        // 2. 检查数据库中的自定义黑名单
        try {
            val db = SleepLockDatabase.getDatabase(this)
            val isCustomBlacklisted = db.appBlacklistDao().isBlacklisted(packageName)
            if (isCustomBlacklisted) {
                Log.w(TAG, "⚠️ 数据库自定义黑名单拦截：$packageName")
                // 获取黑名单详情
                try {
                    val item = db.appBlacklistDao().getByPackageName(packageName)
                    Log.w(TAG, "   黑名单记录：appName=${item?.appName}, isCustom=${item?.isCustom}, addedTime=${item?.addedTime}")
                } catch (e: Exception) {
                    Log.w(TAG, "   无法获取黑名单详情：${e.message}")
                }
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "查询自定义黑名单失败", e)
        }
        
        // 3. 关键词匹配（只匹配明确的娱乐应用，避免误判）
        // 注意：不再使用宽泛的关键词，只保留精确匹配
        val preciseKeywords = listOf(
            // 游戏类
            ".tmgp.sgame", ".tmgp.ig", ".tmgp.cod", ".tmgp.dnf",  // 腾讯游戏
            ".miHoYo.Genshin", ".miHoYo.Yuanshen", ".miHoYo.hkrpg",  // 米哈游
            ".netease.onmyoji", ".netease.mrzh",  // 网易游戏
            ".minecraftpe", ".roblox", ".freefire",  // 国际游戏
            // 视频类
            ".qqlive", ".qiyi.video", ".youku.phone",  // 国内视频
            ".danmaku.bili", ".youtube",  // B 站/YouTube
            ".ugc.aweme", ".kuaishou",  // 抖音快手
            // 社交娱乐
            ".weibo", ".xiaohongshu", ".zhihu", ".douban",  // 社交
            // 音乐类
            ".cloudmusic", ".qqmusic", ".kugou", ".kuwo",  // 音乐
            // 浏览器
            ".chrome", "UCMobile", ".qq.browser",  // 浏览器（排除系统）
            // 直播
            ".douyu", ".huya", ".huajiao", ".inke"  // 直播
        )
        
        // 精确匹配：包名包含完整关键词
        val lowerPackageName = packageName.lowercase()
        return preciseKeywords.any { it in lowerPackageName }
    }
    
    /**
     * 强制停止应用（清除后台）
     */
    private fun forceStopApp(packageName: String) {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            
            // 方法 1: 发送强制停止广播
            try {
                val forceStopIntent = Intent("android.intent.action.FORCE_STOP_PACKAGE")
                forceStopIntent.setPackage("com.android.settings")
                forceStopIntent.putExtra("package", packageName)
                forceStopIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                sendBroadcast(forceStopIntent)
                Log.d(TAG, "✅ 发送强制停止广播：$packageName")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 强制停止广播失败：${e.message}")
            }
            
            // 方法 2: 杀死后台进程
            try {
                activityManager.killBackgroundProcesses(packageName)
                Log.d(TAG, "✅ 杀死后台进程：$packageName")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 杀死后台进程失败：${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 强制停止应用失败：${e.message}", e)
        }
    }
    
    /**
     * 拦截应用 - 无冷却时间，持续拦截
     */
    private fun interceptApp(packageName: String, reason: String, appName: String = "") {
        // 防止重复拦截
        if (isIntercepting) {
            Log.d(TAG, "⚠️ 正在拦截中，跳过：$packageName")
            return
        }
        
        isIntercepting = true
        val now = System.currentTimeMillis()
        lastInterceptTime = now
        interceptCount++
        
        val displayName = appName.ifEmpty { packageName }
        Log.w(TAG, "🚫 拦截 #${interceptCount} - $displayName ($reason)")
        
        // 记录拦截日志（包含应用名称）
        LogManager.intercept("AppIntercept", packageName, "$reason - $displayName")
        
        // 更新统计数据
        updateInterceptStats(packageName, appName)
        
        // 方法 1: 立即启动拦截界面（确保先显示界面）
        mainHandler.post {
            try {
                val intent = Intent(this@MonitorAccessibilityService, LockScreenActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                             Intent.FLAG_ACTIVITY_CLEAR_TASK or
                             Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                             Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    putExtra("mode", LockScreenActivity.Mode.LOCK_PERIOD.name)
                    putExtra("package_name", packageName)
                    putExtra("reason", reason)
                    putExtra("app_name", displayName)
                }
                startActivity(intent)
                Log.d(TAG, "✅ 已显示拦截界面")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 显示拦截界面失败", e)
                isIntercepting = false
            }
        }
        
        // 方法 2: 0.5 秒后强制停止应用（清除后台）
        mainHandler.postDelayed({
            forceStopApp(packageName)
            Log.d(TAG, "🛑 已强制停止应用：$packageName")
        }, 500)
        
        // 方法 3: 5 秒后模拟 Home 键（让用户看警告 5 秒）
        mainHandler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_HOME)
            Log.d(TAG, "🏠 已返回桌面")
        }, 5000)
        
        // 方法 4: 6 秒后再次确保
        mainHandler.postDelayed({
            if (isLockPeriod) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                Log.d(TAG, "🔒 二次确认返回桌面")
            }
        }, 6000)
        
        // 方法 5: 7 秒后重置拦截状态（确保警告显示至少 5 秒）
        mainHandler.postDelayed({
            isIntercepting = false
            Log.d(TAG, "🔄 拦截状态已重置")
        }, 7000)
        
        // 记录违规日志
        recordViolationLog(packageName, appName, reason)
    }
    
    /**
     * 更新拦截统计数据
     */
    private fun updateInterceptStats(packageName: String, appName: String) {
        serviceScope.launch {
            try {
                val db = SleepLockDatabase.getDatabase(this@MonitorAccessibilityService)
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(java.util.Date())
                
                // 获取今日统计
                var stats = db.dailyLockStatsDao().getByDate(today)
                
                if (stats == null) {
                    // 创建新统计
                    stats = com.sleeplock.data.entity.DailyLockStats(
                        date = today,
                        lockStartTime = System.currentTimeMillis(),
                        interceptCount = 1,
                        topBlockedApps = "{\"$packageName\":1}"
                    )
                    db.dailyLockStatsDao().insert(stats)
                } else {
                    // 更新统计
                    stats = stats.copy(
                        interceptCount = stats.interceptCount + 1,
                        unlockTime = System.currentTimeMillis(),
                        lockDuration = (System.currentTimeMillis() - stats.lockStartTime) / 1000
                    )
                    db.dailyLockStatsDao().update(stats)
                }
                
                Log.d(TAG, "📊 拦截统计已更新：$today, 次数：${stats.interceptCount}")
            } catch (e: Exception) {
                Log.e(TAG, "更新拦截统计失败", e)
            }
        }
    }
    
    /**
     * 记录违规日志
     */
    private fun recordViolationLog(packageName: String, appName: String, reason: String) {
        serviceScope.launch {
            try {
                val db = SleepLockDatabase.getDatabase(this@MonitorAccessibilityService)
                val displayName = appName.ifEmpty { packageName }
                db.violationLogDao().insert(
                    com.sleeplock.data.entity.ViolationLog(
                        type = "APP_INTERCEPT",
                        description = "尝试打开$reason：$displayName (拦截 #${interceptCount})"
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "记录违规日志失败", e)
            }
        }
    }
    
    /**
     * 设置锁机时段
     */
    fun setLockPeriod(isLock: Boolean) {
        isLockPeriod = isLock
        Log.d(TAG, "🔐 锁机时段：$isLock (拦截次数：$interceptCount)")
        LogManager.d(ExecutionLog.LogCategory.SERVICE, "LockPeriod", "🔐 锁机时段：$isLock (拦截次数：$interceptCount)")
    }
    
    /**
     * 获取当前包名（供 ScreenReceiver 使用）
     */
    fun getCurrentPackageName(): String = currentPackageName
    
    /**
     * 公开检查拦截方法（供 ScreenReceiver 使用）
     */
    suspend fun checkAndIntercept(packageName: String) {
        checkAndInterceptInternal(packageName)
    }
    
    /**
     * 重置拦截计数
     */
    fun resetInterceptCount() {
        interceptCount = 0
        Log.d(TAG, "拦截计数已重置")
    }
    
    /**
     * 检查当前时间是否在锁机时段 - 同时检查 is_lock_active 标志
     */
    suspend fun updateLockPeriod() {
        try {
            val db = SleepLockDatabase.getDatabase(this)
            val settings = db.userSettingsDao().getSettings() ?: return
            
            val currentTime = Calendar.getInstance()
            val lockTime = parseTime(settings.lockTime)
            val unlockTime = parseTime(settings.unlockTime)
            
            val currentMinutes = currentTime.get(Calendar.HOUR_OF_DAY) * 60 + currentTime.get(Calendar.MINUTE)
            val lockMinutes = lockTime.first * 60 + lockTime.second
            val unlockMinutes = unlockTime.first * 60 + unlockTime.second
            
            // 处理跨夜情况
            val isInTimePeriod = if (lockMinutes > unlockMinutes) {
                // 跨夜：23:40 ~ 04:00，即 currentMinutes >= 1420 || currentMinutes < 240
                currentMinutes >= lockMinutes || currentMinutes < unlockMinutes
            } else {
                // 不跨夜：lockMinutes <= currentMinutes < unlockMinutes
                currentMinutes >= lockMinutes && currentMinutes < unlockMinutes
            }
            
            // 调试日志：输出详细的时间计算
            Log.v(TAG, "⏰ 时间检查：当前=$currentMinutes (${currentTime.get(Calendar.HOUR_OF_DAY)}:${currentTime.get(Calendar.MINUTE)}), " +
                "锁机=$lockMinutes-${unlockMinutes} (${lockTime.first}:${lockTime.second}-${unlockTime.first}:${unlockTime.second}), " +
                "在时段内=$isInTimePeriod")
            
            // 检查 is_lock_active 标志（用户是否启动了锁机服务）
            val prefs = getSharedPreferences("SleepLock", Context.MODE_PRIVATE)
            val isLockActive = prefs.getBoolean("is_lock_active", false)
            val testMode = prefs.getBoolean("test_mode", false)
            
            // 测试模式：忽略时间段，直接锁定
            // 正常模式：必须同时满足：在锁机时段内 + 锁机服务已激活
            val shouldBeLocked = if (testMode) {
                true  // 测试模式下始终锁定
            } else {
                isInTimePeriod && isLockActive
            }
            
            // 关键修复：如果不在锁机时段但 isLockActive 仍为 true，自动清除标志
            // 这确保即使定时解锁广播未触发，也能在解锁时段自动停止拦截
            if (!isInTimePeriod && isLockActive && !testMode) {
                Log.w(TAG, "⚠️ 检测到不在锁机时段但 is_lock_active 仍为 true，自动清除标志")
                prefs.edit()
                    .putBoolean("is_lock_active", false)
                    .putBoolean("test_mode", false)
                    .apply()
                
                LogManager.e(ExecutionLog.LogCategory.SCHEDULER, "AutoUnlock", 
                    "⚠️ 自动清除锁机标志：当前时间不在锁机时段内")
                
                // 停止拦截
                isLockPeriod = false
                val logMsg = "🔓 自动解锁：已清除 is_lock_active (时间=$isInTimePeriod, 测试=$testMode)"
                Log.d(TAG, logMsg)
                LogManager.d(ExecutionLog.LogCategory.SERVICE, "LockPeriod", logMsg)
                return
            }
            
            if (shouldBeLocked != isLockPeriod) {
                isLockPeriod = shouldBeLocked
                val logMsg = "🔄 锁机时段更新：$isLockPeriod (时间=$isInTimePeriod, 激活=$isLockActive, 测试=$testMode)"
                Log.d(TAG, logMsg)
                LogManager.d(ExecutionLog.LogCategory.SERVICE, "LockPeriod", logMsg)
                
                if (isLockPeriod) {
                    resetInterceptCount()
                }
            } else if (isLockPeriod) {
                // 即使状态没变，也定期输出日志确认
                Log.v(TAG, "🔐 锁机状态保持：$isLockPeriod (时间=$isInTimePeriod, 激活=$isLockActive)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新锁机时段失败", e)
            LogManager.e(ExecutionLog.LogCategory.SERVICE, "LockPeriod", "❌ 更新锁机时段失败：${e.message}")
        }
    }
    
    /**
     * 解析时间字符串
     */
    private fun parseTime(timeStr: String): Pair<Int, Int> {
        val parts = timeStr.split(":")
        return Pair(parts[0].toInt(), parts[1].toInt())
    }
}

package com.sleeplock.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * 无障碍监控服务 - 应用白名单监控
 * 
 * 功能：
 * 1. 监控前台应用
 * 2. 拦截非白名单应用
 * 3. 娱乐应用识别和拦截
 */
class MonitorAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MonitorAccessibility"
        
        // 基础白名单（始终允许）
        val BASE_WHITELIST = setOf(
            "com.android.phone",           // 电话
            "com.android.messaging",       // 短信
            "com.android.incallui",        // 来电界面
            "com.miui.securitycenter",     // 手机管家
            "com.android.settings",        // 设置
            "com.sleeplock",               // 本应用
            "com.sleeplock.debug"          // 本应用 debug 版
        )
        
        // 娱乐应用关键词
        val ENTERTAIN_KEYWORDS = setOf(
            "tmgp", "game", "gaming",      // 游戏
            "douyin", "kuaishou", "bilibili", // 视频
            "iqiyi", "youku", "qqvideo",
            "weibo", "xiaohongshu", "douban" // 社交娱乐
        )
    }

    private var isLockPeriod = false
    private var userWhitelist = setOf<String>()
    private var unlockCredits = 0  // 临时解锁额度（分钟）

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "无障碍服务已连接")
        loadUserWhitelist()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            // 检查是否为锁机时段
            if (isLockPeriod) {
                if (!isAllowed(packageName)) {
                    // 拦截非白名单应用
                    returnToHome()
                    showBlockToast(packageName)
                    logViolation(packageName)
                }
            }
        }
    }

    /**
     * 检查应用是否允许使用
     */
    private fun isAllowed(packageName: String): Boolean {
        // 1. 检查基础白名单
        if (BASE_WHITELIST.contains(packageName)) return true
        
        // 2. 检查用户白名单
        if (userWhitelist.contains(packageName)) {
            // 3. 检查是否为娱乐应用（娱乐应用即使加入白名单也拦截）
            return !isEntertainmentApp(packageName)
        }
        
        return false
    }

    /**
     * 判断是否为娱乐应用
     */
    private fun isEntertainmentApp(packageName: String): Boolean {
        val lowerName = packageName.lowercase()
        return ENTERTAIN_KEYWORDS.any { it in lowerName }
    }

    /**
     * 强制返回桌面
     */
    private fun returnToHome() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val gesture = GestureDescription.Builder()
            val path = Path()
            path.moveTo(100f, 500f)
            gesture.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            
            performGlobalAction(GLOBAL_ACTION_HOME)
        } else {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    /**
     * 显示拦截提示
     */
    private fun showBlockToast(packageName: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, "锁机时段不允许使用：$packageName", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 记录违规日志
     */
    private fun logViolation(packageName: String) {
        Log.w(TAG, "拦截应用：$packageName")
        // TODO: 保存到违规日志数据库
    }

    /**
     * 加载用户白名单
     */
    private fun loadUserWhitelist() {
        // TODO: 从数据库加载
        userWhitelist = emptySet()
    }

    /**
     * 设置锁机时段
     */
    fun setLockPeriod(locked: Boolean) {
        isLockPeriod = locked
        Log.d(TAG, "锁机时段：$locked")
    }

    /**
     * 设置解锁额度
     */
    fun setUnlockCredits(credits: Int) {
        unlockCredits = credits
        Log.d(TAG, "解锁额度：$credits 分钟")
    }

    override fun onInterrupt() {
        Log.w(TAG, "服务中断")
    }
}

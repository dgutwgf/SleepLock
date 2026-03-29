package com.sleeplock.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.sleeplock.service.LockService
import com.sleeplock.ui.MainActivity

/**
 * 锁屏/拦截 Activity - 当检测到非白名单应用时显示
 * 
 * 功能：
 * - 拦截非白名单应用时显示全屏警告
 * - 显示倒计时和提示信息
 * - 禁止返回、Home 键等操作
 * - 倒计时结束后自动返回桌面
 */
class LockScreenActivity : Activity() {
    
    companion object {
        private const val TAG = "LockScreenActivity"
        private const val PREFS_NAME = "SleepLock"
        private const val INTERCEPT_DURATION = 5 * 1000L // 拦截界面显示 5 秒（最少）
        private const val RECHECK_DELAY = 100L // 重新检查延迟
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private lateinit var countdownTextView: TextView
    private lateinit var messageTextView: TextView
    private lateinit var appInfoTextView: TextView
    private var countdownRunnable: Runnable? = null
    private var remainingSeconds = 5
    private var interceptedPackageName: String = ""
    private var interceptReason: String = ""
    private var interceptStartTime: Long = 0
    
    enum class Mode {
        LOCK_PERIOD,      // 锁机时段拦截
        TEST_LOCK,        // 测试锁机
        MANUAL_LOCK       // 手动锁定
    }
    
    /**
     * 配置窗口属性
     */
    private fun setupWindow() {
        // 在锁屏界面之上显示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        // 全屏
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // 防止截图
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
        // 隐藏导航栏和状态栏
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }
    
    /**
     * 创建 UI
     */
    private fun createUI(mode: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            setPadding(50, 80, 50, 80)
            
            // 锁定图标
            addView(TextView(this@LockScreenActivity).apply {
                text = "⛔"
                textSize = 80f
                gravity = Gravity.CENTER
            })
            
            // 标题
            addView(TextView(this@LockScreenActivity).apply {
                text = when (mode) {
                    Mode.TEST_LOCK.name -> "测试锁机中"
                    Mode.MANUAL_LOCK.name -> "手动锁定中"
                    else -> "专注时间"
                }
                textSize = 32f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 30, 0, 20)
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            
            // 应用信息（突出显示）
            appInfoTextView = TextView(this@LockScreenActivity).apply {
                val appName = getAppName(interceptedPackageName)
                val displayText = if (interceptedPackageName.isNotEmpty()) {
                    "🚫 已拦截\n\n应用名称：$appName\n包名：$interceptedPackageName\n拦截原因：$interceptReason"
                } else {
                    "🚫 已拦截非白名单应用"
                }
                text = displayText
                textSize = 16f
                setTextColor(Color.parseColor("#FF6B6B"))
                gravity = Gravity.CENTER
                setPadding(30, 30, 30, 30)
                setBackgroundColor(Color.parseColor("#2d2d44"))
            }
            addView(appInfoTextView)
            
            // 倒计时（至少 5 秒）
            countdownTextView = TextView(this@LockScreenActivity).apply {
                text = "请等待 $remainingSeconds 秒"
                textSize = 24f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 30, 0, 30)
            }
            addView(countdownTextView)
            
            // 提示信息
            addView(TextView(this@LockScreenActivity).apply {
                text = "⏰ 锁机时段内禁止使用娱乐应用\n请返回桌面或使用白名单应用"
                textSize = 14f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 20)
            })
            
            // 警告
            addView(TextView(this@LockScreenActivity).apply {
                text = "⚠️ 频繁尝试打开黑名单应用将被记录"
                textSize = 12f
                setTextColor(Color.parseColor("#FF4444"))
                gravity = Gravity.CENTER
                setPadding(0, 15, 0, 0)
            })
        }
    }
    
    /**
     * 获取应用名称
     */
    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName // 如果获取失败，返回包名
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: Mode.LOCK_PERIOD.name
        interceptedPackageName = intent.getStringExtra("package_name") ?: ""
        interceptReason = intent.getStringExtra("reason") ?: ""
        
        Log.d(TAG, "🔒 拦截界面启动 - 模式：$mode, 应用：$interceptedPackageName, 原因：$interceptReason")
        
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        setupWindow()
        setContentView(createUI(mode))
        
        // 记录开始时间
        interceptStartTime = System.currentTimeMillis()
        
        // 开始倒计时（至少 5 秒）
        startCountdown()
        
        // 启动持续监控，防止应用绕过
        startContinuousMonitor()
    }
    
    /**
     * 开始倒计时（至少 5 秒）
     */
    private fun startCountdown() {
        countdownRunnable = object : Runnable {
            override fun run() {
                val elapsedSeconds = ((System.currentTimeMillis() - interceptStartTime) / 1000).toInt()
                val displaySeconds = maxOf(5 - elapsedSeconds, 0)
                remainingSeconds = displaySeconds
                
                countdownTextView.text = "请等待 $displaySeconds 秒"
                
                // 确保至少显示 5 秒
                if (elapsedSeconds >= 5) {
                    Log.d(TAG, "倒计时结束（已显示 ${elapsedSeconds}秒），返回桌面并清除应用")
                    forceStopInterceptedApp()
                    returnToHome()
                    return
                }
                
                handler.postDelayed(this, 1000)
            }
        }
        
        handler.post(countdownRunnable!!)
    }
    
    /**
     * 强制停止被拦截的应用（清除后台）
     */
    private fun forceStopInterceptedApp() {
        if (interceptedPackageName.isEmpty()) return
        
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            
            // 方法 1: 发送强制停止广播
            try {
                val forceStopIntent = Intent("android.intent.action.FORCE_STOP_PACKAGE")
                forceStopIntent.setPackage("com.android.settings")
                forceStopIntent.putExtra("package", interceptedPackageName)
                forceStopIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                sendBroadcast(forceStopIntent)
                Log.d(TAG, "✅ 发送强制停止广播：$interceptedPackageName")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 强制停止广播失败：${e.message}")
            }
            
            // 方法 2: 杀死后台进程
            try {
                activityManager.killBackgroundProcesses(interceptedPackageName)
                Log.d(TAG, "✅ 杀死后台进程：$interceptedPackageName")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 杀死后台进程失败：${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 强制停止应用失败：${e.message}", e)
        }
    }
    
    /**
     * 启动持续监控，防止应用在拦截期间启动
     */
    private fun startContinuousMonitor() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!isFinishing) {
                    val elapsedSeconds = ((System.currentTimeMillis() - interceptStartTime) / 1000).toInt()
                    if (elapsedSeconds < 5) {
                        // 确保拦截界面始终在最上层（至少 5 秒）
                        window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        )
                        handler.postDelayed(this, RECHECK_DELAY)
                    }
                }
            }
        }, RECHECK_DELAY)
    }
    
    /**
     * 返回桌面
     */
    private fun returnToHome() {
        try {
            // 模拟 Home 键
            val homeIntent = Intent(Intent.ACTION_MAIN)
            homeIntent.addCategory(Intent.CATEGORY_HOME)
            homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(homeIntent)
            Log.d(TAG, "✅ 已返回桌面")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 返回桌面失败", e)
        }
        
        // 关闭当前 Activity
        finish()
    }
    
    /**
     * 完全禁止返回键
     */
    override fun onBackPressed() {
        Log.d(TAG, "❌ 阻止返回键")
    }
    
    /**
     * 阻止 Home 键 - 立即响应
     */
    override fun onUserLeaveHint() {
        Log.d(TAG, "⚠️ 检测到 Home 键，立即拦截")
        // 立即将当前界面置顶
        handler.post {
            if (!isFinishing) {
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            }
        }
    }
    
    /**
     * 阻止所有按键
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_POWER) {
            return super.onKeyDown(keyCode, event)
        }
        Log.d(TAG, "❌ 阻止按键：$keyCode")
        return true
    }
    
    /**
     * 消费所有触摸事件
     */
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return true
    }
    
    /**
     * 窗口焦点变化时恢复全屏
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "拦截界面销毁")
    }
}

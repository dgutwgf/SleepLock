package com.sleeplock.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.sleeplock.service.LockService

/**
 * 锁屏 Activity - 强制锁屏界面，无法退出
 * 显示一个全屏界面，阻止用户操作其他应用
 */
class LockScreenActivity : Activity() {
    
    companion object {
        private const val TAG = "LockScreenActivity"
        private const val PREFS_NAME = "SleepLock"
        private const val TEST_LOCK_DURATION = 2 * 60 * 1000L // 2分钟
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private lateinit var statusTextView: TextView
    private lateinit var timeTextView: TextView
    private var checkRunnable: Runnable? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "🔒 锁屏 Activity 启动")
        
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        setupWindow()
        setContentView(createLockScreenUI())
        
        // 开始检查锁机状态
        startLockCheck()
    }
    
    /**
     * 配置窗口属性 - 最强防护
     */
    private fun setupWindow() {
        // 在锁屏界面之上显示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
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
     * 创建锁屏 UI
     */
    private fun createLockScreenUI(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(50, 100, 50, 100)
            
            // 锁定图标
            addView(TextView(this@LockScreenActivity).apply {
                text = "🔒"
                textSize = 80f
                gravity = Gravity.CENTER
            })
            
            // 标题
            addView(TextView(this@LockScreenActivity).apply {
                text = "专注锁机中"
                textSize = 32f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 40, 0, 20)
            })
            
            // 时间显示
            timeTextView = TextView(this@LockScreenActivity).apply {
                text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date())
                textSize = 48f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 20)
            }
            addView(timeTextView)
            
            // 状态信息
            statusTextView = TextView(this@LockScreenActivity).apply {
                text = "测试模式：剩余 2 分钟"
                textSize = 18f
                setTextColor(Color.YELLOW)
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 40)
            }
            addView(statusTextView)
            
            // 提示信息
            addView(TextView(this@LockScreenActivity).apply {
                text = "当前处于锁机时段\n请休息或使用白名单应用"
                textSize = 14f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 40)
            })
            
            // 管理员状态
            if (!LockService.isAdminActive(this@LockScreenActivity)) {
                addView(TextView(this@LockScreenActivity).apply {
                    text = "⚠️ 设备管理员权限未激活"
                    textSize = 14f
                    setTextColor(Color.RED)
                    gravity = Gravity.CENTER
                    setPadding(0, 20, 0, 20)
                })
            }
        }
    }
    
    /**
     * 开始检查锁机状态
     */
    private fun startLockCheck() {
        checkRunnable = object : Runnable {
            override fun run() {
                // 更新时间
                timeTextView.text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date())
                
                // 检查是否仍在锁机时段
                val isLockActive = prefs.getBoolean("is_lock_active", false)
                val testMode = prefs.getBoolean("test_mode", false)
                
                if (!isLockActive) {
                    Log.d(TAG, "锁机已停止，关闭锁屏界面")
                    finish()
                    return
                }
                
                if (testMode) {
                    val lockStartTime = prefs.getLong("lock_start_time", 0)
                    val elapsed = System.currentTimeMillis() - lockStartTime
                    val remaining = (TEST_LOCK_DURATION - elapsed) / 1000
                    
                    if (remaining <= 0) {
                        Log.d(TAG, "测试时间结束，关闭锁屏界面")
                        finish()
                        return
                    }
                    
                    val minutes = remaining / 60
                    val seconds = remaining % 60
                    statusTextView.text = "测试模式：剩余 ${minutes}分${seconds}秒"
                }
                
                // 继续检查
                handler.postDelayed(this, 1000)
            }
        }
        
        handler.post(checkRunnable!!)
    }
    
    override fun onBackPressed() {
        // 完全禁止返回键
        Log.d(TAG, "❌ 阻止返回键操作")
    }
    
    override fun onUserLeaveHint() {
        // 检测到 Home 键，立即重新启动自己
        Log.d(TAG, "⚠️ 检测到 Home 键，重新启动锁屏界面")
        
        if (prefs.getBoolean("is_lock_active", false)) {
            val intent = android.content.Intent(this, LockScreenActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                         android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
        }
    }
    
    override fun onKeyLongPress(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        // 阻止长按事件（如长按电源键）
        Log.d(TAG, "❌ 阻止长按按键: $keyCode")
        return true
    }
    
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        // 阻止所有按键
        Log.d(TAG, "❌ 阻止按键: $keyCode")
        return true
    }
    
    override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
        // 消费所有触摸事件
        return true
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "锁屏 Activity 销毁")
        
        // 如果锁机仍在激活，重新启动自己
        if (prefs.getBoolean("is_lock_active", false)) {
            Log.d(TAG, "锁机仍在激活，重新启动锁屏界面")
            val intent = android.content.Intent(this, LockScreenActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }
}
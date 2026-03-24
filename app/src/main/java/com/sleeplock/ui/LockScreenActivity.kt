package com.sleeplock.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.sleeplock.service.LockService

/**
 * 锁屏 Activity - 当其他锁屏方法失败时的备用方案
 * 显示一个全屏界面，阻止用户操作其他应用
 */
class LockScreenActivity : Activity() {
    
    companion object {
        private const val TAG = "LockScreenActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "锁屏 Activity 启动")
        
        setupWindow()
        setContentView(createLockScreenUI())
    }
    
    /**
     * 配置窗口属性
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
            
            // 提示信息
            addView(TextView(this@LockScreenActivity).apply {
                text = "当前处于锁机时段\n请休息或使用白名单应用"
                textSize = 16f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 40)
            })
            
            // 时间显示
            addView(TextView(this@LockScreenActivity).apply {
                id = View.generateViewId()
                text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date())
                textSize = 48f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 40, 0, 60)
            })
            
            // 管理员状态提示
            if (!LockService.isAdminActive(this@LockScreenActivity)) {
                addView(TextView(this@LockScreenActivity).apply {
                    text = "⚠️ 设备管理员权限未激活"
                    textSize = 14f
                    setTextColor(Color.YELLOW)
                    gravity = Gravity.CENTER
                    setPadding(0, 20, 0, 20)
                })
            }
        }
    }
    
    override fun onBackPressed() {
        // 禁止返回键
        Log.d(TAG, "阻止返回键操作")
    }
    
    override fun onUserLeaveHint() {
        // 防止用户按 Home 键离开
        Log.d(TAG, "检测到 Home 键操作")
        // 立即重新启动锁屏 Activity
        val intent = android.content.Intent(this, LockScreenActivity::class.java)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "锁屏 Activity 销毁")
    }
}
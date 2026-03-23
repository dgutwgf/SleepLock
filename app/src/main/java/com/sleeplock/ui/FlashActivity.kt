package com.sleeplock.ui

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager

/**
 * 闪屏提醒 Activity - 用于睡前提醒时屏幕闪烁
 */
class FlashActivity : Activity() {
    
    companion object {
        private const val FLASH_DURATION = 10000L // 10 秒
        private const val FLASH_INTERVAL = 500L   // 0.5 秒
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private var isRed = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置全屏
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // 创建全屏 View
        val view = View(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        setContentView(view)
        
        // 开始闪烁
        startFlashing()
        
        // 10 秒后自动关闭
        handler.postDelayed({
            finish()
        }, FLASH_DURATION)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
    
    /**
     * 开始屏幕闪烁
     */
    private fun startFlashing() {
        handler.post(object : Runnable {
            override fun run() {
                isRed = !isRed
                window.decorView.setBackgroundColor(
                    if (isRed) Color.RED else Color.BLACK
                )
                
                handler.postDelayed(this, FLASH_INTERVAL)
            }
        })
    }
    
    override fun onBackPressed() {
        // 禁止返回
    }
}

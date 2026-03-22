package com.sleeplock.ui

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.view.ViewGroup

/**
 * 主界面 - 最简版本（不使用 Compose）
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 创建布局
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(50, 50, 50, 50)
        layout.setBackgroundColor(Color.WHITE)
        
        // 标题
        val title = TextView(this)
        title.text = "专注锁机 v0.1.0"
        title.textSize = 24f
        title.setTextColor(Color.BLACK)
        layout.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        
        // 副标题
        val subtitle = TextView(this)
        subtitle.text = "安装成功！"
        subtitle.textSize = 18f
        subtitle.setTextColor(Color.BLUE)
        layout.addView(subtitle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        
        // 提示
        val hint = TextView(this)
        hint.text = "APK 文件正常，可以安装"
        hint.textSize = 16f
        hint.setTextColor(Color.GRAY)
        layout.addView(hint, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        
        setContentView(layout)
    }
}

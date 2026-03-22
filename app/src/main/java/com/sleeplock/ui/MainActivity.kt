package com.sleeplock.ui

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.view.ViewGroup

/**
 * 主界面 - 最简版本（纯 Android，不使用任何 Google 库）
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
        title.gravity = Gravity.CENTER
        layout.addView(title)
        
        // 副标题
        val subtitle = TextView(this)
        subtitle.text = "\n✅ 安装成功！"
        subtitle.textSize = 20f
        subtitle.setTextColor(Color.parseColor("#00C853"))
        subtitle.gravity = Gravity.CENTER
        layout.addView(subtitle)
        
        // 提示
        val hint = TextView(this)
        hint.text = "\nAPK 文件正常\n不使用 Google 库\n纯 AndroidX 开发"
        hint.textSize = 16f
        hint.setTextColor(Color.GRAY)
        hint.gravity = Gravity.CENTER
        layout.addView(hint)
        
        setContentView(layout)
    }
}

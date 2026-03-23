package com.sleeplock.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.sleeplock.R
import com.sleeplock.data.SleepLockDatabase
import com.sleeplock.data.entity.UserSettings
import com.sleeplock.service.LockService
import com.sleeplock.util.SchedulerManager
import com.sleeplock.util.HolidayManager
import kotlinx.coroutines.*

/**
 * 主界面 - 优化版（Material Design 风格）
 */
class MainActivity : Activity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_ADMIN = 1001
        private const val STATUS_TEXT_ID = 10001
    }
    
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var statusTextView: TextView
    private lateinit var adminButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity 已创建")
        
        setContentView(createLayout())
        
        // 初始化
        initializeApp()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADMIN) {
            // 延迟检查，给用户时间完成授权
            ioScope.launch {
                delay(500)
                withContext(Dispatchers.Main) {
                    checkAdminStatus()
                }
            }
        }
    }
    
    /**
     * 检查设备管理员状态
     */
    private fun checkAdminStatus() {
        if (LockService.isAdminActive(this)) {
            Log.d(TAG, "设备管理员权限已授予")
            updateStatusText("✅ 设备管理员已激活", ContextCompat.getColor(this, android.R.color.holo_green_dark))
            adminButton.isEnabled = false
            adminButton.text = "已激活"
            startButton.isEnabled = true
        } else {
            Log.w(TAG, "设备管理员权限被拒绝")
            updateStatusText("❌ 需要设备管理员权限", ContextCompat.getColor(this, android.R.color.holo_red_dark))
            adminButton.isEnabled = true
            adminButton.text = "激活设备管理员"
            startButton.isEnabled = false
        }
    }
    
    /**
     * 创建界面布局（Material Design 风格）
     */
    private fun createLayout(): ScrollView {
        val scrollView = ScrollView(this)
        scrollView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.background_light))
        scrollView.fillViewport = true
        
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER_HORIZONTAL
        layout.setPadding(60, 80, 60, 80)
        scrollView.addView(layout)
        
        // 应用图标（使用文字模拟）
        val iconView = TextView(this).apply {
            text = "🔒"
            textSize = 64f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        layout.addView(iconView)
        
        // 标题
        val title = TextView(this).apply {
            text = "专注锁机"
            textSize = 32f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        layout.addView(title)
        
        // 副标题
        val subtitle = TextView(this).apply {
            text = "建立规律作息，培养健康生活习惯"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        layout.addView(subtitle)
        
        // 状态卡片
        val statusCard = createCard(this)
        statusTextView = TextView(this).apply {
            id = STATUS_TEXT_ID
            textSize = 16f
            gravity = Gravity.CENTER
            text = "⏳ 检查权限中..."
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_orange_dark))
        }
        statusCard.addView(statusTextView)
        layout.addView(statusCard, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 30
        })
        
        // 设备管理员按钮（主要操作）
        adminButton = createPrimaryButton(this, "激活设备管理员") {
            Log.d(TAG, "点击设备管理员按钮")
            LockService.requestAdminPermission(this@MainActivity, REQUEST_ADMIN)
        }
        layout.addView(adminButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 15
        })
        
        // 开始锁机按钮
        startButton = createSecondaryButton(this, "▶️ 开始锁机") {
            startLockService()
        }
        startButton.isEnabled = false
        layout.addView(startButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 15
        })
        
        // 停止锁机按钮
        stopButton = createSecondaryButton(this, "⏹️ 停止锁机") {
            stopLockService()
        }
        layout.addView(stopButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 40
        })
        
        // 分隔线
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                bottomMargin = 30
            }
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
            alpha = 0.2f
        }
        layout.addView(divider)
        
        // 功能说明
        val featuresTitle = TextView(this).apply {
            text = "核心功能"
            textSize = 18f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }
        layout.addView(featuresTitle)
        
        val features = arrayOf(
            "⏰  定时锁屏 (23:40)",
            "🌅  定时解锁 (04:00)",
            "📵  应用白名单",
            "😴  睡眠监测",
            "📅  节假日自动调整",
            "💰  临时解锁额度系统"
        )
        
        for (feature in features) {
            val featureText = TextView(this).apply {
                text = feature
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(20, 12, 20, 12)
            }
            layout.addView(featureText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 2
            })
        }
        
        // 版本信息
        val versionText = TextView(this).apply {
            text = "\n版本 v0.1.0 | 目标设备：Redmi K70 Pro"
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 20)
            alpha = 0.6f
        }
        layout.addView(versionText)
        
        return scrollView
    }
    
    /**
     * 创建卡片容器
     */
    private fun createCard(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
            elevation = 8f
            setPadding(30, 25, 30, 25)
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }
    
    /**
     * 创建主要按钮（蓝色）
     */
    private fun createPrimaryButton(context: Context, text: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            this.text = text
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_blue_dark))
            gravity = Gravity.CENTER
            setPadding(30, 20, 30, 20)
            elevation = 4f
            setOnClickListener { onClick() }
        }
    }
    
    /**
     * 创建次要按钮（灰色）
     */
    private fun createSecondaryButton(context: Context, text: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            this.text = text
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            gravity = Gravity.CENTER
            setPadding(30, 20, 30, 20)
            elevation = 2f
            setOnClickListener { onClick() }
        }
    }
    
    /**
     * 初始化应用
     */
    private fun initializeApp() {
        ioScope.launch {
            try {
                val db = SleepLockDatabase.getDatabase(this@MainActivity)
                
                // 初始化用户设置
                var settings = db.userSettingsDao().getSettings()
                if (settings == null) {
                    settings = UserSettings()
                    db.userSettingsDao().insert(settings)
                    Log.d(TAG, "已创建默认用户设置")
                }
                
                // 初始化节假日数据
                val holidayManager = HolidayManager(this@MainActivity)
                holidayManager.initializeHolidays()
                
                withContext(Dispatchers.Main) {
                    checkAdminStatus()
                }
                
                Log.d(TAG, "应用初始化完成")
            } catch (e: Exception) {
                Log.e(TAG, "初始化失败", e)
                updateStatusText("❌ 初始化失败", ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
            }
        }
    }
    
    /**
     * 更新状态文本
     */
    private fun updateStatusText(status: String, color: Int = ContextCompat.getColor(this, android.R.color.darker_gray)) {
        runOnUiThread {
            statusTextView.text = status
            statusTextView.setTextColor(color)
        }
    }
    
    /**
     * 开始锁机服务
     */
    private fun startLockService() {
        if (!LockService.isAdminActive(this)) {
            Toast.makeText(this, "请先激活设备管理员", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 设置定时任务
        val schedulerManager = SchedulerManager(this)
        schedulerManager.scheduleAllTasks()
        
        updateStatusText("🔒 锁机服务已启动", ContextCompat.getColor(this, android.R.color.holo_green_dark))
        Toast.makeText(this, "锁机服务已启动", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "锁机服务已启动")
    }
    
    /**
     * 停止锁机服务
     */
    private fun stopLockService() {
        val schedulerManager = SchedulerManager(this)
        schedulerManager.cancelAllTasks()
        
        updateStatusText("✅ 设备管理员已激活", ContextCompat.getColor(this, android.R.color.holo_green_dark))
        Toast.makeText(this, "锁机服务已停止", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "锁机服务已停止")
    }
}

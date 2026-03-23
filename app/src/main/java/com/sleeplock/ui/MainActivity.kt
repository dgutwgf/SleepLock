package com.sleeplock.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import com.sleeplock.R
import com.sleeplock.data.SleepLockDatabase
import com.sleeplock.data.entity.UserSettings
import com.sleeplock.service.LockService
import com.sleeplock.util.SchedulerManager
import com.sleeplock.util.HolidayManager
import kotlinx.coroutines.*

/**
 * 主界面 - 核心功能版本（v0.1.0）
 */
class MainActivity : Activity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_ADMIN = 1001
    }
    
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
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
            if (LockService.isAdminActive(this)) {
                Log.d(TAG, "设备管理员权限已授予")
                updateStatusText("✅ 设备管理员已激活")
            } else {
                Log.w(TAG, "设备管理员权限被拒绝")
                updateStatusText("❌ 需要设备管理员权限")
            }
        }
    }
    
    /**
     * 创建界面布局
     */
    private fun createLayout(): LinearLayout {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(50, 50, 50, 50)
        layout.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white))
        
        // 标题
        val title = TextView(this).apply {
            text = "专注锁机 v0.1.0"
            textSize = 24f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }
        layout.addView(title)
        
        // 状态文本
        val statusText = TextView(this).apply {
            id = View.generateViewId()
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        layout.addView(statusText)
        
        // 设备管理员按钮
        val adminButton = Button(this).apply {
            text = "激活设备管理员"
            setOnClickListener {
                LockService.requestAdminPermission(this@MainActivity)
            }
        }
        layout.addView(adminButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 20
            bottomMargin = 10
        })
        
        // 开始锁机按钮
        val startButton = Button(this).apply {
            text = "开始锁机"
            setOnClickListener {
                startLockService()
            }
        }
        layout.addView(startButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 10
            bottomMargin = 10
        })
        
        // 停止锁机按钮
        val stopButton = Button(this).apply {
            text = "停止锁机"
            setOnClickListener {
                stopLockService()
            }
        }
        layout.addView(stopButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 10
            bottomMargin = 10
        })
        
        // 信息文本
        val infoText = TextView(this).apply {
            text = """
                核心功能:
                • 定时锁屏 (23:40)
                • 定时解锁 (04:00)
                • 应用白名单
                • 睡眠监测
            """.trimIndent()
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 0)
        }
        layout.addView(infoText)
        
        return layout
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
                    updateStatusText(if (LockService.isAdminActive(this@MainActivity)) {
                        "✅ 设备管理员已激活"
                    } else {
                        "⚠️ 需要设备管理员权限"
                    })
                }
                
                Log.d(TAG, "应用初始化完成")
            } catch (e: Exception) {
                Log.e(TAG, "初始化失败", e)
            }
        }
    }
    
    /**
     * 更新状态文本
     */
    private fun updateStatusText(status: String) {
        val statusText = findViewById<TextView>(View.generateViewId())
        // 简单处理：实际应该通过 ID 查找
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
        
        Toast.makeText(this, "锁机服务已启动", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "锁机服务已启动")
    }
    
    /**
     * 停止锁机服务
     */
    private fun stopLockService() {
        val schedulerManager = SchedulerManager(this)
        schedulerManager.cancelAllTasks()
        
        Toast.makeText(this, "锁机服务已停止", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "锁机服务已停止")
    }
}

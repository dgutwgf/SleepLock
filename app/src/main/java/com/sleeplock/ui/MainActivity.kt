package com.sleeplock.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import com.sleeplock.util.HolidayManager
import com.sleeplock.util.PermissionChecker
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
    
    @Volatile
    private var isLockServiceRunning: Boolean = false
    
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
            startButton.alpha = 1.0f
            stopButton.isEnabled = false
            stopButton.alpha = 0.5f
            isLockServiceRunning = false
            
            // 恢复之前的锁机状态
            restoreLockState()
        } else {
            Log.w(TAG, "设备管理员权限被拒绝")
            updateStatusText("❌ 需要设备管理员权限", ContextCompat.getColor(this, android.R.color.holo_red_dark))
            adminButton.isEnabled = true
            adminButton.text = "激活设备管理员"
            startButton.isEnabled = false
            startButton.alpha = 0.5f
            stopButton.isEnabled = false
            stopButton.alpha = 0.5f
        }
    }
    
    /**
     * 创建界面布局（Material Design 风格）
     */
    private fun createLayout(): ScrollView {
        val scrollView = ScrollView(this)
        scrollView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.background_light))
        scrollView.setFillViewport(true)
        
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
            bottomMargin = 15
        })
        
        // 权限检查按钮
        val permissionCheckButton = createSecondaryButton(this, "📋 检查权限状态") {
            checkAllPermissions()
        }
        layout.addView(permissionCheckButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 15
        })
        
        // 测试锁屏按钮
        val testLockButton = createSecondaryButton(this, "🔧 测试锁屏") {
            testLockScreen()
        }
        layout.addView(testLockButton, LinearLayout.LayoutParams(
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
        
        // 设置按钮
        val settingsButton = Button(this).apply {
            text = "⚙️ 设置"
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
            gravity = Gravity.CENTER
            setPadding(30, 15, 30, 15)
            elevation = 2f
            setOnClickListener {
                val intent = Intent(this@MainActivity, SettingsActivity::class.java)
                startActivity(intent)
            }
        }
        layout.addView(settingsButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 30
            bottomMargin = 20
        })
        
        // 版本信息
        val versionText = TextView(this).apply {
            text = "版本 v0.2.4 | 目标设备：Redmi K70 Pro"
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
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
        
        // 防止重复点击
        if (isLockServiceRunning) {
            Toast.makeText(this, "锁机服务已在运行中", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 保存锁机状态和开始时间
        getSharedPreferences("SleepLock", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_lock_active", true)
            .putBoolean("test_mode", true)  // 测试模式：2分钟
            .putLong("lock_start_time", System.currentTimeMillis())
            .apply()
        
        Toast.makeText(this, "测试模式：锁屏持续2分钟", Toast.LENGTH_SHORT).show()
        
        ioScope.launch {
            try {
                // 设置定时任务
                val schedulerManager = SchedulerManager(this@MainActivity)
                schedulerManager.scheduleAllTasks()
                
                withContext(Dispatchers.Main) {
                    // 立即锁定屏幕
                    val success = LockService.lockScreen(this@MainActivity)
                    
                    isLockServiceRunning = true
                    
                    if (success) {
                        updateStatusText("🔒 锁机中（测试2分钟）", ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
                    } else {
                        updateStatusText("⚠️ 锁机服务启动中", ContextCompat.getColor(this@MainActivity, android.R.color.holo_orange_dark))
                        Toast.makeText(this@MainActivity, "锁机服务已启动，请手动锁屏确认", Toast.LENGTH_LONG).show()
                    }
                    
                    startButton.isEnabled = false
                    startButton.alpha = 0.5f
                    stopButton.isEnabled = true
                    stopButton.alpha = 1.0f
                }
                
                // 2 分钟后自动停止测试模式
                delay(2 * 60 * 1000)
                
                // 真正清除测试模式标志
                getSharedPreferences("SleepLock", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("is_lock_active", false)
                    .putBoolean("test_mode", false)
                    .apply()
                
                withContext(Dispatchers.Main) {
                    if (isLockServiceRunning) {
                        Toast.makeText(this@MainActivity, "2 分钟测试结束，已自动停止锁机", Toast.LENGTH_SHORT).show()
                        isLockServiceRunning = false
                        updateStatusText("✅ 锁机服务已停止", ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_dark))
                        startButton.isEnabled = true
                        startButton.alpha = 1.0f
                        stopButton.isEnabled = false
                        stopButton.alpha = 0.5f
                    }
                }
                
                
                Log.d(TAG, "锁机服务已启动")
            } catch (e: Exception) {
                Log.e(TAG, "启动锁机服务失败", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "启动失败：${e.message}", Toast.LENGTH_LONG).show()
                    // 恢复状态
                    getSharedPreferences("SleepLock", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("is_lock_active", false)
                        .putBoolean("test_mode", false)
                        .apply()
                }
            }
        }
    }
    
    /**
     * 检查所有权限状态
     */
    private fun checkAllPermissions() {
        Log.d(TAG, "=== 开始检查权限 ===")
        
        val report = PermissionChecker.generateReport(this)
        Log.d(TAG, report)
        
        // 显示权限报告对话框
        val builder = AlertDialog.Builder(this)
        builder.setTitle("📋 权限检查报告")
        builder.setMessage(report)
        builder.setPositiveButton("确定", null)
        
        // 添加快速修复按钮
        builder.setNeutralButton("一键修复") { _, _ ->
            fixMissingPermissions()
        }
        
        builder.show()
    }
    
    /**
     * 修复缺失的权限
     */
    private fun fixMissingPermissions() {
        val permissions = PermissionChecker.checkAllPermissions(this)
        val missingRequired = permissions.filter { !it.granted && it.required }
        
        if (missingRequired.isEmpty()) {
            Toast.makeText(this, "✅ 所有必需权限已授予", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 优先处理设备管理员权限
        val deviceAdmin = missingRequired.find { it.name == "设备管理员权限" }
        if (deviceAdmin != null) {
            Toast.makeText(this, "请先授予设备管理员权限", Toast.LENGTH_LONG).show()
            LockService.requestAdminPermission(this, REQUEST_ADMIN)
            return
        }
        
        // 其他权限依次打开设置页面
        val firstMissing = missingRequired.first()
        Toast.makeText(this, "请手动授予：${firstMissing.name}", Toast.LENGTH_LONG).show()
        
        try {
            val intent = Intent(firstMissing.fixAction ?: Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.fromParts("package", packageName, null)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开设置页面失败", e)
            Toast.makeText(this, "无法打开设置页面，请手动到设置中查找", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 测试锁屏功能
     */
    private fun testLockScreen() {
        Log.d(TAG, "=== 开始测试锁屏功能 ===")
        
        // 检查权限
        val hasAdmin = LockService.isAdminActive(this)
        Log.d(TAG, "设备管理员权限: $hasAdmin")
        
        if (!hasAdmin) {
            Toast.makeText(this, "请先激活设备管理员权限", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 设置测试模式并立即锁屏
        getSharedPreferences("SleepLock", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_lock_active", true)
            .putBoolean("test_mode", true)
            .putLong("lock_start_time", System.currentTimeMillis())
            .apply()
        
        Toast.makeText(this, "测试模式启动：持续锁屏2分钟", Toast.LENGTH_SHORT).show()
        
        // 立即锁屏
        LockService.lockScreen(this)
        
        // 更新UI状态
        isLockServiceRunning = true
        updateStatusText("🔒 锁机中（测试2分钟）", android.graphics.Color.parseColor("#4CAF50"))
        startButton.isEnabled = false
        startButton.alpha = 0.5f
        stopButton.isEnabled = true
        stopButton.alpha = 1.0f
    }
    
    /**
     * 停止锁机服务
     */
    private fun stopLockService() {
        if (!isLockServiceRunning) {
            // 检查 SharedPreferences 中的状态
            val savedState = getSharedPreferences("SleepLock", Context.MODE_PRIVATE)
                .getBoolean("is_lock_active", false)
            if (!savedState) {
                Toast.makeText(this, "锁机服务未运行", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        // 检查是否允许关闭锁机服务
        val prefs = getSharedPreferences("SleepLock", Context.MODE_PRIVATE)
        val isTestMode = prefs.getBoolean("test_mode", false)
        val isInLockPeriod = checkIfInLockPeriod()
        
        // 非测试模式下，在锁机时段内禁止关闭锁机服务
        if (!isTestMode && isInLockPeriod) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ 无法停止")
                .setMessage("锁机时段内无法停止锁机服务，请在解锁时段停止。")
                .setPositiveButton("知道了", null)
                .show()
            Log.w(TAG, "锁机时段内尝试停止锁机服务被阻止")
            return
        }
        
        val schedulerManager = SchedulerManager(this)
        schedulerManager.cancelAllTasks()
        
        isLockServiceRunning = false
        
        // 清除保存的状态
        getSharedPreferences("SleepLock", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_lock_active", false)
            .putBoolean("test_mode", false)
            .putLong("lock_start_time", 0)
            .apply()
        
        updateStatusText("✅ 锁机服务已停止", ContextCompat.getColor(this, android.R.color.holo_blue_dark))
        startButton.isEnabled = true
        startButton.alpha = 1.0f
        stopButton.isEnabled = false
        stopButton.alpha = 0.5f
        Toast.makeText(this, "锁机服务已停止", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "锁机服务已停止")
    }
    
    /**
     * 检查当前是否在锁机时段
     */
    private fun checkIfInLockPeriod(): Boolean {
        return try {
            val db = SleepLockDatabase.getDatabase(this)
            val settings = runBlocking { db.userSettingsDao().getSettings() } ?: return false
            
            val currentTime = Calendar.getInstance()
            val lockTime = settings.lockTime.split(":")
            val unlockTime = settings.unlockTime.split(":")
            
            val currentMinutes = currentTime.get(Calendar.HOUR_OF_DAY) * 60 + currentTime.get(Calendar.MINUTE)
            val lockMinutes = lockTime[0].toInt() * 60 + lockTime[1].toInt()
            val unlockMinutes = unlockTime[0].toInt() * 60 + unlockTime[1].toInt()
            
            // 处理跨夜情况
            if (lockMinutes > unlockMinutes) {
                currentMinutes >= lockMinutes || currentMinutes < unlockMinutes
            } else {
                currentMinutes >= lockMinutes && currentMinutes < unlockMinutes
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查锁机时段失败", e)
            false
        }
    }
    
    /**
     * 恢复锁机状态
     */
    private fun restoreLockState() {
        val isActive = getSharedPreferences("SleepLock", Context.MODE_PRIVATE)
            .getBoolean("is_lock_active", false)
        
        if (isActive) {
            isLockServiceRunning = true
            updateStatusText("🔒 锁机服务运行中", ContextCompat.getColor(this, android.R.color.holo_green_dark))
            startButton.isEnabled = false
            startButton.alpha = 0.5f
            stopButton.isEnabled = true
            stopButton.alpha = 1.0f
        } else {
            isLockServiceRunning = false
        }
    }
}

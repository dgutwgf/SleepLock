package com.sleeplock.ui

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import com.sleeplock.data.SleepLockDatabase
import com.sleeplock.data.entity.UserSettings
import com.sleeplock.util.ReminderScheduler
import com.sleeplock.util.SchedulerManager
import kotlinx.coroutines.*

/**
 * 设置界面
 */
class SettingsActivity : Activity() {
    
    companion object {
        private const val TAG = "SettingsActivity"
    }
    
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private lateinit var lockTimeButton: Button
    private lateinit var unlockTimeButton: Button
    private lateinit var reminderSwitch: Switch
    private lateinit var reminderTimeButton: Button
    private lateinit var holidaySwitch: Switch
    private lateinit var emergencySwitch: Switch
    private lateinit var saveButton: Button
    
    private var settings: UserSettings? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "SettingsActivity 已创建")
        
        setContentView(createLayout())
        loadSettings()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
    }
    
    /**
     * 创建界面布局
     */
    private fun createLayout(): ScrollView {
        val scrollView = ScrollView(this)
        scrollView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.background_light))
        scrollView.isFillViewport = true
        
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER_HORIZONTAL
        layout.setPadding(40, 60, 40, 60)
        scrollView.addView(layout)
        
        // 标题
        val title = TextView(this).apply {
            text = "⚙️ 设置"
            textSize = 28f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.black))
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 40)
        }
        layout.addView(title)
        
        // 锁屏时间设置
        layout.addView(createSectionTitle("锁屏时间"))
        lockTimeButton = createTimeButton("23:40") { showTimePicker(true) }
        layout.addView(lockTimeButton, createFullWidthParams())
        
        // 解锁时间设置
        layout.addView(createSectionTitle("解锁时间"))
        unlockTimeButton = createTimeButton("04:00") { showTimePicker(false) }
        layout.addView(unlockTimeButton, createFullWidthParams())
        
        // 分隔线
        layout.addView(createDivider())
        
        // 睡前提醒开关
        layout.addView(createSectionTitle("睡前提醒"))
        val reminderLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        reminderSwitch = Switch(this).apply {
            isChecked = true
        }
        reminderLayout.addView(reminderSwitch)
        reminderLayout.addView(TextView(this).apply {
            text = "启用睡前提醒（锁屏前 15 分钟）"
            textSize = 16f
            setPadding(20, 0, 0, 0)
        })
        layout.addView(reminderLayout, createFullWidthParams())
        
        // 提醒时间
        layout.addView(createSectionTitle("提醒时间"))
        reminderTimeButton = createTimeButton("23:25") { showTimePicker(true, true) }
        layout.addView(reminderTimeButton, createFullWidthParams())
        
        // 分隔线
        layout.addView(createDivider())
        
        // 节假日调整开关
        layout.addView(createSectionTitle("智能调整"))
        val holidayLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        holidaySwitch = Switch(this).apply {
            isChecked = true
        }
        holidayLayout.addView(holidaySwitch)
        holidayLayout.addView(TextView(this).apply {
            text = "节假日/周末自动推迟锁屏"
            textSize = 16f
            setPadding(20, 0, 0, 0)
        })
        layout.addView(holidayLayout, createFullWidthParams())
        
        // 分隔线
        layout.addView(createDivider())
        
        // 紧急解锁开关
        val emergencyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        emergencySwitch = Switch(this).apply {
            isChecked = true
        }
        emergencyLayout.addView(emergencySwitch)
        emergencyLayout.addView(TextView(this).apply {
            text = "启用连续重启解锁功能"
            textSize = 16f
            setPadding(20, 0, 0, 0)
        })
        layout.addView(emergencyLayout, createFullWidthParams())
        
        // 额度显示
        layout.addView(createSectionTitle("💰 解锁额度"))
        val creditBalanceText = TextView(this).apply {
            id = View.generateViewId()
            text = "查询中..."
            textSize = 24f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.holo_blue_dark))
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 10, 0, 10)
        }
        layout.addView(creditBalanceText, createFullWidthParams())
        
        // 查询并显示额度
        ioScope.launch {
            try {
                val creditManager = com.sleeplock.util.UnlockCreditManager(this@SettingsActivity)
                val balance = creditManager.getCurrentBalance()
                withContext(Dispatchers.Main) {
                    creditBalanceText.text = "$balance 分钟"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    creditBalanceText.text = "0 分钟"
                }
            }
        }
        
        // 说明文字
        layout.addView(TextView(this).apply {
            text = """💡 连续重启 3 次可临时解锁 15 分钟（消耗所有累计额度）
💤 提前 30 分钟睡觉 = +15 分钟额度
⏰ 每天最多解锁 2 次
📅 额度 7 天过期""".trimIndent()
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.darker_gray))
            setPadding(0, 20, 0, 0)
        })
        
        // 保存按钮
        saveButton = Button(this).apply {
            text = "💾 保存设置"
            textSize = 18f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.white))
            setBackgroundColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.holo_blue_dark))
            gravity = Gravity.CENTER
            setPadding(30, 25, 30, 25)
            elevation = 4f
            setOnClickListener { saveSettings() }
        }
        layout.addView(saveButton, createFullWidthParams().apply {
            topMargin = 50
        })
        
        // 分隔线
        layout.addView(createDivider())
        
        // 日志查看按钮
        val logButton = Button(this).apply {
            text = "📋 查看执行日志"
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.white))
            setBackgroundColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.darker_gray))
            gravity = Gravity.CENTER
            setPadding(30, 20, 30, 20)
            elevation = 2f
            setOnClickListener {
                val intent = Intent(this@SettingsActivity, LogViewerActivity::class.java)
                startActivity(intent)
            }
        }
        layout.addView(logButton, createFullWidthParams())
        
        // 日志说明
        layout.addView(TextView(this).apply {
            text = "💡 查看应用运行日志，包括服务状态、应用拦截、屏幕状态等"
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.darker_gray))
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 20)
        })
        
        return scrollView
    }
    
    /**
     * 创建章节标题
     */
    private fun createSectionTitle(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.darker_gray))
            setPadding(0, 25, 0, 15)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }
    
    /**
     * 创建时间选择按钮
     */
    private fun createTimeButton(time: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = time
            textSize = 20f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.black))
            setBackgroundColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.white))
            gravity = Gravity.CENTER
            setPadding(30, 20, 30, 20)
            elevation = 2f
            setOnClickListener { onClick() }
        }
    }
    
    /**
     * 创建分隔线
     */
    private fun createDivider(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                topMargin = 25
                bottomMargin = 25
            }
            setBackgroundColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.darker_gray))
            alpha = 0.2f
        }
    }
    
    /**
     * 创建全宽布局参数
     */
    private fun createFullWidthParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 15
        }
    }
    
    /**
     * 加载设置
     */
    private fun loadSettings() {
        ioScope.launch {
            try {
                val db = SleepLockDatabase.getDatabase(this@SettingsActivity)
                settings = db.userSettingsDao().getSettings()
                
                val currentSettings = settings ?: UserSettings()
                
                withContext(Dispatchers.Main) {
                    lockTimeButton.text = currentSettings.lockTime
                    unlockTimeButton.text = currentSettings.unlockTime
                    reminderSwitch.isChecked = currentSettings.reminderEnabled
                    reminderTimeButton.text = currentSettings.reminderTime
                    holidaySwitch.isChecked = currentSettings.holidayAdjustEnabled
                    emergencySwitch.isChecked = currentSettings.emergencyUnlockEnabled
                }
                
                Log.d(TAG, "设置已加载")
            } catch (e: Exception) {
                Log.e(TAG, "加载设置失败", e)
            }
        }
    }
    
    /**
     * 显示时间选择器
     */
    private fun showTimePicker(isLockTime: Boolean, isReminderTime: Boolean = false) {
        val currentTime = if (isLockTime) {
            lockTimeButton.text.toString()
        } else if (isReminderTime) {
            reminderTimeButton.text.toString()
        } else {
            unlockTimeButton.text.toString()
        }
        
        val parts = currentTime.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        
        TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                val timeStr = String.format("%02d:%02d", selectedHour, selectedMinute)
                
                if (isLockTime) {
                    lockTimeButton.text = timeStr
                } else if (isReminderTime) {
                    reminderTimeButton.text = timeStr
                } else {
                    unlockTimeButton.text = timeStr
                }
            },
            hour,
            minute,
            true
        ).show()
    }
    
    /**
     * 保存设置
     */
    private fun saveSettings() {
        ioScope.launch {
            try {
                val db = SleepLockDatabase.getDatabase(this@SettingsActivity)
                
                val updatedSettings = settings?.copy(
                    lockTime = lockTimeButton.text.toString(),
                    unlockTime = unlockTimeButton.text.toString(),
                    reminderEnabled = reminderSwitch.isChecked,
                    reminderTime = reminderTimeButton.text.toString(),
                    holidayAdjustEnabled = holidaySwitch.isChecked,
                    emergencyUnlockEnabled = emergencySwitch.isChecked
                ) ?: UserSettings(
                    lockTime = lockTimeButton.text.toString(),
                    unlockTime = unlockTimeButton.text.toString(),
                    reminderEnabled = reminderSwitch.isChecked,
                    reminderTime = reminderTimeButton.text.toString(),
                    holidayAdjustEnabled = holidaySwitch.isChecked,
                    emergencyUnlockEnabled = emergencySwitch.isChecked
                )
                
                db.userSettingsDao().update(updatedSettings)
                
                // 重新设置定时任务
                withContext(Dispatchers.Main) {
                    val schedulerManager = SchedulerManager(this@SettingsActivity)
                    schedulerManager.scheduleAllTasks()
                    
                    // 设置睡前提醒
                    if (reminderSwitch.isChecked) {
                        val reminderScheduler = ReminderScheduler(this@SettingsActivity)
                        val reminderParts = reminderTimeButton.text.toString().split(":")
                        reminderScheduler.scheduleDailyReminder(
                            reminderParts[0].toInt(),
                            reminderParts[1].toInt()
                        )
                    }
                    
                    Toast.makeText(
                        this@SettingsActivity,
                        "✅ 设置已保存",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    finish()
                }
                
                Log.d(TAG, "设置已保存")
            } catch (e: Exception) {
                Log.e(TAG, "保存设置失败", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SettingsActivity,
                        "❌ 保存失败：${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}

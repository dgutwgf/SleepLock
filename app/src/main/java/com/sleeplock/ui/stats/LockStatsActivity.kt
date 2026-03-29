package com.sleeplock.ui.stats

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.sleeplock.data.SleepLockDatabase
import com.sleeplock.data.entity.DailyLockStats
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 锁机统计页面
 */
class LockStatsActivity : Activity() {
    
    companion object {
        private const val TAG = "LockStatsActivity"
    }
    
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var statsContainer: LinearLayout
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createUI())
        
        // 加载统计数据
        loadStats()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
    }
    
    /**
     * 创建 UI
     */
    private fun createUI(): ScrollView {
        val scrollView = ScrollView(this)
        scrollView.setBackgroundColor(Color.parseColor("#f5f5f5"))
        
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(30, 30, 30, 30)
        scrollView.addView(layout)
        
        // 标题
        layout.addView(TextView(this).apply {
            text = "📊 锁机统计"
            textSize = 26f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 30)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        
        // 统计卡片容器
        statsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        layout.addView(statsContainer)
        
        // 加载提示
        statsContainer.addView(TextView(this).apply {
            text = "加载中..."
            textSize = 16f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 50, 0, 50)
            id = View.generateViewId()
        })
        
        return scrollView
    }
    
    /**
     * 加载统计数据
     */
    private fun loadStats() {
        ioScope.launch {
            try {
                val db = SleepLockDatabase.getDatabase(this@LockStatsActivity)
                val stats = db.dailyLockStatsDao().getRecentStats(30)
                
                withContext(Dispatchers.Main) {
                    displayStats(stats)
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载统计数据失败", e)
                withContext(Dispatchers.Main) {
                    statsContainer.removeAllViews()
                    statsContainer.addView(TextView(this@LockStatsActivity).apply {
                        text = "加载失败：${e.message}"
                        textSize = 14f
                        setTextColor(Color.RED)
                        gravity = Gravity.CENTER
                        setPadding(0, 50, 0, 50)
                    })
                }
            }
        }
    }
    
    /**
     * 显示统计数据
     */
    private fun displayStats(stats: List<DailyLockStats>) {
        statsContainer.removeAllViews()
        
        if (stats.isEmpty()) {
            statsContainer.addView(TextView(this).apply {
                text = "暂无统计数据\n开始使用锁机功能后会自动记录"
                textSize = 15f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setPadding(0, 50, 0, 50)
            })
            return
        }
        
        // 显示最近 7 天统计
        statsContainer.addView(createSectionTitle("最近 7 天统计"))
        
        stats.take(7).forEach { stat ->
            statsContainer.addView(createStatCard(stat))
            statsContainer.addView(createDivider())
        }
        
        // 总计
        if (stats.size > 1) {
            val totalDays = stats.size
            val totalDuration = stats.sumOf { it.lockDuration }
            val totalIntercepts = stats.sumOf { it.interceptCount }
            
            statsContainer.addView(createSectionTitle("总计"))
            statsContainer.addView(createSummaryCard(totalDays, totalDuration, totalIntercepts))
        }
    }
    
    /**
     * 创建章节标题
     */
    private fun createSectionTitle(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 17f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 25, 0, 15)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }
    
    /**
     * 创建统计卡片
     */
    private fun createStatCard(stat: DailyLockStats): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(25, 20, 25, 20)
            elevation = 3f
            
            // 日期
            addView(TextView(this@LockStatsActivity).apply {
                text = formatDate(stat.date)
                textSize = 16f
                setTextColor(Color.BLACK)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 15)
            })
            
            // 锁机时间
            val lockStartStr = if (stat.lockStartTime > 0) {
                formatTime(stat.lockStartTime)
            } else {
                "未记录"
            }
            val unlockStr = if (stat.unlockTime > 0) {
                formatTime(stat.unlockTime)
            } else {
                "未记录"
            }
            
            addView(TextView(this@LockStatsActivity).apply {
                text = "⏰ 锁机时间：$lockStartStr - $unlockStr"
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
                setPadding(0, 5, 0, 10)
            })
            
            // 锁机时长
            val durationStr = formatDuration(stat.lockDuration)
            addView(TextView(this@LockStatsActivity).apply {
                text = "⏳ 锁机时长：$durationStr"
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
                setPadding(0, 5, 0, 10)
            })
            
            // 拦截次数
            addView(TextView(this@LockStatsActivity).apply {
                text = "🚫 拦截次数：${stat.interceptCount}"
                textSize = 14f
                setTextColor(Color.parseColor("#FF5722"))
                setPadding(0, 5, 0, 0)
            })
        }
    }
    
    /**
     * 创建汇总卡片
     */
    private fun createSummaryCard(days: Int, totalDuration: Long, totalIntercepts: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E3F2FD"))
            setPadding(25, 20, 25, 20)
            elevation = 3f
            
            addView(TextView(this@LockStatsActivity).apply {
                text = "📅 统计天数：$days 天"
                textSize = 15f
                setTextColor(Color.BLACK)
                setPadding(0, 5, 0, 10)
            })
            
            addView(TextView(this@LockStatsActivity).apply {
                text = "⏳ 总锁机时长：${formatDuration(totalDuration)}"
                textSize = 15f
                setTextColor(Color.BLACK)
                setPadding(0, 5, 0, 10)
            })
            
            addView(TextView(this@LockStatsActivity).apply {
                text = "🚫 总拦截次数：$totalIntercepts"
                textSize = 15f
                setTextColor(Color.parseColor("#FF5722"))
                setPadding(0, 5, 0, 0)
            })
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
                topMargin = 15
                bottomMargin = 15
            }
            setBackgroundColor(Color.parseColor("#e0e0e0"))
        }
    }
    
    /**
     * 格式化日期
     */
    private fun formatDate(dateStr: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("yyyy 年 MM 月 dd 日 EEE", Locale.CHINA)
            val date = inputFormat.parse(dateStr)
            outputFormat.format(date!!)
        } catch (e: Exception) {
            dateStr
        }
    }
    
    /**
     * 格式化时间
     */
    private fun formatTime(timestamp: Long): String {
        return try {
            val format = SimpleDateFormat("HH:mm", Locale.getDefault())
            format.format(Date(timestamp))
        } catch (e: Exception) {
            "--:--"
        }
    }
    
    /**
     * 格式化时长
     */
    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        
        return when {
            hours > 0 -> "$hours 小时 $minutes 分钟"
            minutes > 0 -> "$minutes 分钟"
            else -> "$seconds 秒"
        }
    }
}

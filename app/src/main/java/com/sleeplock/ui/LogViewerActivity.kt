package com.sleeplock.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.sleeplock.data.entity.ExecutionLog
import com.sleeplock.util.LogManager
import kotlinx.coroutines.*
import java.io.File

/**
 * 日志查看界面 - 显示执行日志
 */
class LogViewerActivity : Activity() {
    
    companion object {
        private const val TAG = "LogViewerActivity"
    }
    
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var logsListView: ListView
    private lateinit var emptyTextView: TextView
    private lateinit var progressBar: ProgressBar
    private var logsAdapter: LogAdapter? = null
    private var logsList: MutableList<ExecutionLog> = mutableListOf()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createLayout())
        loadLogs()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
    }
    
    /**
     * 创建界面布局
     */
    private fun createLayout(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@LogViewerActivity, android.R.color.background_light))
        }
        
        // 标题栏
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@LogViewerActivity, android.R.color.white))
            elevation = 4f
            setPadding(30, 30, 30, 30)
        }
        
        // 返回按钮
        val backButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_revert)
            setBackgroundColor(ContextCompat.getColor(this@LogViewerActivity, android.R.color.transparent))
            setOnClickListener { finish() }
        }
        headerLayout.addView(backButton, LinearLayout.LayoutParams(80, 80))
        
        // 标题
        val title = TextView(this).apply {
            text = "📋 执行日志"
            textSize = 20f
            setTextColor(ContextCompat.getColor(this@LogViewerActivity, android.R.color.black))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        headerLayout.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = 20
        })
        
        layout.addView(headerLayout, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        // 工具栏
        val toolbarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(ContextCompat.getColor(this@LogViewerActivity, android.R.color.white))
            setPadding(20, 15, 20, 15)
        }
        
        // 刷新按钮
        val refreshButton = Button(this).apply {
            text = "🔄 刷新"
            textSize = 14f
            setOnClickListener { loadLogs() }
        }
        toolbarLayout.addView(refreshButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            rightMargin = 10
        })
        
        // 导出按钮
        val exportButton = Button(this).apply {
            text = "📤 导出"
            textSize = 14f
            setOnClickListener { exportLogs() }
        }
        toolbarLayout.addView(exportButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            rightMargin = 10
        })
        
        // 清除按钮
        val clearButton = Button(this).apply {
            text = "🗑️ 清除"
            textSize = 14f
            setOnClickListener { clearLogs() }
        }
        toolbarLayout.addView(clearButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        layout.addView(toolbarLayout, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        
        // 进度条
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 50
                bottomMargin = 50
            }
        }
        layout.addView(progressBar)
        
        // 空视图
        emptyTextView = TextView(this).apply {
            visibility = View.GONE
            text = "📭 暂无日志记录"
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@LogViewerActivity, android.R.color.darker_gray))
            gravity = Gravity.CENTER
            setPadding(0, 100, 0, 100)
        }
        layout.addView(emptyTextView)
        
        // 日志列表
        logsListView = ListView(this).apply {
            divider = null
            dividerHeight = 0
            setBackgroundColor(ContextCompat.getColor(this@LogViewerActivity, android.R.color.background_light))
        }
        layout.addView(logsListView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ).apply {
            topMargin = 10
        })
        
        return layout
    }
    
    /**
     * 加载日志
     */
    private fun loadLogs() {
        progressBar.visibility = View.VISIBLE
        logsListView.visibility = View.GONE
        emptyTextView.visibility = View.GONE
        
        ioScope.launch {
            try {
                val logManager = LogManager.getInstance(this@LogViewerActivity)
                val logs = logManager.getRecentLogs(200)
                
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    
                    if (logs.isEmpty()) {
                        emptyTextView.visibility = View.VISIBLE
                    } else {
                        logsList.clear()
                        logsList.addAll(logs)
                        
                        if (logsAdapter == null) {
                            logsAdapter = LogAdapter(logsList)
                            logsListView.adapter = logsAdapter
                        } else {
                            logsAdapter!!.notifyDataSetChanged()
                        }
                        
                        logsListView.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@LogViewerActivity, "加载日志失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * 导出日志
     */
    private fun exportLogs() {
        ioScope.launch {
            try {
                val logManager = LogManager.getInstance(this@LogViewerActivity)
                val htmlContent = logManager.exportAsHtml()
                
                // 保存到文件
                val exportDir = File(getExternalFilesDir(null), "exports")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }
                
                val timestamp = System.currentTimeMillis()
                val exportFile = File(exportDir, "sleeplock_logs_$timestamp.html")
                exportFile.writeText(htmlContent, Charsets.UTF_8)
                
                // 分享文件
                withContext(Dispatchers.Main) {
                    try {
                        val uri = FileProvider.getUriForFile(
                            this@LogViewerActivity,
                            "${packageName}.fileprovider",
                            exportFile
                        )
                        
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/html"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        
                        startActivity(Intent.createChooser(shareIntent, "分享日志"))
                        Toast.makeText(this@LogViewerActivity, "✅ 日志已导出", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@LogViewerActivity, "分享失败：${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LogViewerActivity, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * 清除日志
     */
    private fun clearLogs() {
        AlertDialog.Builder(this)
            .setTitle("🗑️ 清除日志")
            .setMessage("确定要清除所有日志记录吗？此操作不可恢复。")
            .setPositiveButton("确定") { dialog, _ ->
                dialog.dismiss()
                ioScope.launch {
                    try {
                        val logManager = LogManager.getInstance(this@LogViewerActivity)
                        logManager.clearAllLogs()
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@LogViewerActivity, "✅ 日志已清除", Toast.LENGTH_SHORT).show()
                            loadLogs()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@LogViewerActivity, "清除失败：${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 日志适配器
     */
    inner class LogAdapter(private val logs: List<ExecutionLog>) : BaseAdapter() {
        
        override fun getCount(): Int = logs.size
        override fun getItem(position: Int): ExecutionLog = logs[position]
        override fun getItemId(position: Int): Long = position.toLong()
        
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: createLogView()
            val log = logs[position]
            
            view.findViewById<TextView>(R.id.log_time).text = log.getFormattedTime().split(" ")[1] // 只显示时间
            view.findViewById<TextView>(R.id.log_level).text = log.getLevelIcon()
            view.findViewById<TextView>(R.id.log_category).text = log.getCategoryIcon()
            view.findViewById<TextView>(R.id.log_tag).text = "[${log.tag}]"
            view.findViewById<TextView>(R.id.log_message).text = log.message
            
            if (log.extraData != null) {
                view.findViewById<TextView>(R.id.log_extra).apply {
                    visibility = View.VISIBLE
                    text = log.extraData
                }
            } else {
                view.findViewById<TextView>(R.id.log_extra).visibility = View.GONE
            }
            
            // 根据级别设置背景色
            val backgroundRes = when (log.level) {
                ExecutionLog.LogLevel.ERROR -> android.R.color.holo_red_light
                ExecutionLog.LogLevel.WARNING -> android.R.color.holo_orange_light
                else -> android.R.color.white
            }
            view.setBackgroundColor(ContextCompat.getColor(this@LogViewerActivity, backgroundRes))
            
            return view
        }
        
        private fun createLogView(): LinearLayout {
            return LinearLayout(this@LogViewerActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 15, 20, 15)
                
                // 第一行：时间 + 级别 + 分类 + 标签
                val topLayout = LinearLayout(this@LogViewerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                
                topLayout.addView(TextView(this@LogViewerActivity).apply {
                    id = R.id.log_time
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(this@LogViewerActivity, android.R.color.darker_gray))
                    width = 150
                })
                
                topLayout.addView(TextView(this@LogViewerActivity).apply {
                    id = R.id.log_level
                    textSize = 16f
                    setPadding(10, 0, 10, 0)
                })
                
                topLayout.addView(TextView(this@LogViewerActivity).apply {
                    id = R.id.log_category
                    textSize = 16f
                    setPadding(5, 0, 5, 0)
                })
                
                topLayout.addView(TextView(this@LogViewerActivity).apply {
                    id = R.id.log_tag
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(this@LogViewerActivity, android.R.color.holo_blue_dark))
                })
                
                addView(topLayout)
                
                // 第二行：消息
                addView(TextView(this@LogViewerActivity).apply {
                    id = R.id.log_message
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(this@LogViewerActivity, android.R.color.black))
                    setPadding(0, 5, 0, 5)
                })
                
                // 第三行：额外数据
                addView(TextView(this@LogViewerActivity).apply {
                    id = R.id.log_extra
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(this@LogViewerActivity, android.R.color.darker_gray))
                    setPadding(0, 3, 0, 0)
                    visibility = View.GONE
                })
            }
        }
    }
}

// 临时资源 ID
object R {
    object id {
        const val log_time = 10001
        const val log_level = 10002
        const val log_category = 10003
        const val log_tag = 10004
        const val log_message = 10005
        const val log_extra = 10006
    }
}

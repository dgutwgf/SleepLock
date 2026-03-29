package com.sleeplock.ui.settings

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.sleeplock.R
import com.sleeplock.data.SleepLockDatabase
import com.sleeplock.data.entity.AppBlacklistItem
import kotlinx.coroutines.*
import java.util.*

/**
 * 黑名单管理 Activity
 * 功能：
 * - 查看当前黑名单应用列表
 * - 从已安装应用中选择添加到黑名单
 * - 移除可选应用（视频/游戏类不可移除）
 */
class BlacklistManageActivity : Activity() {
    
    companion object {
        private const val TAG = "BlacklistManage"
        
        // 强制黑名单（不可移除）- 视频类
        val FORCE_BLACKLIST_VIDEO = setOf(
            "com.tencent.qqlive", "com.qiyi.video", "com.youku.phone",
            "tv.danmaku.bili", "com.google.android.youtube",
            "com.ss.android.ugc.aweme", "com.kuaishou.nebula"
        )
        
        // 强制黑名单（不可移除）- 游戏类
        val FORCE_BLACKLIST_GAME = setOf(
            "com.tencent.tmgp", "com.netease", "com.miHoYo",
            "com.tencent.ig", "com.tencent.tmgp.sgame",
            "com.mojang.minecraftpe", "com.roblox.client"
        )
        
        // 关键词黑名单（不可移除）
        val FORCE_KEYWORDS = listOf(
            "tmgp", "game", "gaming", "video", "movie", "aweme",
            "douyin", "kuaishou", "bilibili", "youtube", "qqlive", "aiqiyi"
        )
    }
    
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var appsListView: ListView
    private lateinit var loadingView: View
    private lateinit var installedApps: List<AppInfo>
    private lateinit var blacklistedPackages: MutableSet<String>
    private val adapter by lazy { AppAdapter(this) }
    
    data class AppInfo(
        val packageName: String,
        val appName: String,
        val isSystemApp: Boolean,
        val isBlacklisted: Boolean,
        val isForceBlacklist: Boolean,
        val icon: android.graphics.drawable.Drawable?
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "黑名单管理页面已创建")
        
        setContentView(createUI())
        
        // 加载数据
        loadData()
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
        layout.setPadding(40, 40, 40, 40)
        scrollView.addView(layout)
        
        // 标题
        layout.addView(TextView(this).apply {
            text = "黑名单应用管理"
            textSize = 24f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 10)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        
        // 说明
        layout.addView(TextView(this).apply {
            text = "⚠️ 视频类和游戏类应用为强制黑名单，不可移除\n其他应用可自定义添加或移除"
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 20)
        })
        
        // 添加按钮
        layout.addView(Button(this).apply {
            text = "+ 添加应用到黑名单"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            setPadding(30, 20, 30, 20)
            setOnClickListener {
                showAddAppDialog()
            }
        })
        
        // 加载提示
        loadingView = TextView(this).apply {
            text = "加载中..."
            textSize = 16f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 40)
        }
        layout.addView(loadingView)
        
        // 应用列表
        appsListView = ListView(this).apply {
            adapter = this@BlacklistManageActivity.adapter
            divider = ColorDrawable(Color.parseColor("#e0e0e0"))
            dividerHeight = 1
            visibility = View.GONE
        }
        layout.addView(appsListView)
        
        return scrollView
    }
    
    /**
     * 加载数据
     */
    private fun loadData() {
        ioScope.launch {
            try {
                // 获取已安装应用
                val pm = packageManager
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { !it.packageName.startsWith("com.android.") || 
                              it.packageName in listOf("com.android.chrome", "com.android.settings") }
                    .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
                
                // 获取当前黑名单
                val db = SleepLockDatabase.getDatabase(this@BlacklistManageActivity)
                val blacklistItems = db.appBlacklistDao().getAll()
                blacklistedPackages = blacklistItems.map { it.packageName }.toMutableSet()
                
                // 合并系统黑名单和用户黑名单
                val allBlacklistPackages = buildSet {
                    addAll(blacklistedPackages)
                    // 添加关键词匹配的应用
                    apps.forEach { app ->
                        if (isForceBlacklistByKeyword(app.packageName)) {
                            add(app.packageName)
                        }
                    }
                }
                
                // 构建应用列表
                installedApps = apps.map { app ->
                    val isForceBlacklist = isForceBlacklist(app.packageName)
                    AppInfo(
                        packageName = app.packageName,
                        appName = pm.getApplicationLabel(app).toString(),
                        isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        isBlacklisted = app.packageName in allBlacklistPackages,
                        isForceBlacklist = isForceBlacklist,
                        icon = try { pm.getApplicationIcon(app) } catch (e: Exception) { null }
                    )
                }.filter { !it.packageName.startsWith("com.sleeplock") } // 排除本应用
                
                withContext(Dispatchers.Main) {
                    loadingView.visibility = View.GONE
                    appsListView.visibility = View.VISIBLE
                    adapter.notifyDataSetChanged()
                    
                    val count = installedApps.count { it.isBlacklisted }
                    Toast.makeText(
                        this@BlacklistManageActivity,
                        "已加载 ${installedApps.size} 个应用，黑名单中 $count 个",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载应用列表失败", e)
                withContext(Dispatchers.Main) {
                    loadingView.visibility = View.GONE
                    Toast.makeText(
                        this@BlacklistManageActivity,
                        "加载失败：${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * 判断是否为强制黑名单（不可移除）
     */
    private fun isForceBlacklist(packageName: String): Boolean {
        return packageName in FORCE_BLACKLIST_VIDEO ||
               packageName in FORCE_BLACKLIST_GAME ||
               FORCE_BLACKLIST_GAME.any { packageName.startsWith(it) }
    }
    
    /**
     * 判断是否通过关键词匹配为黑名单
     */
    private fun isForceBlacklistByKeyword(packageName: String): Boolean {
        val lowerName = packageName.lowercase()
        return FORCE_KEYWORDS.any { it in lowerName }
    }
    
    /**
     * 显示添加应用对话框
     */
    private fun showAddAppDialog() {
        val nonBlacklistedApps = installedApps.filter { !it.isBlacklisted }
            .sortedBy { it.appName.lowercase() }
        
        if (nonBlacklistedApps.isEmpty()) {
            Toast.makeText(this, "所有应用已在黑名单中", Toast.LENGTH_SHORT).show()
            return
        }
        
        val builder = AlertDialog.Builder(this)
        builder.setTitle("选择要添加到黑名单的应用")
        
        val appNames = nonBlacklistedApps.map { "${it.appName} (${it.packageName})" }.toTypedArray()
        
        builder.setItems(appNames) { _, which ->
            val selectedApp = nonBlacklistedApps[which]
            addToBlacklist(selectedApp.packageName, selectedApp.appName)
        }
        
        builder.setNegativeButton("取消", null)
        builder.show()
    }
    
    /**
     * 添加到黑名单
     */
    private fun addToBlacklist(packageName: String, appName: String) {
        ioScope.launch {
            try {
                val db = SleepLockDatabase.getDatabase(this@BlacklistManageActivity)
                val item = AppBlacklistItem(
                    packageName = packageName,
                    appName = appName,
                    addedTime = System.currentTimeMillis(),
                    isCustom = true
                )
                db.appBlacklistDao().insert(item)
                blacklistedPackages.add(packageName)
                
                withContext(Dispatchers.Main) {
                    // 刷新列表
                    installedApps = installedApps.map {
                        if (it.packageName == packageName) {
                            it.copy(isBlacklisted = true)
                        } else {
                            it
                        }
                    }
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this@BlacklistManageActivity, "已添加：$appName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "添加到黑名单失败", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BlacklistManageActivity, "添加失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * 从黑名单移除
     */
    private fun removeFromBlacklist(packageName: String, appName: String, isForce: Boolean) {
        if (isForce) {
            Toast.makeText(this, "视频类和游戏类应用不可移除", Toast.LENGTH_LONG).show()
            return
        }
        
        ioScope.launch {
            try {
                val db = SleepLockDatabase.getDatabase(this@BlacklistManageActivity)
                db.appBlacklistDao().deleteByPackageName(packageName)
                blacklistedPackages.remove(packageName)
                
                withContext(Dispatchers.Main) {
                    // 刷新列表
                    installedApps = installedApps.map {
                        if (it.packageName == packageName) {
                            it.copy(isBlacklisted = false)
                        } else {
                            it
                        }
                    }
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this@BlacklistManageActivity, "已移除：$appName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "从黑名单移除失败", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BlacklistManageActivity, "移除失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * 应用列表适配器
     */
    inner class AppAdapter(context: Context) : BaseAdapter() {
        private val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as android.view.LayoutInflater
        
        override fun getCount(): Int = installedApps.size
        
        override fun getItem(position: Int): Any = installedApps[position]
        
        override fun getItemId(position: Int): Long = position.toLong()
        
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: inflater.inflate(android.R.layout.simple_list_item_2, parent, false)
            val app = installedApps[position]
            
            view.findViewById<TextView>(android.R.id.text1).apply {
                text = app.appName
                textSize = 16f
                setTextColor(if (app.isBlacklisted) Color.parseColor("#FF4444") else Color.BLACK)
            }
            
            view.findViewById<TextView>(android.R.id.text2).apply {
                text = buildString {
                    append(app.packageName)
                    if (app.isForceBlacklist) {
                        append("  ⚠️ 强制黑名单")
                    } else if (app.isBlacklisted) {
                        append("  ✅ 黑名单中")
                    }
                }
                textSize = 12f
                setTextColor(Color.GRAY)
            }
            
            view.setOnClickListener {
                if (app.isBlacklisted) {
                    showRemoveDialog(app)
                } else {
                    addToBlacklist(app.packageName, app.appName)
                }
            }
            
            return view
        }
    }
    
    /**
     * 显示移除对话框
     */
    private fun showRemoveDialog(app: AppInfo) {
        if (app.isForceBlacklist) {
            AlertDialog.Builder(this)
                .setTitle("无法移除")
                .setMessage("${app.appName} 是视频/游戏类应用，属于强制黑名单，不可移除。")
                .setPositiveButton("知道了", null)
                .show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("移除黑名单")
            .setMessage("确定要从黑名单中移除 \"${app.appName}\" 吗？")
            .setPositiveButton("移除") { _, _ ->
                removeFromBlacklist(app.packageName, app.appName, app.isForceBlacklist)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

package com.sleeplock.ui.settings

import android.app.Activity
import android.app.AlertDialog
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
            "com.tencent.qqlive",      // 腾讯视频
            "com.qiyi.video",          // 爱奇艺
            "com.youku.phone",         // 优酷
            "tv.danmaku.bili",         // 哔哩哔哩
            "tv.danmaku.bili",         // 哔哩哔哩
            "com.google.android.youtube", // YouTube
            "com.ss.android.ugc.aweme",   // 抖音
            "com.ss.android.ugc.aweme.lite", // 抖音极速版
            "com.kuaishou.nebula",     // 快手
            "com.kuaishou.nebula",     // 快手
            "com.xunmeng.pinduoduo"    // 拼多多
        )
        
        // 强制黑名单（不可移除）- 游戏类
        val FORCE_BLACKLIST_GAME = setOf(
            "com.tencent.tmgp",                    // 腾讯游戏
            "com.tencent.tmgp.sgame",              // 王者荣耀
            "com.tencent.tmgp.smoba",              // 王者荣耀世界
            "com.tencent.ig",                      // PUBG
            "com.tencent.tmgp.pubgmhd",            // 和平精英
            "com.miHoYo",                          // 米哈游
            "com.miHoYo.GenshinImpact",            // 原神
            "com.miHoYo.Yuanshen",                 // 原神
            "com.miHoYo.hkrpg",                    // 崩坏：星穹铁道
            "com.miHoYo.bh3",                      // 崩坏 3
            "com.netease",                         // 网易游戏
            "com.netease.onmyoji",                 // 阴阳师
            "com.netease.mrzhna",                  // 明日之后
            "com.tencent.tmgp.speedmobile",        // QQ 飞车
            "com.tencent.tmgp.cod",                // 使命召唤
            "com.tencent.jkchess",                 // 金铲铲之战
            "com.tencent.tmgp.naruto",             // 火影忍者
            "com.garena.game.freefire",            // Free Fire
            "com.roblox.client",                   // Roblox
            "com.mojang.minecraftpe",              // 我的世界
            "com.ea.gp.fifamobile",                // FIFA
            "com.riotgames.league.wildrift",       // 英雄联盟手游
            "com.YoStarEN.Arknights",              // 明日方舟
            "com.sunborn.girlsfrontline.en",       // 少女前线
            "com.tencent.tmgp.和平精英",            // 和平精英
            "com.gameloft.android.ANMP.GloftA8HM", // 狂野飙车
            "com.netease.hyxd",                    // 荒野行动
            "com.netease.dwrg",                    // 大话西游
            "com.lilithgame.roc.gp",               // 万国觉醒
            "com.supercell.clashofclans",          // 部落冲突
            "com.supercell.brawlstars",            // 荒野乱斗
            "com.activision.callofduty.shooter",   // COD 手游
            "com.tencent.tmgp.dnf",                // DNF
            "com.wanmei.zhuxian",                  // 诛仙
            "com.tencent.tmgp.ak",                 // 天龙八部
            "com.yoozoo.got.tunnelplay",           // 权力的游戏
            "com.fenqile.fenqile",                 // 分期乐
            "com.proximabeta.mzf",                 // 明日方舟
            "com.nexon.bluearchive",               // 蔚蓝档案
            "com.dts.freefireth",                  // Free Fire
            "com.tencent.tmgp.cf"                  // 穿越火线
        )
        
        // 强制黑名单（不可移除）- 社交/社区类
        val FORCE_BLACKLIST_SOCIAL = setOf(
            "com.tencent.mobileqq",      // QQ
            "com.sina.weibo",            // 微博
            "com.zhihu.android",         // 知乎
            "com.xiaohongshu.android",   // 小红书
            "com.douban.frodo",          // 豆瓣
            "com.coolapk.market",        // 酷安
            "com.tieba.baidu",           // 百度贴吧
            "com.instagram.android",     // Instagram
            "com.facebook.katana",       // Facebook
            "com.twitter.android",       // Twitter
            "com.snapchat.android",      // Snapchat
            "com.discord",               // Discord
            "com.reddit.frontpage"       // Reddit
            // 微信、企业微信、飞书、钉钉等办公软件已移除 ✅
        )
        
        // 强制黑名单（不可移除）- 阅读/小说类
        val FORCE_BLACKLIST_READ = setOf(
            "com.qidian.QDReader",       // 起点读书
            "com.fanqie.reader",         // 番茄小说
            "com.mipush.push",           // 七猫小说
            "com.qq.reader",             // QQ 阅读
            "com.dangdang.reader",       // 当当阅读
            "com.ireader.reader",        // iReader
            "com.flyread.reader",        // 飞读小说
            "com.jingdong.app.reader",   // 京东读书
            "com.chaoxing.book",         // 超星学习通
            "com.douban.book",           // 豆瓣读书
            "com.douban.read"            // 豆瓣阅读
        )
        
        // 强制黑名单（不可移除）- 浏览器类
        val FORCE_BLACKLIST_BROWSER = setOf(
            "com.android.chrome",        // Chrome 浏览器
            "com.UCMobile",              // UC 浏览器
            "com.UCMobile.intl",         // UC 浏览器国际版
            "com.qq.browser",            // QQ 浏览器
            "com.baidu.browser",         // 百度浏览器
            "com.baidu.searchbox",       // 百度搜索
            "com.miui.browser",          // MIUI 浏览器
            "com.huawei.browser",        // 华为浏览器
            "com.vivo.browser",          // vivo 浏览器
            "com.oppo.browser",          // OPPO 浏览器
            "com.oneplus.browser",       // 一加浏览器
            "com.samsung.android.app.sbrowser", // Samsung 浏览器
            "org.mozilla.firefox",       // Firefox
            "com.microsoft.emmx",        // Microsoft Edge
            "com.brave.browser",         // Brave
            "com.opera.browser",         // Opera
            "com.yandex.browser",        // Yandex
            "com.cmcm.cmbrowser",        // 猎豹浏览器
            "com.qihoo.browser",         // 360 浏览器
            "com.android.browser"        // 安卓原生浏览器
        )
        
        // 强制黑名单（不可移除）- 音乐类
        val FORCE_BLACKLIST_MUSIC = setOf(
            "com.netease.cloudmusic",    // 网易云音乐
            "com.tencent.qqmusic",       // QQ 音乐
            "com.kuwo.player",           // 酷我音乐
            "com.kugou.android",         // 酷狗音乐
            "com.baidu.music",           // 百度音乐
            "fm.xiami.main",             // 虾米音乐
            "com.douban.fm",             // 豆瓣 FM
            "com.spotify.music",         // Spotify
            "com.google.android.apps.youtube.music" // YouTube Music
        )
        
        // 关键词黑名单（不可移除）
        val FORCE_KEYWORDS = listOf(
            // 游戏类
            "tmgp", "game", "gaming", "playgame", "mobilegame",
            "miHoYo", "mihaoyou", "yuanshen", "genshin", "honkai",
            "netease", "wangyigame", "tencentgame", "txgame",
            // 视频类
            "video", "movie", "tv", "film", "shortvideo", "shipin",
            "aweme", "douyin", "kuaishou", "nebula",
            "qqlive", "aiqiyi", "youku", "bilibili", "blbl",
            "youtube", "youtubemusic",
            // 社交娱乐类（排除办公）
            "weibo", "shejiao",
            "xiaohongshu", "red", "xhs",
            "zhihu", "douban", "coolapk",
            "tieba", "baidutieba",
            "instagram", "facebook", "twitter", "snapchat",
            "tiktok", "douyin",
            // 音乐类
            "music", "yinyue", "wangyiyunyinyue", "qqmusic",
            "kugou", "kuwo", "xiami",
            // 阅读/小说类
            "reader", "novel", "book", "yuedu", "xiaoshuo",
            "qidian", "qqreader", "fanqie", "qimao",
            // 浏览器类
            "browser", "liulanqi", "chrome", "firefox", "opera",
            "ucbrowser", "qqbrowser", "baidubrowser",
            "edge", "safari", "samsungbrowser",
            // 直播类
            "live", "zhibo", "douyu", "huya", "huajiao", "inke",
            "karaoke", "changba", "quanminkge"
            // 注意：排除 social（包含企业微信等办公应用）
        )
    }
    
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var appsListView: ListView
    private lateinit var loadingView: View
    private lateinit var addButton: Button
    private var installedApps: List<AppInfo> = emptyList()
    private var blacklistedPackages: MutableSet<String> = mutableSetOf()
    private var adapter: AppAdapter? = null
    
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
        
        // 添加按钮（初始禁用，数据加载完成后启用）
        val addButton = Button(this).apply {
            text = "+ 添加应用到黑名单"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            setPadding(30, 20, 30, 20)
            isEnabled = false  // 初始禁用
            alpha = 0.5f
            setOnClickListener {
                showAddAppDialog()
            }
        }
        layout.addView(addButton)
        
        // 保存按钮引用，数据加载完成后启用
        this.addButton = addButton
        
        // 加载提示
        loadingView = TextView(this).apply {
            text = "加载中..."
            textSize = 16f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 40)
        }
        layout.addView(loadingView)
        
        // 应用列表（初始不设置 adapter，数据加载完成后再设置）
        appsListView = ListView(this).apply {
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
                    // 数据加载完成后再创建并设置 adapter
                    adapter = AppAdapter(this@BlacklistManageActivity)
                    appsListView.adapter = adapter
                    loadingView.visibility = View.GONE
                    appsListView.visibility = View.VISIBLE
                    addButton.isEnabled = true  // 启用添加按钮
                    addButton.alpha = 1.0f
                    adapter!!.notifyDataSetChanged()
                    
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
               packageName in FORCE_BLACKLIST_SOCIAL ||
               packageName in FORCE_BLACKLIST_READ ||
               packageName in FORCE_BLACKLIST_BROWSER ||
               packageName in FORCE_BLACKLIST_MUSIC ||
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
        if (installedApps.isEmpty()) {
            Toast.makeText(this, "数据加载中，请稍后", Toast.LENGTH_SHORT).show()
            return
        }
        
        val nonBlacklistedApps = installedApps.filter { !it.isBlacklisted }
            .sortedBy { it.appName.lowercase() }
        
        if (nonBlacklistedApps.isEmpty()) {
            Toast.makeText(this, "所有应用已在黑名单中", Toast.LENGTH_SHORT).show()
            return
        }
        
        val builder = AlertDialog.Builder(this)
        builder.setTitle("选择要添加到黑名单的应用")
        
        val appNames = nonBlacklistedApps.map { "${it.appName} (${it.packageName})" }.toTypedArray()
        
        builder.setItems(appNames) { dialog, which ->
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
                    adapter?.notifyDataSetChanged()
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
                    adapter?.notifyDataSetChanged()
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
            val app = installedApps.getOrNull(position) ?: return view
            
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
            .setPositiveButton("移除") { dialog, which ->
                removeFromBlacklist(app.packageName, app.appName, app.isForceBlacklist)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

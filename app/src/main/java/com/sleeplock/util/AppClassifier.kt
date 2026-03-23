package com.sleeplock.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * 应用分类器 - 识别娱乐应用
 */
class AppClassifier(private val context: Context) {
    
    companion object {
        private const val TAG = "AppClassifier"
    }
    
    // 娱乐应用关键词黑名单
    private val entertainmentKeywords = setOf(
        // 游戏类
        "tmgp", "game", "gaming", "play", "wangzhe", "huping", "yuanshen", "mihoyo",
        // 视频类
        "douyin", "kuaishou", "bilibili", "aiqiyi", "youku", "tencentvideo", "video",
        // 社交娱乐类
        "weibo", "xiaohongshu", "douban", "tieba", "hupu", "zhihu",
        // 购物类
        "taobao", "jd", "pinduoduo", "shopping",
        // 直播类
        "douyu", "huya", "live"
    )
    
    // 精确包名黑名单（常见娱乐应用）
    private val blacklistPackageNames = setOf(
        "com.tencent.tmgp.sgame",           // 王者荣耀
        "com.tencent.tmgp.pubgmhd",         // 和平精英
        "com.miHoYo.GenshinImpact",         // 原神
        "com.miHoYo.bh3",                   // 崩坏 3
        "com.ss.android.ugc.aweme",         // 抖音
        "com.kuaishou.nebula",              // 快手
        "tv.danmaku.bili",                  // 哔哩哔哩
        "com.qiyi.video",                   // 爱奇艺
        "com.youku.phone",                  // 优酷
        "com.tencent.qqlive",               // 腾讯视频
        "com.sina.weibo",                   // 微博
        "com.xingin.xiaohongshu",           // 小红书
        "com.douban.frodo",                 // 豆瓣
        "com.baidu.tieba",                  // 百度贴吧
        "com.taobao.taobao",                // 淘宝
        "com.jingdong.app.mall",            // 京东
        "com.xunmeng.pinduoduo",            // 拼多多
        "com.douyu.kit",                    // 斗鱼
        "com.huya.kit"                      // 虎牙
    )
    
    /**
     * 判断是否为娱乐应用
     */
    fun isEntertainmentApp(packageName: String): Boolean {
        // 检查是否在精确黑名单中
        if (packageName in blacklistPackageNames) {
            Log.d(TAG, "精确匹配娱乐应用：$packageName")
            return true
        }
        
        // 检查包名是否包含关键词
        val lowerCase = packageName.lowercase()
        for (keyword in entertainmentKeywords) {
            if (lowerCase.contains(keyword)) {
                Log.d(TAG, "关键词匹配娱乐应用：$packageName (包含 $keyword)")
                return true
            }
        }
        
        // 尝试通过应用类别判断
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val category = appInfo.category
            
            // 游戏类别
            if (category and android.content.pm.ApplicationInfo.CATEGORY_GAME != 0) {
                Log.d(TAG, "系统类别判断为游戏：$packageName")
                return true
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "未找到应用：$packageName")
        }
        
        return false
    }
    
    /**
     * 获取应用名称
     */
    fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}

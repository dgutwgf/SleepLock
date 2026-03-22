package com.sleeplock

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application 入口类
 */
@HiltAndroidApp
class SleepLockApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 初始化通知渠道
        // NotificationChannels.createAllChannels(this)
    }
}

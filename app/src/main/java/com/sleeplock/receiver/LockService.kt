package com.sleeplock.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 设备管理员服务 - 核心锁机功能
 * 
 * 功能：
 * 1. 锁定屏幕
 * 2. 定时解锁
 * 3. 权限保护（防止直接卸载）
 */
class LockService : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "LockService"
        const val ACTION_LOCK = "com.sleeplock.action.LOCK"
        const val ACTION_UNLOCK = "com.sleeplock.action.UNLOCK"
    }

    /**
     * 设置定时任务
     */
    fun scheduleLock(context: Context, lockTime: String, unlockTime: String) {
        Log.d(TAG, "设置定时任务：锁屏=$lockTime, 解锁=$unlockTime")
        // TODO: 使用 AlarmManager 设置精确闹钟
    }

    /**
     * 执行锁屏
     */
    fun executeLock(context: Context) {
        Log.d(TAG, "执行锁屏")
        
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(context, this)
        
        if (devicePolicyManager.isAdminActive(componentName)) {
            devicePolicyManager.lockNow()
            Log.d(TAG, "锁屏成功")
            
            // 启动监控服务
            // val monitorIntent = Intent(context, MonitorService::class.java)
            // context.startForegroundService(monitorIntent)
        } else {
            Log.w(TAG, "设备管理员权限未激活")
            // 请求权限
            requestAdminPermission(context)
        }
    }

    /**
     * 执行解锁
     */
    fun executeUnlock(context: Context) {
        Log.d(TAG, "执行解锁")
        // TODO: 停止监控服务，发送解锁广播
    }

    /**
     * 请求设备管理员权限
     */
    private fun requestAdminPermission(context: Context) {
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(context, this)
        
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "需要设备管理员权限来实现锁屏功能")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "设备管理员权限已启用")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "设备管理员权限已禁用 - 用户可能尝试卸载")
        // TODO: 记录违规日志
    }

    override fun onPasswordChanged(context: Context, intent: Intent, type: Int) {
        super.onPasswordChanged(context, intent, type)
        Log.d(TAG, "密码已更改")
    }
}

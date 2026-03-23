package com.sleeplock.service

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * 设备管理员服务 - 实现锁屏/解锁功能
 */
class LockService : DeviceAdminReceiver() {
    
    companion object {
        private const val TAG = "LockService"
        
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, LockService::class.java)
        }
        
        /**
         * 检查是否已授予设备管理员权限
         */
        fun isAdminActive(context: Context): Boolean {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return devicePolicyManager.isAdminActive(getComponentName(context))
        }
        
        /**
         * 请求设备管理员权限
         */
        fun requestAdminPermission(context: Context, requestCode: Int = 1001) {
            try {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, 
                    "专注锁机需要设备管理员权限来实现以下功能：\n\n" +
                    "• 定时锁定屏幕，防止熬夜玩手机\n" +
                    "• 定时自动解锁，早上正常使用\n" +
                    "• 保护设置不被恶意修改\n\n" +
                    "撤销权限后应用将无法锁机。")
                
                // 如果 context 是 Activity，使用 startActivityForResult
                if (context is Activity) {
                    context.startActivityForResult(intent, requestCode)
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                Log.d(TAG, "已启动设备管理员授权页面")
            } catch (e: Exception) {
                Log.e(TAG, "启动设备管理员授权页面失败", e)
                Toast.makeText(context, "无法打开授权页面，请手动到设置中授予设备管理员权限", Toast.LENGTH_LONG).show()
            }
        }
        
        /**
         * 锁定屏幕
         */
        fun lockScreen(context: Context) {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            try {
                if (isAdminActive(context)) {
                    devicePolicyManager.lockNow()
                    Log.d(TAG, "屏幕已锁定")
                } else {
                    Log.w(TAG, "设备管理员权限未激活")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "锁定屏幕失败", e)
            }
        }
    }
    
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "设备管理员权限已启用")
    }
    
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "设备管理员权限已禁用")
    }
    
    override fun onPasswordChanged(context: Context, intent: Intent) {
        super.onPasswordChanged(context, intent)
        Log.d(TAG, "密码已更改")
    }
    
    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.w(TAG, "密码尝试失败")
    }
}

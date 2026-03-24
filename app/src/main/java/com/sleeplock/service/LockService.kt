package com.sleeplock.service

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
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
         * 锁定屏幕 - 多种方法尝试
         */
        fun lockScreen(context: Context): Boolean {
            Log.d(TAG, "开始执行锁屏...")
            
            // 方法1: 使用 DevicePolicyManager.lockNow()（主要方法）
            if (lockWithDevicePolicy(context)) {
                return true
            }
            
            // 方法2: 使用 AccessibilityService 的全局锁屏动作 (Android 9+)
            if (lockWithAccessibility(context)) {
                return true
            }
            
            // 方法3: 启动锁屏 Activity（备用方案）
            if (lockWithActivity(context)) {
                return true
            }
            
            Log.e(TAG, "所有锁屏方法均失败")
            Toast.makeText(context, "锁屏失败，请检查设备管理员权限", Toast.LENGTH_LONG).show()
            return false
        }
        
        /**
         * 方法1: DevicePolicyManager.lockNow()
         */
        private fun lockWithDevicePolicy(context: Context): Boolean {
            return try {
                if (!isAdminActive(context)) {
                    Log.w(TAG, "设备管理员权限未激活")
                    return false
                }
                
                val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                
                // 执行锁屏
                devicePolicyManager.lockNow()
                
                Log.d(TAG, "✅ DevicePolicyManager.lockNow() 执行成功")
                true
            } catch (e: SecurityException) {
                Log.e(TAG, "❌ DevicePolicyManager.lockNow() 权限异常", e)
                false
            } catch (e: Exception) {
                Log.e(TAG, "❌ DevicePolicyManager.lockNow() 失败", e)
                false
            }
        }
        
        /**
         * 方法2: AccessibilityService 全局锁屏动作 (Android 9+)
         */
        private fun lockWithAccessibility(context: Context): Boolean {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val accessibilityService = MonitorAccessibilityService.getInstance()
                    if (accessibilityService != null) {
                        val result = accessibilityService.performGlobalAction(
                            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
                        )
                        if (result) {
                            Log.d(TAG, "✅ AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN 执行成功")
                            return true
                        } else {
                            Log.w(TAG, "❌ AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN 返回 false")
                        }
                    } else {
                        Log.w(TAG, "AccessibilityService 未运行")
                    }
                }
                false
            } catch (e: Exception) {
                Log.e(TAG, "❌ AccessibilityService 锁屏失败", e)
                false
            }
        }
        
        /**
         * 方法3: 启动全屏锁屏 Activity（最后备用方案）
         */
        private fun lockWithActivity(context: Context): Boolean {
            return try {
                val intent = Intent(context, com.sleeplock.ui.LockScreenActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or 
                               Intent.FLAG_ACTIVITY_CLEAR_TASK or
                               Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                context.startActivity(intent)
                Log.d(TAG, "✅ 启动 LockScreenActivity 作为锁屏界面")
                true
            } catch (e: Exception) {
                Log.e(TAG, "❌ 启动锁屏 Activity 失败", e)
                false
            }
        }
    }
    
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "设备管理员权限已启用")
        Toast.makeText(context, "设备管理员权限已启用", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "设备管理员权限已禁用")
        Toast.makeText(context, "设备管理员权限已禁用，锁机功能将无法使用", Toast.LENGTH_LONG).show()
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
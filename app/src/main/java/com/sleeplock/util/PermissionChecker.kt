package com.sleeplock.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.sleeplock.service.LockService

/**
 * 权限检查工具 - 检查所有必要的权限是否已授予
 */
object PermissionChecker {
    
    private const val TAG = "PermissionChecker"
    
    data class PermissionStatus(
        val name: String,
        val granted: Boolean,
        val required: Boolean,
        val description: String,
        val fixAction: String? = null
    )
    
    /**
     * 检查所有必要权限
     */
    fun checkAllPermissions(context: Context): List<PermissionStatus> {
        return listOf(
            checkDeviceAdmin(context),
            checkAccessibilityService(context),
            checkUsageStatsPermission(context),
            checkSystemAlertWindow(context),
            checkBatteryOptimization(context),
            checkNotificationPermission(context),
            checkDrawOverOtherApps(context)
        )
    }
    
    /**
     * 1. 设备管理员权限 - 最关键
     */
    fun checkDeviceAdmin(context: Context): PermissionStatus {
        val granted = LockService.isAdminActive(context)
        return PermissionStatus(
            name = "设备管理员权限",
            granted = granted,
            required = true,
            description = "用于锁定屏幕和防止卸载",
            fixAction = if (!granted) DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN else null
        )
    }
    
    /**
     * 2. 无障碍服务权限 - 用于监控和拦截应用
     */
    fun checkAccessibilityService(context: Context): PermissionStatus {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        
        val granted = enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName
        }
        
        return PermissionStatus(
            name = "无障碍服务权限",
            granted = granted,
            required = true,
            description = "用于监控前台应用并拦截非白名单应用",
            fixAction = if (!granted) Settings.ACTION_ACCESSIBILITY_SETTINGS else null
        )
    }
    
    /**
     * 3. 应用使用统计权限 - 用于获取前台应用
     */
    fun checkUsageStatsPermission(context: Context): PermissionStatus {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
        val mode = appOps?.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        
        val granted = mode == android.app.AppOpsManager.MODE_ALLOWED
        
        return PermissionStatus(
            name = "应用使用统计权限",
            granted = granted,
            required = true,
            description = "用于获取当前运行的前台应用",
            fixAction = if (!granted) Settings.ACTION_USAGE_ACCESS_SETTINGS else null
        )
    }
    
    /**
     * 4. 悬浮窗权限
     */
    fun checkSystemAlertWindow(context: Context): PermissionStatus {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
        
        return PermissionStatus(
            name = "悬浮窗权限",
            granted = granted,
            required = true,
            description = "用于在锁屏界面之上显示拦截界面",
            fixAction = if (!granted) "android.settings.MANAGE_OVERLAY_PERMISSION" else null
        )
    }
    
    /**
     * 5. 电池优化豁免
     */
    fun checkBatteryOptimization(context: Context): PermissionStatus {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
        
        return PermissionStatus(
            name = "电池优化豁免",
            granted = granted,
            required = true,
            description = "防止系统在后台杀死服务",
            fixAction = if (!granted) Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS else null
        )
    }
    
    /**
     * 6. 通知权限 (Android 13+)
     */
    fun checkNotificationPermission(context: Context): PermissionStatus {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        
        return PermissionStatus(
            name = "通知权限",
            granted = granted,
            required = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            description = "用于显示锁机状态通知",
            fixAction = null
        )
    }
    
    /**
     * 7. 显示在其他应用上层
     */
    fun checkDrawOverOtherApps(context: Context): PermissionStatus {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
        
        return PermissionStatus(
            name = "显示在其他应用上层",
            granted = granted,
            required = true,
            description = "用于显示全屏拦截界面",
            fixAction = if (!granted) Settings.ACTION_MANAGE_OVERLAY_PERMISSION else null
        )
    }
    
    /**
     * 生成权限状态报告
     */
    fun generateReport(context: Context): String {
        val permissions = checkAllPermissions(context)
        val sb = StringBuilder()
        
        sb.appendLine("=== 权限检查报告 ===")
        sb.appendLine()
        
        permissions.forEach { perm ->
            val status = if (perm.granted) "✅" else "❌"
            val required = if (perm.required) "[必需]" else "[可选]"
            sb.appendLine("$status $required ${perm.name}")
            sb.appendLine("   └─ ${perm.description}")
            if (!perm.granted && perm.fixAction != null) {
                sb.appendLine("   └─ 需要手动授予")
            }
            sb.appendLine()
        }
        
        val allGranted = permissions.filter { it.required }.all { it.granted }
        sb.appendLine("===================")
        if (allGranted) {
            sb.appendLine("✅ 所有必需权限已授予")
        } else {
            sb.appendLine("❌ 有必需权限未授予，锁机功能可能无法正常工作")
        }
        
        return sb.toString()
    }
    
    /**
     * 打印详细日志
     */
    fun logDetailedStatus(context: Context) {
        val permissions = checkAllPermissions(context)
        
        Log.d(TAG, "=== 权限详细检查 ===")
        permissions.forEach { perm ->
            Log.d(TAG, "${if (perm.granted) "✅" else "❌"} ${perm.name}: ${perm.description}")
        }
        Log.d(TAG, "========================")
    }
}

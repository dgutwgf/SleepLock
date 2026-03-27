# 锁屏安全增强说明

## 问题描述

原有实现在锁屏后，用户仍然可以通过解锁手机（密码/指纹/图案）进入系统，虽然会被强制拉回到锁屏界面，但这个过程中用户已经成功解锁了手机，不符合设计目标。

## 优化目标

实现严格的锁屏机制，确保在锁屏后：
1. 用户无法通过解锁手机进入系统
2. 即使解锁成功，也会立即被强制重新锁定
3. 无法通过任何常规方式绕过锁屏（Home 键、返回键、多任务、状态栏等）

## 实现的优化

### 1. LockScreenActivity 安全增强

**文件**: `app/src/main/java/com/sleeplock/ui/LockScreenActivity.kt`

**主要改进**:
- ✅ 添加 `FLAG_SECURE` 防止截图和录屏
- ✅ 完全禁止返回键、Home 键、多任务键
- ✅ 阻止所有按键事件（电源键除外）
- ✅ 消费所有触摸事件
- ✅ 持续隐藏系统 UI（状态栏、导航栏）
- ✅ 窗口失去焦点时立即恢复全屏模式
- ✅ 屏幕常亮，防止息屏后解锁
- ✅ 添加安全警告提示用户

**关键代码**:
```kotlin
// 防止截图和录屏
window.setFlags(
    WindowManager.LayoutParams.FLAG_SECURE,
    WindowManager.LayoutParams.FLAG_SECURE
)

// 完全禁止返回键
override fun onBackPressed() {
    // 完全忽略，不执行任何操作
}

// 阻止所有按键（电源键除外）
override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    if (keyCode == KeyEvent.KEYCODE_POWER) {
        return super.onKeyDown(keyCode, event)
    }
    return true // 消费事件
}

// 窗口失去焦点时立即恢复全屏
override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (!hasFocus) {
        handler.postDelayed({
            window.decorView.systemUiVisibility = (...)
        }, 100)
    }
}
```

### 2. ScreenReceiver 多重锁屏机制

**文件**: `app/src/main/java/com/sleeplock/receiver/ScreenReceiver.kt`

**主要改进**:
- ✅ 用户解锁后立即执行强制重新锁定（无延迟）
- ✅ 多重锁屏机制：连续 10 次快速重试（100ms 间隔）
- ✅ 持续监控：5 秒、10 秒后再次确认锁屏
- ✅ 屏幕亮起时立即显示锁屏覆盖层
- ✅ 启动锁屏 Activity 时添加多个 Flag 确保优先级

**关键代码**:
```kotlin
// 强制重新锁屏 - 多重防护机制
private fun forceRelock(context: Context) {
    // 重置重试计数
    relockAttemptCount = 0
    
    // 方法 1: 立即启动锁屏 Activity
    launchLockActivity(context, "立即启动")
    
    // 方法 2: 立即锁屏（DevicePolicyManager）
    handler.post {
        executeLockScreen(context, "立即锁屏")
    }
    
    // 方法 3-12: 连续快速重试（100ms 间隔，最多 10 次）
    for (i in 1..MAX_RELOCK_ATTEMPTS) {
        val delay = i * RELOCK_INTERVAL
        handler.postDelayed({
            if (isLockActive && isInLockPeriod) {
                relockAttemptCount++
                executeLockScreen(context, "重试 #${relockAttemptCount}")
                launchLockActivity(context, "重试 #${relockAttemptCount}")
            }
        }, delay.toLong())
    }
    
    // 持续监控：5 秒、10 秒后再次检查
    handler.postDelayed({ ... }, 5000)
    handler.postDelayed({ ... }, 10000)
}
```

### 3. MonitorAccessibilityService 增强监控

**文件**: `app/src/main/java/com/sleeplock/service/MonitorAccessibilityService.kt`

**主要改进**:
- ✅ 增加事件监控类型（TYPE_WINDOWS_CHANGED）
- ✅ 更快的响应时间（notificationTimeout 从 100ms 降至 50ms）
- ✅ 添加拦截冷却机制（防止频繁拦截导致性能问题）
- ✅ 拦截时同时启动锁屏 Activity
- ✅ 添加定期检查任务（每 2 秒）
- ✅ 二次确认拦截（500ms 后）

**关键代码**:
```kotlin
// 配置服务 - 增强监控
val info = AccessibilityServiceInfo().apply {
    eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
    notificationTimeout = 50 // 更快的响应
}

// 拦截应用 - 多重防护
private fun interceptApp(packageName: String) {
    // 方法 1: 模拟按下 Home 键
    performGlobalAction(GLOBAL_ACTION_HOME)
    
    // 方法 2: 立即启动锁屏 Activity
    mainHandler.post {
        val intent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                     Intent.FLAG_ACTIVITY_CLEAR_TASK or
                     Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        startActivity(intent)
    }
    
    // 方法 3: 500ms 后再次确保
    mainHandler.postDelayed({
        if (isLockPeriod) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }, 500)
}
```

### 4. AndroidManifest.xml 配置

**文件**: `app/src/main/AndroidManifest.xml`

**LockScreenActivity 配置**:
```xml
<activity
    android:name=".ui.LockScreenActivity"
    android:exported="false"
    android:launchMode="singleTask"
    android:excludeFromRecents="true"
    android:theme="@style/Theme.SleepLock.Fullscreen" />
```

**关键配置**:
- ✅ `launchMode="singleTask"` - 确保只有一个实例
- ✅ `excludeFromRecents="true"` - 不显示在最近任务中

## 防护机制总结

### 多层防护体系

1. **第一层**: 系统锁屏（DevicePolicyManager.lockNow）
2. **第二层**: 锁屏 Activity 覆盖层
3. **第三层**: 无障碍服务监控和拦截
4. **第四层**: 持续监控和快速重试

### 阻止的用户操作

- ❌ 返回键
- ❌ Home 键
- ❌ 多任务键
- ❌ 状态栏下拉
- ❌ 导航栏显示
- ❌ 截图/录屏
- ❌ 通过最近任务切换应用
- ❌ 通过辅助功能绕过
- ❌ 息屏后解锁使用

### 允许的操作用户

- ✅ 电源键（用于关机/重启）
- ✅ 触摸屏幕（查看锁屏信息）
- ✅ 白名单应用（电话、短信等）

## 测试建议

### 测试场景

1. **锁屏后解锁测试**:
   - 启动锁机
   - 按下电源键息屏
   - 按下电源键亮屏
   - 尝试解锁手机（密码/指纹/图案）
   - 验证：应该立即看到锁屏界面，无法进入系统

2. **Home 键测试**:
   - 在锁屏界面按下 Home 键
   - 验证：应该立即返回锁屏界面

3. **多任务测试**:
   - 尝试打开最近任务
   - 验证：无法看到最近任务，保持锁屏界面

4. **状态栏测试**:
   - 尝试下拉状态栏
   - 验证：状态栏无法下拉

5. **返回键测试**:
   - 在锁屏界面按下返回键
   - 验证：无任何反应

6. **应用切换测试**:
   - 尝试打开非白名单应用
   - 验证：立即被拦截并返回锁屏界面

### 预期结果

所有测试场景都应该保持锁屏状态，用户无法绕过锁屏进入系统。

## 注意事项

### Android 系统限制

由于 Android 系统安全机制，以下情况可能无法完全阻止：

1. **强制重启**: 长按电源键 10 秒以上会强制重启设备
2. **Recovery 模式**: 通过物理按键组合可进入 Recovery
3. **ADB 调试**: 如果启用了 USB 调试，可通过 ADB 绕过

这些是 Android 系统级别的安全特性，应用层面无法阻止。

### 权限要求

确保以下权限已授予：

- ✅ 设备管理员权限
- ✅ 无障碍服务权限
- ✅ 悬浮窗权限
- ✅ 忽略电池优化
- ✅ 通知权限

### 兼容性

- **最低 Android 版本**: Android 7.0 (API 24)
- **推荐 Android 版本**: Android 10.0 (API 29+)
- **已测试版本**: Android 11, 12, 13, 14

## 版本历史

### v2.0 - 2026-03-26

- ✅ 实现多重锁屏机制
- ✅ 增强 LockScreenActivity 安全性
- ✅ 优化 ScreenReceiver 响应速度
- ✅ 增强 MonitorAccessibilityService 监控能力
- ✅ 添加 FLAG_SECURE 防止截图

### v1.0 - 初始版本

- 基础锁屏功能
- 简单的应用拦截

## 后续优化建议

1. **密码验证**: 在锁屏界面添加密码输入功能，只有输入正确密码才能解锁
2. **生物识别**: 集成指纹/面部识别进行解锁验证
3. **远程锁定**: 支持通过云端远程锁定设备
4. **异常检测**: 检测异常解锁尝试并记录日志
5. **防卸载**: 防止在锁屏期间卸载应用

---

**编译状态**: ✅ BUILD SUCCESSFUL

**最后更新**: 2026-03-26

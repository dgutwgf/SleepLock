# 锁机应用修复说明

## 修复的问题

### 🔴 核心问题：锁屏后解锁又可以正常使用手机

**根本原因**（2026-03-26 19:30 发现）：
1. **`updateLockPeriod` 没有检查 `is_lock_active` 标志** - 只检查时间，不检查用户是否启动了锁机服务
2. **MonitorService 检查间隔太长** - 30 秒才检查一次，解锁后响应太慢
3. **解锁后没有立即恢复监控** - 无障碍服务在锁屏期间可能被系统暂停

**详细分析**：
```
用户操作流程：
1. 启动锁机服务 → is_lock_active = true
2. 锁屏 → 系统进入休眠
3. 解锁 → 系统恢复，但无障碍服务的 isLockPeriod 可能未更新
4. 打开应用 → 无障碍服务检查 isLockPeriod，但它是 false（因为没检查 is_lock_active）
5. 应用正常打开 ❌
```

### ✅ 最终修复方案

#### 修复 1：`updateLockPeriod` 同时检查时间和 `is_lock_active`
```kotlin
// 之前：只检查时间
val shouldBeLocked = isInTimePeriod

// 现在：同时检查时间和激活状态
val isLockActive = prefs.getBoolean("is_lock_active", false)
val shouldBeLocked = isInTimePeriod && (isLockActive || testMode)
```

#### 修复 2：缩短检查间隔
```kotlin
// 之前
private const val CHECK_INTERVAL = 30 * 1000L // 30 秒

// 现在
private const val CHECK_INTERVAL = 5 * 1000L // 5 秒
```

#### 修复 3：解锁后立即恢复监控
```kotlin
// ScreenReceiver.kt
Intent.ACTION_USER_PRESENT -> {
    // 立即更新锁机时段（强制刷新）
    scope.launch {
        accessibilityService.updateLockPeriod()
        delay(500)
        accessibilityService.updateLockPeriod() // 双重确认
    }
}
```

#### 修复 4：每次窗口变化都更新状态
```kotlin
// MonitorAccessibilityService.kt
override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    when (event.eventType) {
        TYPE_WINDOW_STATE_CHANGED -> {
            serviceScope.launch {
                updateLockPeriod() // 每次都更新
                checkAndIntercept(packageName)
            }
        }
    }
}
```

### 问题 2：点击黑名单应用仍然能打开
**原因**：
- 存在 3 秒的拦截冷却时间 (`INTERCEPT_COOLDOWN`)
- 在冷却时间内，用户可以快速点击并打开应用
- 拦截响应不够及时

**解决方案**：
- ✅ **完全移除冷却时间限制** - 每次检测到黑名单应用都立即拦截
- ✅ **添加 `isIntercepting` 状态标志** - 防止重复拦截，但不阻止新的拦截
- ✅ **优化拦截延迟** - 从 1000ms 降低到 500ms
- ✅ **二次确认返回桌面** - 1.5 秒后再次确保返回桌面
- ✅ **3 秒后重置拦截状态** - 允许下一次拦截

## 核心修改

### 1. MonitorAccessibilityService.kt

#### 移除冷却时间
```kotlin
// 之前
private const val INTERCEPT_COOLDOWN = 3000L // 拦截冷却时间 3 秒
private var lastInterceptTime = 0L

// 现在
private const val INTERCEPT_DELAY = 500L // 拦截延迟（防止误判）
private var isIntercepting = false // 是否正在拦截中
```

#### 优化拦截逻辑
```kotlin
// 之前：检查冷却时间
if (now - lastInterceptTime < INTERCEPT_COOLDOWN) {
    return
}

// 现在：检查是否正在拦截
if (isIntercepting) {
    Log.d(TAG, "⚠️ 正在拦截中，跳过：$packageName")
    return
}
```

#### 增强拦截响应
```kotlin
// 方法 1: 立即启动拦截界面（0 延迟）
mainHandler.post {
    startActivity(intent)
}

// 方法 2: 0.5 秒后模拟 Home 键（更快响应）
mainHandler.postDelayed({
    performGlobalAction(GLOBAL_ACTION_HOME)
}, 500)

// 方法 3: 1.5 秒后再次确保
mainHandler.postDelayed({
    performGlobalAction(GLOBAL_ACTION_HOME)
}, 1500)

// 方法 4: 3 秒后重置拦截状态
mainHandler.postDelayed({
    isIntercepting = false
}, 3000)
```

### 2. LockScreenActivity.kt

#### 显示被拦截应用信息
```kotlin
interceptedPackageName = intent.getStringExtra("package_name") ?: ""
interceptReason = intent.getStringExtra("reason") ?: ""

messageTextView.text = "检测到：$interceptedPackageName\n原因：$interceptReason\n\n请返回桌面或使用白名单应用"
```

#### 添加持续监控
```kotlin
private fun startContinuousMonitor() {
    handler.postDelayed(object : Runnable {
        override fun run() {
            if (!isFinishing && remainingSeconds > 0) {
                // 确保拦截界面始终在最上层
                window.decorView.systemUiVisibility = (...)
                handler.postDelayed(this, RECHECK_DELAY)
            }
        }
    }, RECHECK_DELAY)
}
```

### 3. accessibility_service_config.xml

#### 优化无障碍服务配置
```xml
<!-- 增加 WindowsChanged 事件类型 -->
android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeWindowsChanged"

<!-- 增加键盘事件过滤 -->
android:accessibilityFlags="...|flagRequestFilterKeyEvents"

<!-- 降低通知延迟，提高响应速度 -->
android:notificationTimeout="50"
```

## 工作流程

### 拦截流程
```
用户点击黑名单应用
    ↓
无障碍服务检测到窗口变化（50ms 内）
    ↓
检查是否在锁机时段
    ↓
检查是否在白名单
    ↓
❌ 非白名单 → 立即拦截
    ↓
1. 显示拦截界面（0ms）
2. 模拟 Home 键（500ms）
3. 二次确认（1500ms）
4. 重置状态（3000ms）
    ↓
用户返回桌面，拦截完成
```

### 持续监控
```
锁机时段开始
    ↓
启动定期检查任务（每秒）
    ↓
检查当前前台应用
    ↓
如果是黑名单应用 → 拦截
    ↓
继续监控...
```

## 测试建议

### 测试场景 1：锁机时段内打开黑名单应用
1. 设置锁机时间（例如 22:00 - 06:00）
2. 等待进入锁机时段
3. 尝试打开抖音、微信、游戏等黑名单应用
4. **预期结果**：立即显示拦截界面，0.5 秒后返回桌面

### 测试场景 2：锁屏后解锁
1. 进入锁机时段
2. 手动锁屏（电源键）
3. 解锁手机
4. 尝试打开黑名单应用
5. **预期结果**：无障碍服务继续工作，立即拦截

### 测试场景 3：快速连续点击
1. 进入锁机时段
2. 快速连续点击黑名单应用图标
3. **预期结果**：每次点击都会被拦截，不会有任何应用打开

### 测试场景 4：白名单应用
1. 进入锁机时段
2. 打开电话、短信、设置等白名单应用
3. **预期结果**：正常使用，不被拦截

## 注意事项

### 无障碍服务必须保持运行
- 进入设置 → 无障碍 → 已下载的服务 → 开启"专注锁机监控"
- 部分手机系统可能会自动关闭无障碍服务，需要手动保持开启

### 电池优化设置
- 进入设置 → 电池 → 应用启动管理
- 找到"专注锁机"应用
- 关闭"自动管理"，开启"允许后台活动"

### 锁机时段设置
- 在设置中正确配置锁机时间和解锁时间
- 支持跨夜设置（例如 22:00 - 06:00）

## 技术细节

### 为什么移除冷却时间？
- 冷却时间的初衷是防止过度拦截
- 但实际上给用户留下了绕过机会
- 现代手机性能足够处理频繁检查
- 使用 `isIntercepting` 标志足以防止重复拦截

### 为什么降低 Home 键延迟？
- 原来的 1 秒延迟太长，应用可能已经启动
- 500ms 是平衡点：足够快，又不会误判
- 二次确认（1500ms）确保应用被完全关闭

### 为什么添加持续监控？
- 拦截界面显示期间，用户可能尝试其他操作
- 持续监控确保界面始终在最上层
- 防止用户通过系统手势绕过拦截

## 版本信息

### v1.4.0 (2026-03-26 20:00) - 添加日志系统
- **修复日期**: 2026-03-26
- **编译状态**: ✅ 成功
- **新增功能**: 
  - ✅ **完整的执行日志系统** - 记录应用运行时所有关键行为
  - ✅ **日志查看界面** - 设置页面可查看最近 200 条日志
  - ✅ **日志导出功能** - 支持导出为 HTML 格式并分享
  - ✅ **中文日志描述** - 所有日志均为中文，方便理解
  - ✅ **时间戳记录** - 精确到毫秒
  - ✅ **分类和级别** - 支持 8 种分类和 5 个级别
  - ✅ **自动清理** - 保留 7 天内的日志，最多 1000 条

#### 日志分类
- 📱 通用 (GENERAL)
- ⚙️ 服务 (SERVICE)
- ♿ 无障碍 (ACCESSIBILITY)
- 🚫 拦截 (INTERCEPT)
- 📵 屏幕状态 (SCREEN)
- ⚙️ 设置 (SETTINGS)
- ⏰ 定时任务 (SCHEDULER)
- 💾 数据库 (DATABASE)

#### 日志级别
- 🔍 详细 (VERBOSE)
- 🐛 调试 (DEBUG)
- ℹ️ 信息 (INFO)
- ⚠️ 警告 (WARNING)
- ❌ 错误 (ERROR)

#### 日志记录位置
- **无障碍服务连接/断开** - 记录服务状态变化
- **应用拦截事件** - 记录被拦截的应用包名和原因
- **锁机时段更新** - 记录锁机状态变化及原因
- **屏幕状态变化** - 记录锁屏/解锁事件
- **服务启动/停止** - 记录后台服务生命周期

### v1.3.1 (2026-03-26 19:30) - 修复锁屏后解锁问题
- **核心修复**: 
  - ✅ `updateLockPeriod` 现在同时检查时间和 `is_lock_active` 标志
  - ✅ MonitorService 检查间隔从 30 秒缩短到 5 秒
  - ✅ 解锁后立即强制更新锁机状态（双重确认）
  - ✅ 每次窗口变化都更新锁机时段
  - ✅ 移除冷却时间限制
  - ✅ 加快拦截响应速度（500ms）

### v1.3.0 (2026-03-26 18:35) - 初始修复
- 移除冷却时间限制
- 加快拦截响应速度
- 增强拦截界面稳定性
- 显示被拦截应用信息

---

## 📥 下载

**最新版本 APK**: [app-debug.apk](https://lightai.cloud.tencent.com/drive/preview?filePath=1774524624632/app-debug.apk) (6.4MB)

---

## 🧪 测试步骤（重要）

### 查看日志（新功能）
1. 打开应用 → 设置
2. 点击 **"📋 查看执行日志"**
3. 查看最近的日志记录
4. 可以：
   - 🔄 刷新日志列表
   - 📤 导出日志为 HTML 并分享
   - 🗑️ 清除所有日志

### 日志示例
```
19:45:32.123 ℹ️ ⚙️ SERVICE [LockPeriod]: 🔄 锁机时段更新：true (时间=true, 激活=true, 测试=true)
19:45:35.456 ℹ️ 🚫 INTERCEPT [AppIntercept]: 🚫 拦截应用：com.ss.android.ugc.aweme (原因：娱乐应用)
19:45:36.789 ℹ️ 📵 SCREEN [ScreenReceiver]: 🔓 用户解锁 - 准备恢复监控
19:45:37.234 ℹ️ ⚙️ SERVICE [AccessibilityService]: ✅ 服务已连接 - 严格监控模式
```

### 测试场景 1：锁屏后解锁（核心问题）
1. 安装新版本 APK
2. 激活设备管理员权限
3. 点击"开始锁机"（测试模式 2 分钟）
4. **立即锁屏**（按电源键）
5. 等待 10 秒
6. **解锁手机**
7. 尝试打开抖音、微信等黑名单应用
8. **预期结果**: 立即显示拦截界面，应用无法打开 ✅
9. **查看日志**: 设置 → 查看执行日志，确认拦截记录

### 测试场景 2：连续解锁测试
1. 保持锁机服务运行
2. 锁屏 → 解锁 → 尝试打开黑名单应用
3. 再次锁屏 → 再次解锁 → 再次尝试
4. **预期结果**: 每次解锁后都无法打开黑名单应用 ✅

### 测试场景 3：测试模式结束
1. 等待 2 分钟测试模式结束
2. 尝试打开黑名单应用
3. **预期结果**: 可以正常打开（测试模式已结束）

---

## ⚠️ 重要提示

1. **必须重新安装** - 卸载旧版本后安装新 APK
2. **重新激活设备管理员** - 安装后需要重新激活
3. **确保无障碍服务开启** - 设置 → 无障碍 → 已下载的服务 → 开启"专注锁机监控"
4. **关闭电池优化** - 允许应用在后台运行
5. **查看日志** - 如果仍有问题，使用 `adb logcat | grep -E "Monitor|ScreenReceiver"` 查看日志

---

## 🔍 调试日志关键词

如果问题仍然存在，请查看以下日志：
- `MonitorAccessibility: 🔐 锁机时段` - 锁机状态更新
- `MonitorAccessibility: 🚫 拦截` - 应用拦截
- `ScreenReceiver: 🔓 用户解锁` - 解锁事件
- `ScreenReceiver: 🔄 解锁后立即更新锁机时段` - 解锁后恢复监控
- `MonitorService: 🔄 已通知无障碍服务` - 服务状态同步

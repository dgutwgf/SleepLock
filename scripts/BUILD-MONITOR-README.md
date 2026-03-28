# GitHub Actions 构建监控系统

## 概述

本监控系统会自动检查 SleepLock 项目的 GitHub Actions 构建状态，并在构建失败时尝试自动修复。

## 功能

- ✅ **定时监控**: 每 10 分钟检查一次构建状态
- ✅ **成功通知**: 构建成功时发送通知并停止监控
- ✅ **失败通知**: 构建失败时立即通知
- ✅ **自动修复**: 根据错误类型尝试自动修复
- ✅ **日志记录**: 所有操作记录到日志文件

## 文件结构

```
SleepLock/scripts/
├── check-build-status.sh    # 主监控脚本 (每 10 分钟运行)
├── auto-fix-build.sh        # 自动修复脚本
├── monitor-build.sh         # 长运行监控脚本 (可选)
└── github-build-monitor.service  # systemd 服务配置 (可选)
```

## 监控流程

1. **定时检查**: cron 每 10 分钟执行 `check-build-status.sh`
2. **状态对比**: 对比当前运行与上次记录的状态
3. **新运行检测**: 发现新的构建运行时更新状态
4. **结果处理**:
   - **成功**: 发送通知 → 删除状态文件 → 停止监控
   - **失败**: 发送通知 → 触发自动修复 → 等待下次构建

## 自动修复策略

### 支持的错误类型

| 错误类型 | 检测模式 | 修复策略 |
|---------|---------|---------|
| GRADLE_ERROR | `gradle.*failed` | 清理缓存、检查配置 |
| COMPILATION_ERROR | `compilation.*failed` | 分析错误日志 |
| TEST_FAILURE | `test.*failed` | 跳过测试或修复测试 |
| MEMORY_ERROR | `outofmemory\|heap space` | 增加 JVM 内存 |
| SDK_MISSING | `sdk.*not.*found` | 检查 SDK 配置 |
| PERMISSION_ERROR | `permission\|access.*denied` | 检查权限配置 |

### 修复流程

```
检测到失败
    ↓
获取失败日志
    ↓
分析错误类型
    ↓
执行对应修复策略
    ↓
提交并推送修复
    ↓
等待新的构建
```

## 使用方法

### 启动监控

监控系统已通过 crontab 自动启动：

```bash
# 查看监控状态
crontab -l | grep build-monitor

# 查看监控日志
tail -f /tmp/build-monitor.log
```

### 手动检查

```bash
# 手动执行一次检查
/root/.openclaw/workspace/SleepLock/scripts/check-build-status.sh

# 查看当前监控状态
cat /tmp/sleeplock_build_monitor.json
```

### 手动修复

```bash
# 手动执行自动修复
/root/.openclaw/workspace/SleepLock/scripts/auto-fix-build.sh

# 查看修复日志
tail -f /tmp/build-fix.log
```

### 停止监控

```bash
# 删除状态文件（构建成功后会自动删除）
rm -f /tmp/sleeplock_build_monitor.json

# 从 crontab 移除
crontab -e
# 删除包含 check-build-status.sh 的行
```

## 通知集成

### 飞书通知 (待配置)

编辑 `check-build-status.sh` 中的通知部分，添加飞书机器人 Webhook：

```bash
send_feishu_message() {
    local title="$1"
    local content="$2"
    local color="$3"
    
    curl -X POST "YOUR_FEISHU_WEBHOOK_URL" \
        -H "Content-Type: application/json" \
        -d "{...}"
}
```

## 日志文件

- **监控日志**: `/tmp/build-monitor.log`
- **修复日志**: `/tmp/build-fix.log`
- **状态文件**: `/tmp/sleeplock_build_monitor.json`

## 故障排查

### 监控不工作

```bash
# 1. 检查 crontab 配置
crontab -l

# 2. 检查脚本权限
ls -la /root/.openclaw/workspace/SleepLock/scripts/*.sh

# 3. 手动执行测试
bash -x /root/.openclaw/workspace/SleepLock/scripts/check-build-status.sh
```

### 自动修复不触发

```bash
# 1. 检查状态文件
cat /tmp/sleeplock_build_monitor.json

# 2. 查看修复日志
cat /tmp/build-fix.log

# 3. 检查 GitHub API 访问
gh run list --repo dgutwgf/SleepLock --limit 1
```

### 网络问题

如果遇到 GitHub API 超时：

```bash
# 检查网络连接
ping github.com

# 检查 gh 配置
gh auth status

# 重试构建
cd /root/.openclaw/workspace/SleepLock
git commit --allow-empty -m "ci: trigger rebuild"
git push
```

## 注意事项

1. **API 限流**: GitHub API 有访问限制，监控间隔不宜过短（建议≥5 分钟）
2. **Token 权限**: 确保 `GH_TOKEN` 有访问仓库和 Actions 的权限
3. **自动修复风险**: 自动修复可能会修改代码，建议配合分支保护使用
4. **通知频率**: 失败通知只发送一次，避免重复通知

## 扩展

### 添加新的修复策略

编辑 `auto-fix-build.sh`，在 `analyze_error()` 函数中添加新的错误模式，
并在 `case` 语句中添加对应的修复函数。

### 集成其他通知渠道

修改 `check-build-status.sh` 中的通知函数，支持：
- 微信
- 钉钉
- Slack
- Email

---

**最后更新**: 2026-03-28

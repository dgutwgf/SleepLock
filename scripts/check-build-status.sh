#!/bin/bash
# GitHub Actions 构建监控 - Cron 版本
# 添加到 crontab: */10 * * * * /root/.openclaw/workspace/SleepLock/scripts/check-build-status.sh

REPO="dgutwgf/SleepLock"
WORKFLOW_NAME="Android Build"
STATE_FILE="/tmp/sleeplock_build_monitor.json"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

# 获取最新运行
get_latest_run() {
    gh run list --repo "$REPO" --workflow "$WORKFLOW_NAME" --limit 1 --json status,conclusion,createdAt,databaseId,headBranch --jq '.[0]' 2>/dev/null
}

# 检查是否是第一次运行
if [ ! -f "$STATE_FILE" ]; then
    log "首次运行，初始化状态文件..."
    run_info=$(get_latest_run)
    if [ -n "$run_info" ]; then
        echo "$run_info" | jq '{lastRunId: .databaseId, lastStatus: .status, lastConclusion: .conclusion, notified: false}' > "$STATE_FILE"
    fi
    exit 0
fi

# 读取上次状态
last_run_id=$(jq -r '.lastRunId' "$STATE_FILE")
notified=$(jq -r '.notified' "$STATE_FILE")

# 获取当前最新运行
current_run=$(get_latest_run)
if [ -z "$current_run" ]; then
    log "警告：无法获取运行信息"
    exit 0
fi

current_run_id=$(echo "$current_run" | jq -r '.databaseId')
current_status=$(echo "$current_run" | jq -r '.status')
current_conclusion=$(echo "$current_run" | jq -r '.conclusion')
created_at=$(echo "$current_run" | jq -r '.createdAt')

# 检测新的运行
if [ "$current_run_id" != "$last_run_id" ]; then
    log "检测到新的运行 #$current_run_id (状态：$current_status, 结论：$current_conclusion)"
    
    # 更新状态文件
    echo "$current_run" | jq --arg rid "$current_run_id" --arg st "$current_status" --arg cl "$current_conclusion" \
        '{lastRunId: $rid, lastStatus: $st, lastConclusion: $cl, notified: false}' > "$STATE_FILE"
    notified="false"
fi

# 如果已完成且未通知
if [ "$current_status" = "completed" ] && [ "$notified" = "false" ]; then
    log "构建完成：$current_conclusion"
    
    if [ "$current_conclusion" = "success" ]; then
        log "${GREEN}✅ 构建成功！${NC}"
        # 发送成功通知
        echo "构建成功通知：$REPO - $WORKFLOW_NAME 在 $created_at 完成，运行 ID: $current_run_id"
        # 这里可以集成飞书/微信通知
        
        # 停止监控（删除状态文件）
        rm -f "$STATE_FILE"
        log "监控已停止"
        
    elif [ "$current_conclusion" = "failure" ]; then
        log "${RED}❌ 构建失败！${NC}"
        # 发送失败通知
        echo "构建失败通知：$REPO - $WORKFLOW_NAME 在 $created_at 失败，运行 ID: $current_run_id"
        
        # 调用自动修复脚本
        log "触发自动修复..."
        /root/.openclaw/workspace/SleepLock/scripts/auto-fix-build.sh >> /tmp/build-fix.log 2>&1
        
        # 更新通知状态
        jq '.notified = true' "$STATE_FILE" > "${STATE_FILE}.tmp" && mv "${STATE_FILE}.tmp" "$STATE_FILE"
    fi
fi

# 显示当前状态
log "当前状态：运行#$current_run_id | 状态：$current_status | 结论：$current_conclusion"

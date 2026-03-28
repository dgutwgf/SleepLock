#!/bin/bash
# GitHub Actions 构建监控脚本
# 监控 SleepLock 项目的 Android Build 工作流

REPO="dgutwgf/SleepLock"
WORKFLOW_NAME="Android Build"
LAST_KNOWN_STATUS_FILE="/tmp/github_build_status"
CHECK_INTERVAL=300  # 5 分钟检查一次
MAX_CHECKS=288      # 最多检查 24 小时 (288 * 5 分钟)

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log() {
    echo -e "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

# 获取最新的 workflow run
get_latest_run() {
    gh run list --repo "$REPO" --workflow "$WORKFLOW_NAME" --limit 1 --json status,conclusion,createdAt,databaseId --jq '.[0]'
}

# 发送飞书消息
send_feishu_message() {
    local title="$1"
    local content="$2"
    local color="$3"
    
    # 使用 message tool 的简化版本 - 这里我们通过 feishu 机器人发送
    curl -X POST "https://open.feishu.cn/open-apis/bot/v2/hook/WEBHOOK_URL" \
        -H "Content-Type: application/json" \
        -d "{
            \"msg_type\": \"interactive\",
            \"card\": {
                \"header\": {
                    \"title\": {
                        \"tag\": \"plain_text\",
                        \"content\": \"$title\"
                    },
                    \"template\": \"$color\"
                },
                \"elements\": [
                    {
                        \"tag\": \"markdown\",
                        \"content\": \"$content\"
                    }
                ]
            }
        }" 2>/dev/null
}

# 主监控循环
check_count=0
last_status=""

log "开始监控 $REPO 的 $WORKFLOW_NAME 工作流..."
log "检查间隔：${CHECK_INTERVAL}秒，最大检查次数：$MAX_CHECKS"

while [ $check_count -lt $MAX_CHECKS ]; do
    check_count=$((check_count + 1))
    
    # 获取最新运行状态
    run_info=$(get_latest_run)
    if [ -z "$run_info" ]; then
        log "${YELLOW}警告：无法获取运行信息，跳过本次检查${NC}"
        sleep $CHECK_INTERVAL
        continue
    fi
    
    status=$(echo "$run_info" | jq -r '.status')
    conclusion=$(echo "$run_info" | jq -r '.conclusion')
    created_at=$(echo "$run_info" | jq -r '.createdAt')
    run_id=$(echo "$run_info" | jq -r '.databaseId')
    
    current_status="${status}:${conclusion}"
    
    # 状态变化检测
    if [ "$current_status" != "$last_status" ]; then
        log "检测到状态变化：$current_status (运行 ID: $run_id)"
        last_status="$current_status"
        
        if [ "$status" = "completed" ]; then
            if [ "$conclusion" = "success" ]; then
                log "${GREEN}✓ 构建成功！${NC}"
                send_feishu_message "✅ 构建成功" "项目：$REPO\n工作流：$WORKFLOW_NAME\n状态：成功\n时间：$created_at\n运行 ID：$run_id" "green"
                log "监控完成，退出..."
                exit 0
            elif [ "$conclusion" = "failure" ]; then
                log "${RED}✗ 构建失败！${NC}"
                send_feishu_message "❌ 构建失败" "项目：$REPO\n工作流：$WORKFLOW_NAME\n状态：失败\n时间：$created_at\n运行 ID：$run_id\n\n将尝试自动修复..." "red"
                
                # 这里可以触发自动修复逻辑
                # 例如：获取失败日志，分析错误，尝试修复代码
                
            elif [ "$conclusion" = "cancelled" ]; then
                log "${YELLOW}⚠ 构建已取消${NC}"
            fi
        fi
    fi
    
    sleep $CHECK_INTERVAL
done

log "达到最大检查次数，监控结束"

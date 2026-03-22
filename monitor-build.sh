#!/bin/bash
# GitHub Actions 编译监控脚本
# 用法：./monitor-build.sh [检测间隔秒数] [最大检测次数]

REPO="dgutwgf/SleepLock"
INTERVAL=${1:-120}  # 默认 2 分钟
MAX_CHECKS=${2:-10} # 默认检测 10 次

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

check_build() {
    cd /root/.openclaw/workspace/SleepLock
    local result=$(gh run list --limit 1 2>/dev/null)
    local status=$(echo "$result" | awk '{print $1}')
    local conclusion=$(echo "$result" | awk '{print $2}')
    local run_id=$(echo "$result" | awk '{print $6}')
    
    echo "$status|$conclusion|$run_id"
}

notify() {
    local message="$1"
    log "📢 $message"
    # 这里可以添加飞书通知、邮件通知等
}

# 主监控循环
log "🚀 开始监控 GitHub Actions 编译状态"
log "📦 仓库：$REPO"
log "⏱️ 检测间隔：${INTERVAL}秒"
log "🔄 最大检测次数：$MAX_CHECKS"
log "---"

check_count=0
last_status=""

while [ $check_count -lt $MAX_CHECKS ]; do
    check_count=$((check_count + 1))
    
    # 获取编译状态
    result=$(check_build)
    status=$(echo "$result" | cut -d'|' -f1)
    conclusion=$(echo "$result" | cut -d'|' -f2)
    run_id=$(echo "$result" | cut -d'|' -f3)
    
    log "📊 检测 #$check_count - Run ID: $run_id"
    log "   状态：$status | 结论：$conclusion"
    
    # 检查是否完成
    if [ "$status" = "completed" ]; then
        if [ "$conclusion" = "success" ]; then
            notify "✅ 编译成功！Run ID: $run_id"
            log "---"
            log "🎉 编译完成，可以继续进行下一步开发！"
            exit 0
        else
            # 编译失败，获取详细错误
            log "❌ 编译失败！Run ID: $run_id"
            log "---"
            
            # 获取失败日志
            cd /root/.openclaw/workspace/SleepLock
            error_log=$(gh run view $run_id --log-failed 2>&1 | grep -A3 "error\|Error\|FAILED" | head -20)
            
            if [ -n "$error_log" ]; then
                log "📝 错误详情："
                echo "$error_log" | while read line; do
                    log "   $line"
                done
            fi
            
            log "---"
            log "⚠️ 需要修复编译错误后重新推送"
            exit 1
        fi
    else
        log "⏳ 编译进行中，等待下次检测..."
        log "---"
    fi
    
    # 等待下次检测
    if [ $check_count -lt $MAX_CHECKS ]; then
        sleep $INTERVAL
    fi
done

log "⚠️ 达到最大检测次数，编译仍未完成"
log "💡 可以手动检查：https://github.com/$REPO/actions"
exit 2

#!/bin/bash
# GitHub Actions 自动监控和修复脚本
# 功能：检测编译状态，失败自动修复，成功/失败都报告

REPO="dgutwgf/SleepLock"
INTERVAL=120  # 2 分钟
MAX_CHECKS=15 # 最多检测 30 分钟
LOG_FILE="/tmp/build-monitor.log"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

# 检查推送是否成功
check_push() {
    cd /root/.openclaw/workspace/SleepLock
    local status=$(git status --porcelain)
    if [ -z "$status" ]; then
        log "✅ 代码已推送，工作区干净"
        return 0
    else
        log "⚠️ 工作区有未提交的更改"
        return 1
    fi
}

# 获取编译状态
get_build_status() {
    cd /root/.openclaw/workspace/SleepLock
    local result=$(gh run list --limit 1 2>/dev/null)
    local status=$(echo "$result" | awk '{print $1}')
    local conclusion=$(echo "$result" | awk '{print $2}')
    local run_id=$(echo "$result" | awk '{print $6}')
    echo "$status|$conclusion|$run_id"
}

# 获取错误日志
get_error_log() {
    local run_id=$1
    cd /root/.openclaw/workspace/SleepLock
    gh run view $run_id --log-failed 2>&1 | grep -A5 "error\|Error\|FAILED" | head -30
}

# 分析错误类型
analyze_error() {
    local error_log="$1"
    
    if echo "$error_log" | grep -q "ClassNotFoundException.*GradleWrapperMain"; then
        echo "GRADLE_WRAPPER_ERROR"
    elif echo "$error_log" | grep -q "resource.*not found"; then
        echo "RESOURCE_ERROR"
    elif echo "$error_log" | grep -q "e: file:.*\.kt"; then
        echo "KOTLIN_COMPILE_ERROR"
    elif echo "$error_log" | grep -q "FAILURE: Build failed"; then
        echo "BUILD_FAILED"
    else
        echo "UNKNOWN_ERROR"
    fi
}

# 报告状态
report_status() {
    local status="$1"
    local message="$2"
    
    log "📢 =========================================="
    log "📢 编译状态报告"
    log "📢 =========================================="
    log "📢 状态：$status"
    log "📢 详情：$message"
    log "📢 =========================================="
    
    # 这里可以添加飞书/邮件/短信通知
    # 目前通过日志记录
}

# 主监控循环
main() {
    log "🚀 开始监控 GitHub Actions 编译状态"
    log "📦 仓库：$REPO"
    log "⏱️ 检测间隔：${INTERVAL}秒"
    log "🔄 最大检测次数：$MAX_CHECKS"
    log "📝 日志文件：$LOG_FILE"
    log "=========================================="
    
    # 检查推送
    check_push
    
    local check_count=0
    
    while [ $check_count -lt $MAX_CHECKS ]; do
        check_count=$((check_count + 1))
        
        # 获取编译状态
        local result=$(get_build_status)
        local status=$(echo "$result" | cut -d'|' -f1)
        local conclusion=$(echo "$result" | cut -d'|' -f2)
        local run_id=$(echo "$result" | cut -d'|' -f3)
        
        log "📊 检测 #$check_count - Run ID: $run_id"
        log "   状态：$status | 结论：$conclusion"
        
        # 检查是否完成
        if [ "$status" = "completed" ]; then
            if [ "$conclusion" = "success" ]; then
                report_status "✅ 成功" "编译成功！Run ID: $run_id"
                log "🎉 编译完成，可以继续进行下一步开发！"
                exit 0
            else
                # 编译失败
                log "❌ 编译失败！Run ID: $run_id"
                
                # 获取错误日志
                local error_log=$(get_error_log $run_id)
                local error_type=$(analyze_error "$error_log")
                
                log "📝 错误类型：$error_type"
                log "📝 错误详情："
                echo "$error_log" | while read line; do
                    log "   $line"
                done
                
                report_status "❌ 失败" "编译失败，错误类型：$error_type"
                
                # 这里可以调用自动修复脚本
                log "⚠️ 需要手动修复或运行自动修复脚本"
                
                exit 1
            fi
        else
            log "⏳ 编译进行中，等待下次检测..."
        fi
        
        # 等待下次检测
        if [ $check_count -lt $MAX_CHECKS ]; then
            sleep $INTERVAL
        fi
    done
    
    log "⚠️ 达到最大检测次数，编译仍未完成"
    log "💡 可以手动检查：https://github.com/$REPO/actions"
    exit 2
}

# 运行主函数
main "$@"

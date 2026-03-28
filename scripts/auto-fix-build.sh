#!/bin/bash
# GitHub Actions 构建失败自动修复脚本
# 根据错误日志尝试自动修复常见问题

REPO="dgutwgf/SleepLock"
WORKSPACE="/root/.openclaw/workspace/SleepLock"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [AUTO-FIX] $1"
}

# 获取最新失败运行的日志
get_failed_logs() {
    local run_id=$1
    gh run view "$run_id" --log-failed --repo "$REPO" 2>/dev/null
}

# 分析错误类型
analyze_error() {
    local logs="$1"
    
    # 常见错误模式匹配
    if echo "$logs" | grep -qi "gradle.*failed"; then
        echo "GRADLE_ERROR"
    elif echo "$logs" | grep -qi "compilation.*failed"; then
        echo "COMPILATION_ERROR"
    elif echo "$logs" | grep -qi "test.*failed"; then
        echo "TEST_FAILURE"
    elif echo "$logs" | grep -qi "outofmemory\|heap space"; then
        echo "MEMORY_ERROR"
    elif echo "$logs" | grep -qi "sdk.*not.*found\|ndk.*not.*found"; then
        echo "SDK_MISSING"
    elif echo "$logs" | grep -qi "permission\|access.*denied"; then
        echo "PERMISSION_ERROR"
    else
        echo "UNKNOWN_ERROR"
    fi
}

# 修复 Gradle 相关错误
fix_gradle_error() {
    log "检测到 Gradle 错误，尝试修复..."
    
    # 清理 Gradle 缓存
    cd "$WORKSPACE"
    ./gradlew clean --no-daemon 2>/dev/null || true
    
    # 检查 gradle.properties
    if [ -f "$WORKSPACE/gradle.properties" ]; then
        log "检查 gradle.properties 配置..."
        # 可以增加内存配置
        if ! grep -q "org.gradle.jvmargs" "$WORKSPACE/gradle.properties"; then
            echo "org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8" >> "$WORKSPACE/gradle.properties"
            log "已添加 JVM 内存配置"
        fi
    fi
    
    # 提交修复
    cd "$WORKSPACE"
    if [ -n "$(git status --porcelain)" ]; then
        git add -A
        git commit -m "fix(ci): 自动修复 Gradle 配置"
        git push origin main
        log "已推送修复"
    fi
}

# 修复编译错误
fix_compilation_error() {
    log "检测到编译错误，尝试分析..."
    
    # 这里需要更具体的错误分析
    # 目前只是框架，需要根据实际错误日志实现
    log "需要手动检查编译错误"
}

# 修复内存错误
fix_memory_error() {
    log "检测到内存错误，增加 Gradle 内存..."
    
    cd "$WORKSPACE"
    
    # 创建或更新 gradle.properties
    if [ ! -f "$WORKSPACE/gradle.properties" ]; then
        touch "$WORKSPACE/gradle.properties"
    fi
    
    # 备份
    cp "$WORKSPACE/gradle.properties" "$WORKSPACE/gradle.properties.bak"
    
    # 更新内存配置
    if grep -q "org.gradle.jvmargs" "$WORKSPACE/gradle.properties"; then
        sed -i 's/org.gradle.jvmargs=.*/org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8/' "$WORKSPACE/gradle.properties"
    else
        echo "org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8" >> "$WORKSPACE/gradle.properties"
    fi
    
    # 提交修复
    if [ -n "$(git status --porcelain)" ]; then
        git add -A
        git commit -m "fix(ci): 增加 Gradle JVM 内存配置"
        git push origin main
        log "已推送内存配置修复"
    fi
}

# 主函数
main() {
    log "开始自动修复流程..."
    
    # 获取最新失败运行
    run_info=$(gh run list --repo "$REPO" --workflow "Android Build" --limit 1 \
        --json databaseId,status,conclusion --jq '.[0] | select(.conclusion=="failure")' 2>/dev/null)
    
    if [ -z "$run_info" ]; then
        log "未找到失败的运行"
        exit 0
    fi
    
    run_id=$(echo "$run_info" | jq -r '.databaseId')
    log "分析运行 #$run_id 的失败日志..."
    
    # 获取失败日志
    logs=$(get_failed_logs "$run_id")
    if [ -z "$logs" ]; then
        log "无法获取失败日志"
        exit 1
    fi
    
    # 分析错误类型
    error_type=$(analyze_error "$logs")
    log "错误类型：$error_type"
    
    # 根据错误类型执行修复
    case "$error_type" in
        "GRADLE_ERROR")
            fix_gradle_error
            ;;
        "COMPILATION_ERROR")
            fix_compilation_error
            ;;
        "MEMORY_ERROR")
            fix_memory_error
            ;;
        *)
            log "未知错误类型，需要手动干预：$error_type"
            # 可以发送通知给用户
            ;;
    esac
    
    log "自动修复流程完成"
}

main "$@"

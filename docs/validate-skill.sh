#!/bin/bash
#!/bin/bash
# Skill 包验证脚本

SKILL_DIR="$1"
ERRORS=0
WARNINGS=0

if [ -z "$SKILL_DIR" ]; then
    echo "❌ 用法: $0 <skill-directory>"
    exit 1
fi

echo "🔍 验证技能包: $SKILL_DIR"
echo "===================="

# 1. 检查 SKILL.md 存在
if [ ! -f "$SKILL_DIR/SKILL.md" ]; then
    echo "❌ 错误: SKILL.md 不存在"
    exit 1
fi
echo "✅ SKILL.md 存在"

# 2. 检查必需字段
if ! grep -q "^name:" "$SKILL_DIR/SKILL.md"; then
    echo "❌ 错误: 缺少 name 字段"
    ERRORS=$((ERRORS + 1))
fi

if ! grep -q "^description:" "$SKILL_DIR/SKILL.md"; then
    echo "❌ 错误: 缺少 description 字段"
    ERRORS=$((ERRORS + 1))
fi
echo "✅ 必需字段存在"

# 3. 验证 name 格式
NAME=$(grep "^name:" "$SKILL_DIR/SKILL.md" | awk '{print $2}')
if ! echo "$NAME" | grep -qE "^[a-z0-9]+(-[a-z0-9]+)*$"; then
    echo "⚠️  警告: name '$NAME' 不是 kebab-case 格式"
    WARNINGS=$((WARNINGS + 1))
else
    echo "✅ name 格式正确: $NAME"
fi

# 4. 检查文件大小
MAX_SIZE=$((10 * 1024 * 1024))
LARGE_FILES=$(find "$SKILL_DIR" -type f -size +${MAX_SIZE}c 2>/dev/null)
if [ -n "$LARGE_FILES" ]; then
    echo "❌ 错误: 发现超过 10MB 的文件:"
    echo "$LARGE_FILES"
    ERRORS=$((ERRORS + 1))
else
    echo "✅ 单文件大小符合限制"
fi

# 5. 检查总大小
TOTAL_SIZE=$(du -sb "$SKILL_DIR" 2>/dev/null | cut -f1)
MAX_TOTAL=$((100 * 1024 * 1024))
if [ -n "$TOTAL_SIZE" ] && [ $TOTAL_SIZE -gt $MAX_TOTAL ]; then
    echo "❌ 错误: 总大小超过 100MB"
    ERRORS=$((ERRORS + 1))
else
    if [ -n "$TOTAL_SIZE" ]; then
        TOTAL_MB=$(echo "scale=2; $TOTAL_SIZE / 1024 / 1024" | bc 2>/dev/null || echo "N/A")
        echo "✅ 总大小符合限制: ${TOTAL_MB}MB"
    fi
fi

# 6. 检查文件数量
FILE_COUNT=$(find "$SKILL_DIR" -type f 2>/dev/null | wc -l)
if [ $FILE_COUNT -gt 100 ]; then
    echo "❌ 错误: 文件数量超过 100 (实际: $FILE_COUNT)"
    ERRORS=$((ERRORS + 1))
else
    echo "✅ 文件数量符合限制: $FILE_COUNT"
fi

# 7. 检查文件编码
NON_UTF8=$(find "$SKILL_DIR" -name "*.md" -o -name "*.txt" -name "*.json" 2>/dev/null | xargs file -i 2>/dev/null | grep -v "utf-8")
if [ -n "$NON_UTF8" ]; then
    echo "⚠️  警告: 发现非 UTF-8 编码的文本文件:"
    echo "$NON_UTF8"
    WARNINGS=$((WARNINGS + 1))
else
    echo "✅ 文本文件编码正确"
fi

# 总结
echo "===================="
if [ $ERRORS -eq 0 ] && [ $WARNINGS -eq 0 ]; then
    echo "✅ 验证通过，可以上传"
    exit 0
elif [ $ERRORS -eq 0 ]; then
    echo "⚠️  验证通过，但有 $WARNINGS 个警告"
    exit 0
else
    echo "❌ 验证失败，发现 $ERRORS 个错误，$WARNINGS 个警告"
    exit 1
fi

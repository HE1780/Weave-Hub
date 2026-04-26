# Skill 批量上传实战手册

## 1. 现有技能评估

### 1.1 source-test 目录技能清单

| 技能名称 | 状态 | 问题描述 | 优先级 |
|---------|------|---------|--------|
| khazix-writer | ✅ 可用 | name/description正确，含额外字段 | 低 |
| hv-analysis | ✅ 可用 | 有脚本和参考资料，文件引用正确 | 低 |
| prompts | ✅ 可用 | 结构简单，符合规范 | 低 |

### 1.2 质量分析

#### khazix-writer
- ✅ **优点**: name 使用 kebab-case，description 清晰
- ⚠️ **问题**:
  - 包含 `version`, `author`, `tags` 等可选字段（这些会被存入 `parsed_metadata_json` 但不映射到核心字段）
  - 中英文混合
- 💡 **建议**: 当前格式可以直接上传，无需修改

#### hv-analysis
- ✅ **优点**: 结构完整，包含 scripts/ 和 references/
- ✅ **文件验证**: md_to_pdf.py (287行) 符合 .py 扩展名验证，schema.json 符合 .json 扩展名
- ⚠️ **问题**:
  - 包含额外可选字段
  - SKILL.md 中引用了实际存在的文件（scripts/md_to_pdf.py）
- 💡 **建议**: 当前格式可以直接上传

#### prompts
- ✅ **优点**: 最简单，只有 SKILL.md，无风险
- ⚠️ **问题**: 包含额外可选字段
- 💡 **建议**: 优先上传此技能验证流程

## 2. 验证规则详解

### 2.1 核心验证点（必须满足）

#### 1) SKILL.md 格式
```yaml
---
name: skill-name          # 必需，kebab-case
description: When to use  # 必需，1-2句话
---
```

**验证命令**:
```bash
# 检查 frontmatter 格式
head -5 SKILL.md | grep -E "^---$|name:|description:"

# 提取 name 字段
grep "^name:" SKILL.md | awk '{print $2}'

# 验证 name 格式（kebab-case）
grep "^name:" SKILL.md | awk '{print $2}' | grep -E "^[a-z0-9]+(-[a-z0-9]+)*$"
```

#### 2) 文件类型白名单
允许的扩展名：
- 文档: `.md`, `.txt`, `.json`, `.yaml`, `.yml`, `.html`, `.css`, `.csv`, `.pdf`
- 脚本: `.js`, `.ts`, `.py`, `.sh`, `.rb`, `.go`, `.rs`, `.java`, `.kt`, `.lua`, `.sql`
- 图片: `.png`, `.jpg`, `.jpeg`, `.svg`, `.gif`, `.webp`, `.ico`
- 压缩: `.zip`, `.tar`, `.gz`

**验证命令**:
```bash
# 检查所有文件扩展名
find . -type f | sed 's/.*\.//' | sort | uniq

# 检查是否有不允许的扩展名
find . -type f ! -name "*.md" ! -name "*.txt" ! -name "*.json" ! -name "*.yaml" ! -name "*.yml" ! -name "*.js" ! -name "*.ts" ! -name "*.py" ! -name "*.sh" ! -name "*.png" ! -name "*.jpg" ! -name "*.jpeg" ! -name "*.svg" ! -name "*.zip"
```

#### 3) 文件大小限制
- 单文件: < 10MB
- 总包: < 100MB
- 文件数: < 100

**验证命令**:
```bash
# 检查单文件大小
find . -type f -exec ls -lh {} \; | awk '{print $5, $9}' | sort -h | tail

# 检查总大小
du -sh .

# 检查文件数量
find . -type f | wc -l
```

#### 4) 路径规范化
- 不能以 `/` 或 `../` 开头
- 必须使用相对路径
- 不能包含 `..` 或 Windows 驱动器前缀

**验证命令**:
```bash
# 检查是否有绝对路径
find . -name "/*" -o -name "../*"

# 检查打包后的路径
unzip -l skill.zip | grep -E "^/\.\.|^/"
```

#### 5) 文件内容验证
- 文本文件必须是 UTF-8 编码
- 二进制文件必须与扩展名匹配（PNG 文件头等）

**验证命令**:
```bash
# 检查 UTF-8 编码
file -i *.md */*.md

# 检查 PNG 文件头
file *.png

# 检查是否包含空字节（非文本）
find . -type f -exec grep -l $'\0' {} \;
```

### 2.2 可选字段处理

SKILL.md 中的可选字段会被存储到 `parsed_metadata_json` 中，不会影响上传：

```yaml
---
name: my-skill              # 映射到 skill.slug
description: When to use    # 映射到 skill.summary
version: 1.0.0              # 存入 parsed_metadata_json
author: Team                # 存入 parsed_metadata_json
tags:                       # 存入 parsed_metadata_json
  - tag1
  - tag2
---
```

**建议**: 可以保留这些字段，不影响上传。如果需要使用平台扩展功能，应使用 `x-astron-` 前缀。

## 3. 批量上传流程

### 3.1 自动化验证脚本

创建 `validate-skill.sh`：

```bash
#!/bin/bash
# Skill 包验证脚本

SKILL_DIR="$1"
ERRORS=0
WARNINGS=0

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

# 4. 检查文件扩展名
INVALID_FILES=$(find "$SKILL_DIR" -type f ! -name "*.md" ! -name "*.txt" ! -name "*.json" ! -name "*.yaml" ! -name "*.yml" ! -name "*.js" ! -name "*.ts" ! -name "*.py" ! -name "*.sh" ! -name "*.png" ! -name "*.jpg" ! -name "*.jpeg" ! -name "*.svg" ! -name "*.gif" ! -name "*.webp" ! -name "*.ico" ! -name "*.pdf" ! -name "*.zip" ! -name "*.tar" ! -name "*.gz" 2>/dev/null)
if [ -n "$INVALID_FILES" ]; then
    echo "❌ 错误: 发现不允许的文件类型:"
    echo "$INVALID_FILES"
    ERRORS=$((ERRORS + 1))
else
    echo "✅ 文件类型符合白名单"
fi

# 5. 检查文件大小
MAX_SIZE=$((10 * 1024 * 1024))  # 10MB
LARGE_FILES=$(find "$SKILL_DIR" -type f -size +${MAX_SIZE}c)
if [ -n "$LARGE_FILES" ]; then
    echo "❌ 错误: 发现超过 10MB 的文件:"
    echo "$LARGE_FILES"
    ERRORS=$((ERRORS + 1))
else
    echo "✅ 单文件大小符合限制"
fi

# 6. 检查总大小
TOTAL_SIZE=$(du -sb "$SKILL_DIR" | cut -f1)
MAX_TOTAL=$((100 * 1024 * 1024))  # 100MB
if [ $TOTAL_SIZE -gt $MAX_TOTAL ]; then
    echo "❌ 错误: 总大小超过 100MB"
    ERRORS=$((ERRORS + 1))
else
    TOTAL_MB=$(echo "scale=2; $TOTAL_SIZE / 1024 / 1024" | bc)
    echo "✅ 总大小符合限制: ${TOTAL_MB}MB"
fi

# 7. 检查文件数量
FILE_COUNT=$(find "$SKILL_DIR" -type f | wc -l)
if [ $FILE_COUNT -gt 100 ]; then
    echo "❌ 错误: 文件数量超过 100 (实际: $FILE_COUNT)"
    ERRORS=$((ERRORS + 1))
else
    echo "✅ 文件数量符合限制: $FILE_COUNT"
fi

# 8. 检查文件编码
NON_UTF8=$(find "$SKILL_DIR" -name "*.md" -o -name "*.txt" -o -name "*.json" | xargs file -i | grep -v "utf-8")
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
```

### 3.2 批量打包和验证

```bash
#!/bin/bash
# 批量打包和验证脚本

SOURCE_DIR="/Users/lydoc/projectscoding/skillhub/source-test"
OUTPUT_DIR="/tmp/skill-packages"

mkdir -p "$OUTPUT_DIR"

# 处理每个技能包
for skill_dir in khazix-writer hv-analysis prompts; do
    echo "📦 处理: $skill_dir"
    
    # 验证
    if bash validate-skill.sh "$SOURCE_DIR/$skill_dir"; then
        # 打包
        cd "$SOURCE_DIR"
        zip -r "$OUTPUT_DIR/${skill_dir}.zip" "$skill_dir/"
        echo "✅ $skill_dir 打包成功"
    else
        echo "❌ $skill_dir 验证失败，跳过打包"
    fi
    echo ""
done

echo "📋 打包完成，输出目录: $OUTPUT_DIR"
ls -lh "$OUTPUT_DIR"
```

### 3.3 批量上传 API 调用

```bash
#!/bin/bash
# 批量上传脚本

BASE_URL="http://localhost:8080"
TOKEN="your-auth-token"
NAMESPACE="global"
PACKAGES_DIR="/tmp/skill-packages"

for zip_file in "$PACKAGES_DIR"/*.zip; do
    skill_name=$(basename "$zip_file" .zip)
    echo "📤 上传: $skill_name"
    
    # 上传技能包
    curl -X POST "${BASE_URL}/api/v1/skills/${NAMESPACE}/publish" \
        -H "Authorization: Bearer $TOKEN" \
        -F "file=@$zip_file" \
        -F "visibility=public" \
        -F "confirmWarnings=true"
    
    echo ""
    echo "✅ $skill_name 上传完成"
    echo ""
done
```

## 4. 上传后验证

### 4.1 检查上传结果

```bash
# 查询技能列表
curl http://localhost:8080/api/v1/skills?namespace=global

# 查询特定技能
curl http://localhost:8080/api/v1/skills/global/khazix-writer

# 验证 SKILL.md 解析
curl http://localhost:8080/api/v1/skills/global/hv-analysis/versions/1.0.0 | jq '.data.parsedMetadataJson'
```

### 4.2 下载和验证

```bash
# 下载技能包
curl -O http://localhost:8080/api/v1/skills/global/prompts/download

# 验证下载的包
unzip -l prompts.zip
```

## 5. 常见问题排查

### 5.1 上传失败

**症状**: 上传时返回错误

**排查步骤**:
1. 检查后端日志: `tail -f server/skillhub-app/logs/skillhub.log`
2. 验证技能包格式（使用验证脚本）
3. 检查命名空间权限
4. 确认文件大小限制

### 5.2 SKILL.md 解析失败

**症状**: `parsedMetadataJson` 为空或错误

**排查步骤**:
1. 检查 frontmatter 格式（必须是 `---` 包围）
2. 确认 `name` 和 `description` 字段存在
3. 验证 YAML 语法（不能有多余的空格或特殊字符）

### 5.3 文件验证失败

**症状**: "File content does not match extension"

**排查步骤**:
1. 检查文件实际类型: `file filename.png`
2. 确认扩展名与内容匹配
3. 重新导出或转换文件格式

## 6. 推荐上传顺序

1. **prompts** (最简单)
   - 只包含 SKILL.md
   - 验证基本流程

2. **khazix-writer** (中等)
   - 包含参考资料
   - 验证文件引用

3. **hv-analysis** (复杂)
   - 包含脚本和 JSON 文件
   - 验证文件类型和大小限制

## 7. 持续改进

### 7.1 质量检查清单

每次上传前确认：
- [ ] SKILL.md 在根目录
- [ ] name 和 description 字段存在
- [ ] name 使用 kebab-case
- [ ] 所有文件在白名单中
- [ ] 单文件 < 10MB，总包 < 100MB
- [ ] 文件数 < 100
- [ ] 文本文件使用 UTF-8 编码
- [ ] 图片文件内容与扩展名匹配

### 7.2 版本管理建议

- 使用语义化版本号（1.0.0, 1.1.0, 2.0.0）
- 每次修改后递增版本号
- 保留旧版本供用户选择

## 8. 附录

### 8.1 完整的文件类型白名单

```
文档: .md, .txt, .json, .yaml, .yml, .html, .css, .csv, .pdf
配置: .toml, .xml, .xsd, .xsl, .dtd, .ini, .cfg, .env
脚本: .js, .cjs, .mjs, .ts, .py, .sh, .rb, .go, .rs, .java, .kt, .lua, .sql, .r, .bat, .ps1, .zsh, .bash
图片: .png, .jpg, .jpeg, .svg, .gif, .webp, .ico
办公: .doc, .xls, .ppt, .docx, .xlsx, .pptx
压缩: .zip, .tar, .gz, .tar.gz, .tgz, .rar, .7z
```

### 8.2 API 端点参考

```
POST /api/v1/skills/{namespace}/publish          # 上传技能包
GET  /api/v1/skills                              # 查询技能列表
GET  /api/v1/skills/{namespace}/{slug}           # 查询技能详情
GET  /api/v1/skills/{namespace}/{slug}/versions  # 查询版本列表
GET  /api/v1/skills/{namespace}/{slug}/versions/{version}  # 查询版本详情
GET  /api/v1/skills/{namespace}/{slug}/download  # 下载技能包
POST /api/v1/skills/{namespace}/{slug}/versions/{version}/publish  # 发布版本
```

### 8.3 错误码参考

| 错误码 | 含义 | 解决方法 |
|--------|------|---------|
| 400 | 请求参数错误 | 检查 SKILL.md 格式 |
| 403 | 权限不足 | 确认命名空间权限 |
| 413 | 文件过大 | 减小文件或总包大小 |
| 422 | 验证失败 | 检查文件类型和内容 |

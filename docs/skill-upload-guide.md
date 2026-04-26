# Skill 规范化上传操作手册

## 1. 概述

本文档说明如何将本地 skill 文档进行规范化处理并上传到 SkillHub 平台。

## 2. SKILL.md 格式要求

### 2.1 必需字段

每个 skill 必须包含 `SKILL.md` 文件，格式如下：

```markdown
---
name: my-skill              # 必需，kebab-case 格式
description: When to use    # 必需，1-2 句话说明使用场景
---

# Markdown 正文

技能的详细指令内容...
```

**字段说明**：
- `name`: 技能名称，必须使用 kebab-case 格式（小写字母、数字、连字符）
- `description`: 简短描述，说明何时使用该技能（1-2 句话）

### 2.2 可选扩展字段

```yaml
---
name: my-skill
description: When to use
x-astron-category: code-review    # 分类
x-astron-runtime: claude-code     # 运行时环境
x-astron-min-version: "1.0"       # 最低版本要求
---
```

## 3. 技能包目录结构

```
my-skill/
├── SKILL.md              # 主入口文件（必需）
├── references/           # 参考资料（可选）
│   ├── doc1.md
│   └── doc2.pdf
├── scripts/              # 脚本文件（可选）
│   └── setup.sh
└── assets/               # 静态资源（可选）
    ├── image.png
    └── logo.svg
```

**目录规则**：
- 根目录必须包含 `SKILL.md`
- 支持的子目录：`references/`, `scripts/`, `assets/`
- 路径必须使用相对路径，不能以 `/` 或 `../` 开头

## 4. 文件类型限制

### 4.1 允许的文件扩展名

**文档类**：
- `.md`, `.txt`, `.json`, `.yaml`, `.yml`, `.html`, `.css`, `.csv`, `.pdf`

**配置和脚本**：
- `.toml`, `.xml`, `.ini`, `.cfg`, `.env`
- `.js`, `.cjs`, `.mjs`, `.ts`, `.py`, `.sh`, `.rb`, `.go`, `.rs`, `.java`, `.kt`, `.lua`, `.sql`, `.r`
- `.bat`, `.ps1`, `.zsh`, `.bash`

**图片类**：
- `.png`, `.jpg`, `.jpeg`, `.svg`, `.gif`, `.webp`, `.ico`

**办公文档**：
- `.doc`, `.xls`, `.ppt`, `.docx`, `.xlsx`, `.pptx`

**压缩包**：
- `.zip`, `.tar`, `.gz`, `.tar.gz`, `.tgz`, `.rar`, `.7z`

### 4.2 文件大小和数量限制

- 单文件大小：最大 10MB
- 总包大小：最大 100MB
- 文件数量：最多 100 个

## 5. 内容验证规则

### 5.1 文件内容与扩展名匹配

平台会验证文件内容是否与扩展名匹配：

| 扩展名 | 验证规则 |
|--------|----------|
| `.png` | 必须以 PNG 文件头开头 (`0x89 0x50 0x4e 0x47 ...`) |
| `.jpg` / `.jpeg` | 必须以 JPEG 文件头开头 (`0xff 0xd8 0xff`) |
| `.svg` | 必须是 UTF-8 文本且包含 `<svg` 标签 |
| `.gif` | 必须以 `GIF8` 开头 |
| `.webp` | 必须包含 `RIFF....WEBP` 标识 |
| `.pdf` | 必须以 `%PDF` 开头 |
| 文本文件 | 必须是有效的 UTF-8 编码，不包含空字节 |

### 5.2 路径规范化规则

- 路径分隔符统一使用 `/`（Windows 的 `\` 会自动转换）
- 不能以 `/` 或 `\` 开头
- 不能包含 `..`（防止路径穿越攻击）
- 不能包含 Windows 驱动器前缀（如 `C:`）
- 路径必须已规范化（如 `a//b` 会被拒绝）

**示例**：
- ✅ `SKILL.md`
- ✅ `references/doc.md`
- ✅ `scripts/setup.sh`
- ❌ `/absolute/path.md`
- ❌ `../escape.md`
- ❌ `C:\windows.md`

## 6. 上传流程

### 6.1 准备技能包

1. **创建目录结构**
   ```bash
   mkdir my-skill
   cd my-skill
   touch SKILL.md
   ```

2. **编写 SKILL.md**
   ```markdown
   ---
   name: code-review
   description: Use when reviewing code for quality, security, and best practices
   ---

   # Code Review

   Review the code for:
   1. Security vulnerabilities
   2. Performance issues
   3. Code quality
   ...
   ```

3. **添加参考资料（可选）**
   ```bash
   mkdir references
   cp ~/docs/checklist.md references/
   ```

4. **添加脚本（可选）**
   ```bash
   mkdir scripts
   echo '#!/bin/bash' > scripts/setup.sh
   ```

### 6.2 打包上传

**方式一：通过 Web UI 上传**

1. 访问 `/skills/new` 页面
2. 填写技能基本信息：
   - Namespace: 选择或创建命名空间
   - Slug: 会自动从 SKILL.md 的 `name` 字段读取
   - Version: 输入版本号（如 1.0.0）
3. 拖拽或选择技能包目录/ZIP文件
4. 点击"发布"按钮

**方式二：通过 API 上传**

```bash
# 1. 创建技能（首次发布）
curl -X POST https://skillhub.example.com/api/v1/skills \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "namespaceSlug": "my-team",
    "slug": "my-skill",
    "summary": "My awesome skill"
  }'

# 2. 上传技能包版本
curl -X POST https://skillhub.example.com/api/v1/skills/my-team/my-skill/versions \
  -H "Authorization: Bearer $TOKEN" \
  -F "version=1.0.0" \
  -F "changelog=Initial release" \
  -F "file=@my-skill.zip"

# 3. 发布版本
curl -X POST https://skillhub.example.com/api/v1/skills/my-team/my-skill/versions/1.0.0/publish \
  -H "Authorization: Bearer $TOKEN"
```

**方式三：使用 CLI（如果已安装）**

```bash
# 打包技能
cd my-skill
zip -r ../my-skill.zip .

# 上传并发布
skillhub publish my-skill.zip \
  --namespace my-team \
  --version 1.0.0
```

### 6.3 验证检查清单

上传前请确认：

- [ ] `SKILL.md` 存在于根目录
- [ ] `SKILL.md` 包含必需的 `name` 和 `description` 字段
- [ ] `name` 使用 kebab-case 格式
- [ ] 所有文件扩展名在白名单中
- [ ] 单文件大小 < 10MB
- [ ] 总包大小 < 100MB
- [ ] 文件数量 < 100 个
- [ ] 所有路径使用相对路径
- [ ] 图片文件内容与扩展名匹配
- [ ] 文本文件使用 UTF-8 编码

## 7. 常见错误及解决方法

### 错误 1：`Package entry path must be relative`

**原因**：路径以 `/` 开头或包含绝对路径

**解决**：使用相对路径，如 `references/doc.md` 而非 `/absolute/path/doc.md`

### 错误 2：`File content does not match extension`

**原因**：文件内容与扩展名不匹配（如将 PNG 文件重命名为 `.jpg`）

**解决**：确保文件扩展名与实际内容类型一致

### 错误 3：`Package entry path escapes package root`

**原因**：路径包含 `..`

**解决**：移除路径中的 `..`，使用正确的相对路径

### 错误 4：`File content does not match extension: SKILL.md`

**原因**：SKILL.md 不是有效的 UTF-8 文本

**解决**：确保文件使用 UTF-8 编码保存，不包含 BOM 或控制字符

### 错误 5：`Package entry path must be normalized`

**原因**：路径包含 `./` 或 `//` 等非规范化表示

**解决**：使用规范化路径（`a/b/c.md` 而非 `./a//b/c.md`）

## 8. 版本管理

### 8.1 版本号规范

推荐使用语义化版本号（SemVer）：
- 格式：`MAJOR.MINOR.PATCH`
- 示例：`1.0.0`, `1.2.3`, `2.0.0`

### 8.2 更新已有技能

1. 修改 `SKILL.md` 或其他文件
2. 更新版本号（如从 `1.0.0` 到 `1.1.0`）
3. 重新打包上传
4. 系统会自动创建新版本，旧版本保留

## 9. 命名空间和坐标

### 9.1 坐标格式

SkillHub 使用以下坐标格式：
```
@{namespace}/{skill-slug}
```

示例：
- `@global/code-review`
- `@my-team/database-migration`

### 9.2 本地安装目录

安装后的本地目录名使用 `skill-slug`（不含 namespace）：
```
~/.claude/skills/code-review/
```

## 10. 客户端兼容性

SkillHub 技能与以下客户端兼容：
- Claude Code (`.claude/skills/`)
- OpenSkills (`.agent/skills/`)
- 其他支持 SKILL.md 格式的客户端

**注意**：不同 namespace 下的同名 skill 安装到本地时会产生目录冲突，CLI 应在安装时检测并提示。

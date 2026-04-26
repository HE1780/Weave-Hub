# SkillHub 首页重新设计方案

## 🎯 设计目标

**核心目标**：设计一个能同时展示Skill和Agent双重价值的首页，让用户清楚理解：
- 🔧 **Skill频道**：单一功能的能力单元
- 🤖 **Agent频道**：多技能组合的智能配置
- 🔗 **协同价值**：Skill + Agent = 完整AI解决方案

## 📐 首页结构设计

### Hero区域 - 双价值主张
```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│              SkillHub - AI Agent配置管理平台                    │
│                                                             │
│     🤖 为Claude等AI Agent提供可组合的技能和配置管理                │
│                                                             │
│   ┌─────────────┐              ┌─────────────┐              │
│   │  🔧 Skills  │              │ 🤖 Agents   │              │
│   │  能力单元   │              │  智能配置   │              │
│   └─────────────┘              └─────────────┘              │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 统计数据 - 分层展示
```
┌─────────────────────────────────────────────────────────────┐
│  平台数据                                                  │
│  • 1000+ 技能包 (Skills)                                     │
│  • 200+ 预配置Agent (Agents)                                │
│  • 50K+ 下载使用                                             │
│  • 500+ 企业团队                                             │
└─────────────────────────────────────────────────────────────┘
```

### 功能特性 - 双轨展示
```
┌─────────────────────────────────────────────────────────────┐
│  Skills频道能力                                              │
│  ┌──────────┬──────────┬──────────┐                        │
│  │ 提示词模板 │ 数据分析  │ 代码生成  │                        │
│  │ 知识库   │ 文件处理  │ 图像生成  │                        │
│  │ 单一功能  │ 可复用    │ 可组合    │                        │
│  └──────────┴──────────┴──────────┘                        │
├─────────────────────────────────────────────────────────────┤
│  Agents频道能力                                              │
│  ┌──────────┬──────────┬──────────┐                        │
│  │ 研究助理 │ 代码审查  │ 内容创作  │                        │
│  │ 自动客服 │ 数据分析  │ 自动化流  │                        │
│  │ 多技能组合 │ 工作流    │ 端到端    │                        │
│  └──────────┴──────────┴──────────┘                        │
└─────────────────────────────────────────────────────────────┘
```

### 快速开始 - 并行引导
```
┌─────────────────────────────────────────────────────────────┐
│  快速开始 - 选择你的使用方式                                 │
│                                                             │
│  ┌─────────────────────┬─────────────────────┐           │
│  │   我是Agent使用者    │   我是技能开发者     │           │
│  │                     │                     │           │
│  │ [浏览Agent模板]      │ [发布技能包]         │           │
  │ [创建Agent配置]     │ [技能包文档]         │           │
│  │                     │                     │           │
│  │ 现成的解决方案      │ 可复用的能力组件     │           │
│  └─────────────────────┴─────────────────────┘           │
└─────────────────────────────────────────────────────────────┘
```

### 内容展示 - 双区展示
```
┌─────────────────────────────────────────────────────────────┐
│  热门Skills                                              │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐        │
│  │ 提示词  │ │数据分析 │ │写作    │ │代码    │        │
│  │ 模板   │ │工具    │ │助手    │ │生成    │        │
│  └────────┘ └────────┘ └────────┘ └────────┘        │
├─────────────────────────────────────────────────────────────┤
│  热门Agents                                              │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐        │
│  │研究    │ │代码审查 │ │内容    │ │自动    │        │
│  │助理    │ │助手    │ │创作    │ │化流程  │        │
│  └────────┘ └────────┘ └────────└ └────────┘        │
└─────────────────────────────────────────────────────────────┘
```

## 🎨 具体页面设计

### 1. Hero区域重新设计
```tsx
// 主标题
<h1>SkillHub - AI Agent配置管理平台</h1>

// 副标题
<p>🤖 为Claude等AI Agent提供可组合的技能和配置管理</p>

// 双入口按钮组
<div className="flex gap-4">
  <Link to="/skills" className="btn-primary">
    🔧 探索技能包
  </Link>
  <Link to="/agents" className="btn-secondary">
    🤖 浏览Agent配置
  </Link>
  <Link to="/dashboard/publish" className="btn-outline">
    📤 发布资源
  </Link>
</div>
```

### 2. 特性展示区重新设计
```tsx
const features = [
  // Skills频道特性
  {
    category: "Skills频道",
    icon: <PackageOpen className="w-6 h-6" />,
    title: "丰富的技能包",
    description: "提示词模板、数据分析、代码生成等多种能力单元"
  },
  {
    category: "Skills频道",
    icon: <GitBranch className="w-6 h-6" />,
    title: "版本管理",
    description: "技能包的版本控制、回退和升级机制"
  },

  // Agents频道特性
  {
    category: "Agents频道",
    icon: <Settings className="w-6 h-6" />,
    title: "Agent配置",
    description: "通过JSON配置多技能工作流，创建智能Agent"
  },
  {
    category: "Agents频道",
    icon: <Bot className="w-6 h-6" />,
    title: "一键部署",
    description: "选择Agent配置，一键部署到你的AI环境"
  },

  // 通用特性
  {
    category: "平台特性",
    icon: <Shield className="w-6 h-6" />,
    title: "安全可控",
    description: "企业级权限控制、审计日志、私有部署"
  },
  {
    category: "平台特性",
    icon: <Users className="w-6 h-6" />,
    title: "团队协作",
    description: "多租户支持、技能分享、团队配置库"
  }
]
```

### 3. 快速开始区域重新设计
```tsx
// 双Tab快速开始
const quickStartTabs = [
  {
    id: 'agent-user',
    label: '我是Agent使用者',
    description: '快速找到现成的Agent配置',
    icon: <Bot className="w-5 h-5" />,
    steps: [
      '浏览Agent模板',
      '选择合适的Agent配置',
      '一键部署到环境',
      '开始使用'
    ]
  },
  {
    id: 'skill-developer',
    label: '我是技能开发者',
    description: '发布可复用的技能包',
    icon: <Code className="w-5 h-5" />,
    steps: [
      '创建技能包',
      '编写SKILL.md',
      '上传到平台',
      '获得使用反馈'
    ]
  },
  {
    id: 'agent-architect',
    label: '我是Agent架构师',
    description: '设计Agent工作流配置',
    icon: <Workflow className="w-5 h-5" />,
    steps: [
      '组合多个技能包',
      '设计工作流程',
      '配置Agent行为',
      '发布Agent配置'
    ]
  }
]
```

### 4. 示例展示区域
```tsx
// 分栏展示Skills和Agents
<div className="grid md:grid-cols-2 gap-8">
  // Skills展示
  <section>
    <h3>🔧 热门技能包</h3>
    <div className="skill-grid">
      <SkillCard skill="prompts" />
      <SkillCard skill="data-analyzer" />
      <SkillCard skill="code-gen" />
    </div>
    <Link to="/skills">查看所有技能 →</Link>
  </section>

  // Agents展示
  <section>
    <h3>🤖 热门Agent配置</h3>
    <div className="agent-grid">
      <AgentCard agent="research-assistant" />
      <AgentCard agent="code-reviewer" />
      <AgentCard agent="content-creator" />
    </div>
    <Link to="/agents">查看所有Agent →</Link>
  </section>
</div>
```

### 5. 统一搜索区
```tsx
// 统一的搜索和过滤
<div className="search-section">
  <Tabs defaultValue="all">
    <TabsList>
      <TabsTrigger value="all">全部资源</TabsTrigger>
      <TabsTrigger value="skills">技能包</TabsTrigger>
      <TabsTrigger value="agents">Agent配置</TabsTrigger>
    </TabsList>

    <TabsContent value="all">
      <SearchBar placeholder="搜索技能包和Agent配置..." />
      <CombinedResults />
    </TabsContent>

    <TabsContent value="skills">
      <SearchBar placeholder="搜索技能包..." />
      <SkillResults />
    </TabsContent>

    <TabsContent value="agents">
      <SearchBar placeholder="搜索Agent配置..." />
      <AgentResults />
    </TabsContent>
  </Tabs>
</div>
```

## 🎯 核心设计原则

### 1. 清晰的区分
- **视觉区分**：不同的图标、颜色、卡片样式
- **功能区分**：明确的解释各自的用途
- **用户路径**：不同的使用流程和入口

### 2. 统一的体验
- **一致的设计语言**：相似的卡片、按钮、交互模式
- **统一的质量标准**：相同的审核、版本管理、搜索机制
- **共享的基础设施**：用户系统、权限控制、API设计

### 3. 协同的价值
- **Skill作为基础**：Agent依赖Skill，展示组合价值
- **Agent作为应用**：展示实际使用场景和效果
- **生态完整性**：从开发到使用的完整闭环

## 📱 响应式布局

### 桌面端
- Hero区域：Skills和Agents并排展示
- 特性介绍：分两列展示（左边Skills，右边Agents）
- 示例展示：Skills和Agents各占一半

### 移动端
- Hero区域：Skills和Agents上下排列
- 特性介绍：垂直列表，交替展示
- 示例展示：Tabs切换Skills/Agents

## 🎨 视觉设计建议

### 配色方案
- **Skills频道主色**：蓝色系 (代表工具、能力)
- **Agents频道主色**：紫色系 (代表智能、自动化)
- **平台品牌色**：保持现有的渐变色

### 图标设计
- **Skills图标**：🔧 工具、⚙️ 齿轮
- **Agents图标**：🤖 机器人、🧠 智能、⚡ 闪电
- **组合图标**：🔧+🤖 (技能+智能体)

### 文案建议
- **Skills标签语**："能力单元"、"可复用技能"、"工具组件"
- **Agents标签语**："智能配置"、"自动化方案"、"端到端解决方案"

## 📝 关键文案

### Hero区域
- 主标题：`SkillHub - AI Agent配置管理平台`
- 副标题：`🤖 为Claude等AI Agent提供可组合的技能和配置管理`
- CTA按钮：`探索技能包`、`浏览Agent配置`、`发布资源`

### 价值说明
- Skills："**单一功能的强大工具**，为AI Agent提供专业能力"
- Agents："**多技能的智能配置**，开箱即用的AI解决方案"
- 协同："**Skills + Agents = 完整的AI能力生态系统**"

这个首页设计清晰地区分了两个频道的价值，同时展示了它们如何协同工作，为用户提供完整的AI配置管理体验。

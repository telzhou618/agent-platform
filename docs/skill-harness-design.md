# 技能（Skill）管理 + HarnessAgent 改造设计

> 状态：待确认。本文档仅为设计方案，确认后再动手改代码。
> 依据：官方文档 [技能](https://java.agentscope.io/v2/zh/docs/harness/skill.html)、[工作区](https://java.agentscope.io/v2/zh/docs/harness/workspace.html)，以及本地对 `agentscope-harness-2.0.0.jar` / `agentscope-core-2.0.0.jar` 的反编译核实。

## 1. 目标

1. 新增「技能管理」菜单：管理**技能仓库**（Skill Repository，即技能来源）的 CRUD，支持 Git / MySQL / Classpath 三种来源，表单按类型切换字段、可扩展。
2. `ReActAgent` 改造为 `HarnessAgent`，智能体创建/编辑时可多选关联技能仓库，运行时动态生效。
3. 配置全部落库，重启自动加载；沿用现有用户级数据权限模式。

## 2. 关键调研结论（已核实）

- `io.agentscope:agentscope-harness:2.0.0` 已在 Maven Central 发布，依赖 `agentscope-core:2.0.0`（与项目现有版本对齐，无冲突）。
- 技能仓库扩展构件 2.0.0 均已发布：
  - `agentscope-extensions-skill-git-repository`（Git 来源）
  - `agentscope-extensions-skill-mysql-repository`（MySQL 来源）
  - Classpath 来源 `ClasspathSkillRepository` 在 core 里，无需额外依赖。
- `HarnessAgent.Builder` 已核实存在的方法（javap）：
  `name/sysPrompt/model/toolkit/skillRepository(可重复)/skillRepositories(List)/workspace(Path)/skillFilter/enableSkills/disableSkills/disableDynamicSkills/disableWorkspaceContext/disableMemoryHooks/disableMemoryTools/disableSubagents/disableToolsConfig/disableShellTool/disableFilesystemTools/build()` 等。
- `HarnessAgent` 实例方法与现有调用链兼容：`streamEvents(Msg, RuntimeContext)`、`setPermissionMode(...)` 均存在，`ChatService` 基本无需改事件处理逻辑。
- **注意点 1**：`HarnessAgent.Builder` **没有** `knowledge()/ragMode()`（RAG 是 ReActAgent 的能力）。保留了迁移通道 `HarnessAgent.Builder.fromAgent(ReActAgent)`——先按现有逻辑构建带知识库的 ReActAgent，再包装成 HarnessAgent。知识库能否完整保留需编码时验证（见 §8 风险）。
- **注意点 2**：HarnessAgent 默认开启一批子系统（工作区上下文注入、记忆钩子、子 agent、tools.json 读取等）。为保持平台现有行为（提示词由 DB 管、无文件注入），构建时需显式关闭不用的子系统。

## 3. 数据库设计

### 3.1 新表 `skill_repo`（技能仓库）

仿 `knowledge_base` 的 `type + config JSON` 可扩展模式：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | |
| name | varchar(128) | 仓库名称 |
| type | varchar(32) | `git` / `mysql` / `classpath` |
| config | text | 类型相关 JSON（见下） |
| remark | varchar(512) | 备注 |
| user_id / create_time / update_time / deleted | | 与其他业务表一致 |

各类型 config 字段：

- `git`：`url`(必填)、`autoSync`(bool，默认 true，对应 `GitSkillRepository(url, autoSync)`)
- `mysql`：`databaseName`(必填)、`skillsTableName`(默认 `skills`)、`writeable`(bool，默认 false)；数据源直接用平台自己的 `DataSource`（管理端就是本平台，不接外部库，保持简单）
- `classpath`：`directory`(必填，默认 `skills`，对应 `src/main/resources/skills/`)

### 3.2 `agent_info` 加列

- `skill_repos` varchar(1024)：技能仓库 ID 的 JSON 数组，与 `mcp_servers` / `knowledge_bases` 同模式。

### 3.3 权限

`UserTenantHandler.TENANT_TABLES` 增加 `skill_repo`，普通用户只见自己的，管理员见全部——零额外代码。

## 4. 后端改造

### 4.1 新增（仿知识库全链路）

- `entity/SkillRepo.java`：实体 + `TYPE_GIT/TYPE_MYSQL/TYPE_CLASSPATH` 常量。
- `mapper/SkillRepoMapper.java`
- `service/SkillRepoService.java`：`pageSkillRepos / saveSkillRepo / deleteSkillRepo`，保存/删除打 `@OperationLog`，并调用 `AgentRegistry.onSkillRepoChanged / onSkillRepoDeleted` 级联重建引用实例。
- `system/agent/SkillRepoFactory.java`：`fromConfig(SkillRepo) -> AgentSkillRepository`，`switch(type)` 分派构建（仿 `KnowledgeFactory`）：
  - git → `new GitSkillRepository(url, autoSync)`
  - mysql → `MysqlSkillRepository.builder(dataSource).databaseName(...).skillsTableName(...).createIfNotExist(true).writeable(...).build()`
  - classpath → `new ClasspathSkillRepository(directory)`

### 4.2 `AgentRegistry` 改造（核心）

- `build()` 改为：
  1. 按现有逻辑构建 `Toolkit`（系统工具 + MCP）；
  2. 按现有逻辑构建带 `knowledge()/ragMode(AGENTIC)` 的 `ReActAgent`；
  3. `HarnessAgent.builder().fromAgent(reActAgent)` 接管名称/提示词/模型/工具箱；
  4. 逐个 `skillRepository(skillRepoFactory.fromConfig(repo))`，单个构建失败记日志跳过（与知识库同策略）；
  5. `workspace(Paths.get("workspaces/agent-" + id))`：每智能体独立工作区目录（技能脚本执行、`.skills-cache` 物化都需要）；
  6. 显式关闭不用的子系统，保持现有行为：
     - `disableWorkspaceContext()`：不注入 AGENTS.md/MEMORY.md（提示词由 DB 管）
     - `disableMemoryHooks()` + `disableMemoryTools()`：不开长期记忆
     - `disableSubagents()`、`disableToolsConfig()`：不读 workspace 文件配置
  7. `build()` 产出 `HarnessAgent`。
- `find()` 返回类型 `ReActAgent` → `HarnessAgent`；`BEAN_PREFIX` 改名 `harnessAgent#`。
- 快照 `BuildSnapshot` 增加 `List<SkillRepo> skillRepos`；`register(...)` 签名加技能仓库列表；新增 `onSkillRepoChanged/onSkillRepoDeleted`（复制知识库的级联方法）。
- `AgentInfo` 实体加 `skillRepos` 字段；`AgentInfoService.skillReposOf()` 仿 `knowledgeBasesOf()`。

### 4.3 `ChatService` 微调

- 字段类型 `ReActAgent` → `HarnessAgent`；`streamEvents` / `setPermissionMode` API 兼容，事件处理（六类 Delta 事件转 `ChatChunk`）预期不变。
- 默认兜底智能体（`AgentScopeConfig` 里的 defaultAgent）同样改为 HarnessAgent 或保留 ReActAgent——两者 `streamEvents` 签名一致，取公共父类型持有即可，编码时定。

### 4.4 `AgentBootstrap`

- 只多查一张 `skill_repo` 表并传入 `register(...)`，结构不变。

## 5. 前端改造

- **新增 `SkillRepoView`**（仿 `KnowledgeView`）：toolbar + Grid（类型徽标）+ PaginationBar + Dialog（固定宽度，类型切换只切 `setVisible` 不改弹框大小）+ ConfirmDialog；类型专属字段不挂 Binder，`buildConfigJson()` 按类型校验必填（git:url 必填 + URL 校验；mysql:databaseName 必填；classpath:directory 必填）。
- **`AgentView`**：表单增加 `MultiSelectComboBox<SkillRepo> skillRepos`，与 MCP/知识库同样的 converter（排序后存 ID JSON 数组）。
- **`MainLayout`**：菜单在「知识库管理」后加「技能管理」一行。
- 操作日志菜单无需动（注解在 Service 层）。

## 6. 依赖变更（pom.xml）

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-harness</artifactId>
    <version>${agentscope.version}</version>
</dependency>
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-skill-git-repository</artifactId>
    <version>${agentscope.version}</version>
</dependency>
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-skill-mysql-repository</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

（harness 会带入 jackson-dataformat-yaml、commons-compress、sqlite-jdbc，均为其自身需要，无需排除。）

## 7. 落地顺序

1. pom + sql 脚本 + 本地建表
2. SkillRepo 实体/Mapper/Service/Factory + 租户表注册
3. AgentRegistry 改造为 HarnessAgent（含 skillRepository 挂载、子系统关闭）
4. ChatService / AgentScopeConfig / AgentBootstrap 适配
5. SkillRepoView + AgentView 加字段 + 菜单
6. 编译 + 启动验证（界面用户自验）

## 8. 风险与验证点

| 风险 | 应对 |
| --- | --- |
| `fromAgent(ReActAgent)` 是否完整保留知识库/RAG 配置 | 编码时先用单测/启动日志验证；若不保留，备选方案是把 RAG 检索工具直接注册进 Toolkit 再传给 HarnessAgent（retrieve_knowledge 本质是工具调用） |
| HarnessAgent 默认开启的子系统改变现有对话行为（额外 prompt 注入、记忆写入磁盘） | 构建时显式 disable（§4.2 第 6 点），启动后对比一次对话输出 |
| workspace 目录落在项目根下产生运行期文件 | `workspaces/` 加入 `.gitignore` |
| Git 仓库不可达拖慢启动 | `GitSkillRepository` 构建失败按现有策略记日志跳过；必要时 git 类型默认 `autoSync=false` 启动后手动同步 |
| HarnessAgent 实现 `AutoCloseable`，unregister 时应调用 `close()` 释放资源 | `unregister` 里对实例调 `close()`（包 try-catch） |

## 9. 明确不做

- 自学习闭环（`enableSkillManageTool` / 审核闸门 / curator）：本期不开，后续可独立加。
- 沙箱 / 远端共享文件系统模式：本期用默认本机模式。
- 技能的在线编写（SKILL.md 内容编辑）：本期技能内容只来自外部仓库（Git/MySQL/classpath），平台只管理"来源"，不做技能内容编辑器。

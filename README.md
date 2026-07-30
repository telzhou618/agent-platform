# agent-platform

基于 **AgentScope Java 2.0 + Spring Boot 3 + Vaadin 24 + MySQL + MyBatis-Plus** 的一站式智能体管理平台：模型、智能体、工具、MCP 服务、知识库、技能仓库统一配置，开箱即用的流式对话与数据看板。

![数据看板预览](imgs/home.png)

## 功能特性

- **首页数据看板**（`/`）：渐变 Hero + 资源统计、近 7 日对话趋势、对话数据概览、活跃智能体排行、模型可用环图、Token 消耗摘要、快捷入口
- **Token 监控**（`/token-monitor`）：每次模型调用的 token 消耗实时埋点——指标卡（今日/累计 token、调用次数、缓存命中率、平均耗时）、7/30 天输入输出堆叠趋势图、智能体消耗排行（占比进度条）、消耗明细分页（按智能体/来源过滤）
- **模型管理**（`/models`）：CRUD；保存时真实调用验证可用性，支持重新检测；供应商覆盖 DashScope / Kimi / DeepSeek / GLM / MiniMax / OpenAI / Anthropic / Gemini / Ollama / 自定义
- **智能体管理**（`/agents`）：CRUD + 启用/禁用；每个智能体可挂载系统工具、自定义工具、MCP 服务、知识库、技能仓库；保存前确认弹窗
- **系统工具**（`/tools`）：扫描 `@Tool` 注解组件自动注册，内置查天气（wttr.in 真实数据）、联网搜索（必应）、查日期，不落库
- **自定义工具**（`/custom-tools`）：配置化 HTTP 工具，无需写代码即可接入任意 REST 接口
- **MCP 服务管理**（`/mcp`）：接入 MCP Server，工具自动并入所属智能体的工具箱
- **知识库管理**（`/knowledge`）：RAG 知识库配置，供智能体检索增强
- **技能仓库管理**（`/skills`）：支持 Git 远程仓库（如 [agentscope-ai/skills](https://github.com/agentscope-ai/skills)）与本地路径，按 AgentScope Skill 规范扫描 SKILL.md
- **流式对话**（`/chat`）：选择启用状态的智能体进行流式对话，每轮对话异步落库（`chat_record`）供看板统计
- **ApiKey 管理**（`/apikey`）：开放平台密钥管理
- **开放接口**（`/api/agent/**`）：对外智能体代理接口，请求头 `X-Api-Key` 鉴权（无需登录管理端），按 key 归属用户校验智能体访问权限；流式对话（SSE，返回 agent_start / thinking / text_block / tool_call / tool_result / agent_result / agent_end 事件序列）、会话列表 / 详情 / 删除 / 中断；Knife4j 在线文档 `/doc.html`（OpenAPI JSON 在 `/v3/api-docs`）
- **数据存储**（`/state-stores`）：会话状态存储（AgentStateStore）总览——内存 / 本地 JSON 文件 / Redis / MySQL 四种实现，展示配置与实时可用性；每个智能体创建时可独立选择，数据互相隔离
- **登录与权限**：sa-token 登录认证（会话持久化到 Redis）；多用户数据隔离，普通用户只见自己的资源，管理员看全部；用户管理、操作日志仅管理员可见
- **健康检查**：`/actuator/health` 及 K8s 探针 `/actuator/health/liveness`、`/actuator/health/readiness`（readiness 汇聚 db/redis 状态，依赖故障时探针失败自动摘流）

## 技术栈

| 组件 | 版本 |
|---|---|
| Java | 17 |
| Spring Boot | 3.3.4 |
| Vaadin | 24.4.x |
| AgentScope Java | 2.0.0 |
| MyBatis-Plus | 3.5.7 |
| Sa-Token | 1.39.0 |
| Hutool | 5.8.32 |
| MySQL | 8.x |
| Redis | 会话存储 |

## 快速开始

1. **初始化数据库**：本机安装 MySQL 8，依次执行两个脚本（建库建表 + 演示数据）：

   ```bash
   mysql -uroot -p < sql/agent_platform_schema.sql   # 建库建表（会 drop 重建整个库）
   mysql -uroot -p < sql/agent_platform_data.sql     # 演示数据（仅限全新库）
   ```

2. **准备依赖服务**：启动本地 Redis（sa-token 会话存储）；编辑 `src/main/resources/application.yml` 中的 `spring.datasource` / `spring.data.redis` 连接信息。

3. **启动**：

   ```bash
   mvn spring-boot:run
   ```

4. **访问**：浏览器打开 <http://localhost:8081>，使用内置管理员账号 **admin / admin123** 登录（首次启动自动创建，请登录后及时修改密码）。

## 打包部署

```bash
mvn clean package -Pproduction
java -jar target/agent-platform-1.0.0.jar
```

## 目录结构

```
├── sql/agent_platform_schema.sql # 建库建表
├── sql/agent_platform_data.sql   # 演示数据（schema 之后执行，仅限全新库）
└── src/main/
    ├── java/com/example/agent/
    │   ├── config/             # MyBatis-Plus 分页与字段填充、管理员初始化、启动注册智能体、OpenAPI 文档
    │   ├── proxy/              # 开放接口（/api/agent/**，X-Api-Key 鉴权），自包含：controller/service/敏感词/DTO
    │   ├── system/
    │   │   ├── entity/         # 模型/智能体/知识库/MCP/技能仓库/用户/对话记录等 12 张表
    │   │   ├── mapper/         # BaseMapper + 看板统计 SQL
    │   │   ├── dto/            # ToolInfo、看板统计 DTO
    │   │   ├── agent/          # AgentRegistry、ModelFactory、MCP/知识库/技能工厂、可用性检测
    │   │   ├── chat/           # 管理端流式对话（ChatService/ChatChunk）
    │   │   ├── auth/           # sa-token 登录、数据权限（租户过滤）
    │   │   ├── log/            # 操作日志注解 + AOP
    │   │   └── service/        # 业务逻辑 + ToolService（@Tool 扫描注册）
    │   ├── tool/               # 内置系统工具：WeatherTools、SearchTools、DateTimeTools
    │   └── ui/                 # MainLayout + LoginView（登录页不挂主布局）
    │       ├── view/           # 13 个管理页（Dashboard / 智能体 / 模型 / 工具 / 对话等）
    │       ├── chat/           # 流式对话面板：ChatPanel、AssistantMessageView、MarkdownRenderer
    │       └── component/      # 通用组件：Notify、FormValidators、PaginationBar
    └── resources/
        ├── application.yml
        └── META-INF/resources/styles/  # 各页面独立样式（dashboard.css / chat.css / ...）
```

## 关键设计说明

- **智能体实例注册中心**（`AgentRegistry`）：每个启用状态的智能体按配置独立构建 `HarnessAgent` 并注册为 Spring 单例，创建时固化系统提示词、模型、工具箱、MCP 工具、知识库与技能仓库；新增/编辑重建、删除销毁，模型/MCP/知识库/技能仓库变更时级联重建引用它的实例；禁用状态的智能体不注册、不可对话。
- **系统工具解析**：`ToolService` 在 Spring 单例就绪后扫描所有 Bean，把带 `@Tool` 注解的方法反射注册进 AgentScope `Toolkit`。新增系统工具只需再写一个带 `@Tool` 注解的 `@Component`，重启生效。
- **技能仓库**：`GitSkillRepository` 克隆远程仓库到本地缓存目录（`localPath` 可指定持久位置），仓库根存在 `skills/` 子目录时优先扫描，只识别直接子目录中符合规范的 `SKILL.md`。
- **对话统计**：`ChatService` 每轮对话结束异步写入 `chat_record`（工具调用数、耗时、成功与否），看板的趋势/概览/活跃榜全部由它聚合，普通用户只统计自己名下智能体的数据。
- **Token 消耗统计**（`TokenUsageService`）：管理端 `ChatService` 与开放接口 `AgentProxyService` 在事件流上捕获 `ModelCallEndEvent`（携带 AgentScope `ChatUsage`：输入/输出/缓存 token 与耗时），每次模型调用异步落一条 `agent_token_usage`（source 区分 admin/api），服务商未上报 usage 的调用不落库；监控页的指标卡/趋势/排行/明细全部由它聚合，普通用户只统计自己名下智能体的数据。
- **开放接口鉴权**（`AgentProxyService`）：`/api/agent/**` 不走管理端登录态，凭请求头 `X-Api-Key` 定位 key 的归属用户，再校验其是否有权访问目标智能体（本人创建或管理员）；会话列表/详情/删除直接读写该智能体的 `AgentStateStore`（`agent_state` key），中断走 `ReActAgent.interrupt(userId, sessionId)` 会话级协作式中断；流式对话独立于管理端 `ChatService`，由 `AgentProxyService` 直接驱动 HarnessAgent，事件逐条转换为 `AgentSseEvent` 后以 `Flux<ServerSentEvent>` 推送——工具入参增量按 toolCallId 累积、在 ToolCallEndEvent 输出完整参数（支持并行工具调用），工具结果增量逐条输出，流内异常以 `event=error` 事件下发；入参消息先经敏感词过滤（houbb sensitive-word 默认词库，`SensitiveWordFilter`），命中即拒绝（400）并返回命中的词。
- **会话状态存储**（`AgentStateStoreFactory`）：按智能体配置的 `state_store` 类型构建 AgentScope `AgentStateStore` 注入 HarnessAgent——内存（独立实例）、本地 JSON 文件（`workspaces/state/agent-<id>` 子目录）、Redis（每智能体 key 前缀）、MySQL（与主库同库的 `agentscope_sessions` 表，官方 userId:sessionId 寻址），四种实现之间数据互相隔离。
- **数据权限**：MyBatis 租户插件按当前登录用户自动过滤各表 `user_id`，管理员（`is_admin=1`）不过滤。
- **逻辑删除**：各表均有 `deleted` 字段，MyBatis-Plus 全局配置逻辑删除。

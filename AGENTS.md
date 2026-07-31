# AGENTS.md

本文件面向 AI 编码代理，介绍 agent-platform 项目的架构、构建方式与开发约定。阅读本文件前无需任何项目背景知识。

## 项目概述

agent-platform 是一个一站式智能体（Agent）管理平台，基于 **AgentScope Java 2.0 + Spring Boot 3 + Vaadin 24 + MySQL + MyBatis-Plus**。核心能力：

- 模型、智能体、工具、MCP 服务、知识库、技能仓库的统一配置与管理（Vaadin 服务端渲染的管理后台）
- 管理端流式对话（`/chat`）与对话数据看板（`/`、`/token-monitor`）
- 开放接口（`/api/agent/**`）：SSE 流式对话、会话管理，请求头 `X-Api-Key` 鉴权
- 多用户登录认证（sa-token，会话持久化到 Redis）与数据隔离（按 `user_id` 租户过滤）

主类：`com.example.agent.AgentPlatformApplication`（`@SpringBootApplication` + `@MapperScan("com.example.agent.system.mapper")`）。服务端口 **8081**。

## 技术栈

| 组件 | 版本 | 用途 |
|---|---|---|
| Java | 17 | 语言版本（`pom.xml` 中 `java.version`） |
| Spring Boot | 3.3.4 | 应用框架（parent） |
| Vaadin | 24.4.13 | 管理后台 UI（纯 Java 服务端渲染，无前端源码构建） |
| AgentScope Java | 2.0.0 | Agent 框架：HarnessAgent、Toolkit、各模型扩展、技能仓库、RAG、状态存储 |
| MyBatis-Plus | 3.5.7 | ORM（`mybatis-plus-spring-boot3-starter`） |
| Sa-Token | 1.39.0 | 登录认证 + Redis 会话持久化 |
| MySQL | 8.x | 主数据库 |
| Redis | - | sa-token 会话 + AgentStateStore 可选实现 |
| Hutool | 5.8.32 | 工具类库（项目约定优先使用） |
| Knife4j | 4.5.0 | 接口文档 `/doc.html`（OpenAPI JSON 在 `/v3/api-docs`） |
| fastjson2 | 2.0.52 | JSON 处理 |
| Lombok | 随 Boot 管理 | 简化实体/服务代码 |

注意：`agentscope-extensions-redis` 中央仓库暂无 2.0.0，固定使用 `2.0.0-RC4`，且排除了 jedis/redisson/lettuce（lettuce 复用 spring-data-redis 自带版本），改动此依赖前先阅读 `pom.xml` 中的注释。

## 构建与运行命令

```bash
# 初始化数据库（MySQL 8，schema 会 drop 重建整个库；data 仅限全新库）
mysql -uroot -p < sql/agent_platform_schema.sql
mysql -uroot -p < sql/agent_platform_data.sql

# 本地开发启动（需先启动本地 MySQL 和 Redis，连接信息在 application.yml）
mvn spring-boot:run

# 运行测试
mvn test

# 生产打包（-Pproduction 触发 vaadin-maven-plugin 前端构建）
mvn clean package -Pproduction
java -jar target/agent-platform-1.0.0.jar
```

启动后访问 <http://localhost:8081>，内置管理员 **admin / admin123**（首次启动由 `AdminUserInitializer` 自动创建）。健康检查 `/actuator/health`，K8s 探针 `/actuator/health/liveness`、`/actuator/health/readiness`。

## 目录结构与模块划分

所有代码在 `src/main/java/com/example/agent/` 下：

- `config/` — MyBatis-Plus 分页与字段填充（`MybatisPlusConfig`、`MyMetaObjectHandler`）、管理员初始化（`AdminUserInitializer`）、启动时注册智能体（`AgentBootstrap`）、OpenAPI 文档、状态存储配置（`StateStoreProperties`）
- `proxy/` — 开放接口 `/api/agent/**`，自包含：`AgentProxyController`/`AgentProxyService`/`SensitiveWordFilter`/DTO。`X-Api-Key` 鉴权，不依赖管理端登录态
- `system/` — 管理端核心业务：
  - `entity/` — 12 张表的实体（模型/智能体/知识库/MCP/技能仓库/用户/对话记录/Token 用量等），均继承 `BaseEntity`
  - `mapper/` — MyBatis-Plus BaseMapper + 看板统计 SQL
  - `service/` — 业务逻辑；`ToolService` 扫描 `@Tool` 注解注册系统工具；`TokenUsageService` 聚合 token 消耗
  - `agent/` — Agent 运行体系核心：`AgentRegistry`（实例注册中心）、`ModelFactory`、`McpClientFactory`、`KnowledgeFactory`、`SkillRepoFactory`、`AgentStateStoreFactory`、`ModelAvailabilityChecker`
  - `chat/` — 管理端流式对话（`ChatService`/`ChatChunk`）
  - `auth/` — sa-token 登录、Vaadin 会话集成、`UserTenantHandler`（租户过滤）
  - `log/` — 操作日志注解 + AOP 切面
- `tool/` — 内置系统工具：`WeatherTools`（wttr.in）、`SearchTools`（必应）、`DateTimeTools`
- `ui/` — Vaadin 界面：`MainLayout` + `LoginView`（登录页不挂主布局）；`view/` 13+ 个管理页；`chat/` 流式对话面板；`component/` 通用组件（`Notify`、`FormValidators`、`PaginationBar`）

资源目录：

- `src/main/resources/application.yml` — 全部配置（数据源、Redis、sa-token、状态存储、actuator）
- `src/main/resources/META-INF/resources/styles/` — 各页面独立 CSS（dashboard.css / chat.css / ...）
- `src/main/resources/skills/` — Classpath 技能（含 `SKILL.md`，符合 AgentScope Skill 规范）
- `sql/` — 建库建表与演示数据脚本
- `workspaces/` — HarnessAgent 运行期工作区（技能缓存、会话日志、`state/` 下的 JSON 状态存储），**已 gitignore，勿提交**

`src/main/frontend/generated/` 是 Vaadin 构建生成物，已 gitignore，不要手工编辑。

## 关键架构机制

- **AgentRegistry（智能体实例注册中心）**：每个启用状态的智能体按配置独立构建 `HarnessAgent` 并注册为 Spring 单例，创建时固化系统提示词、模型、工具箱、MCP 工具、知识库与技能仓库。新增/编辑触发重建，删除触发销毁，模型/MCP/知识库/技能仓库变更时级联重建引用它的实例；禁用状态不注册、不可对话。对话时直接从容器取实例。
- **系统工具**：`ToolService` 在 Spring 单例就绪后扫描所有 Bean，把带 AgentScope `@Tool` 注解的方法反射注册进 `Toolkit`，不落库。新增系统工具 = 写一个带 `@Tool` 注解的 `@Component`，重启生效。
- **数据权限**：MyBatis 租户插件（`UserTenantHandler`）按当前登录用户自动过滤各表 `user_id`；管理员（`is_admin=1`）不过滤。普通用户只见自己的资源。
- **逻辑删除**：各表均有 `deleted` 字段，`application.yml` 全局配置逻辑删除（删除值 1，未删 0）。
- **对话与 Token 统计**：`ChatService` 每轮对话异步写 `chat_record`；`TokenUsageService` 在事件流上捕获 `ModelCallEndEvent` 异步写 `agent_token_usage`（source 区分 admin/api，服务商未上报 usage 的不落库）。看板全部由这两表聚合。
- **会话状态存储**：`AgentStateStoreFactory` 按智能体配置的 `state_store` 类型构建 AgentScope `AgentStateStore`——内存 / 本地 JSON 文件（`workspaces/state/agent-<id>`）/ Redis（key 前缀隔离）/ MySQL（`agentscope_sessions` 表），四种实现数据互相隔离。
- **开放接口**：`AgentProxyService` 独立于管理端 `ChatService` 直接驱动 HarnessAgent，事件转换为 `AgentSseEvent` 后以 SSE 推送；入参先经敏感词过滤（`SensitiveWordFilter`，houbb sensitive-word 默认词库），命中返回 400；中断走会话级协作式中断。

## 代码风格与约定

- **注释与文档一律使用中文**（项目原始需求即要求中文交流，代码注释均为中文）。
- 优先使用 **Hutool** 工具类简化代码；不重复造轮子，代码保持简洁。
- 使用 **Lombok**（`@Slf4j`、`@Data` 等）。
- **UI 渲染与业务逻辑分离**：Vaadin 视图层不写后端处理逻辑，业务放 `system/service/`。
- 统一返回体 `common/Result` + `common/ErrorCode`（开放接口用 `AgentProxyException` 携带错误码）。
- Vaadin 类扫描范围限定为 `com.example.agent`（`vaadin.allowed-packages`），新包必须放在该包下。
- 新增实体表：继承 `BaseEntity`，带 `user_id` 与 `deleted` 字段，以接入租户过滤与逻辑删除。

## 测试说明

测试在 `src/test/java/com/example/agent/`，共 7 个测试类，两种形态：

- **纯内存单元测试**（不依赖 Spring 上下文与外部服务）：`SensitiveWordFilterTest`、`MarkdownRendererTest`
- **集成测试**（`@SpringBootTest`，需要本地 MySQL + Redis 按 `application.yml` 可用）：`ApiKeyModuleTest`、`UserModuleTest`、`ChatServiceTest`、`DashboardViewTest`、`PermissionBypassTest`

运行全部测试：`mvn test`。在没有 MySQL/Redis 的环境下，`@SpringBootTest` 类会失败，属预期；改动后至少确保相关测试通过。新增功能应补充对应测试（项目已有测试即按此模式编写）。

## 安全注意事项

- 管理端登录用 sa-token，会话持久化到 Redis；开放接口 `/api/agent/**` 不走登录态，仅凭 `X-Api-Key`，按 key 归属用户校验智能体访问权限。
- 开放接口入参必经敏感词过滤，不要绕过 `SensitiveWordFilter`。
- `application.yml` 中的 MySQL/Redis 账号密码是本地开发默认值，部署时务必修改；不要把真实密钥提交进仓库。
- 默认管理员 `admin / admin123` 仅用于首次登录，文档中已提示登录后及时改密。
- 多用户隔离依赖 `user_id` 租户过滤 + 逻辑删除，新增查询/表时不要绕开这套机制。
- `workspaces/` 含运行期会话数据，勿提交、勿泄露。

## 部署

- 打包：`mvn clean package -Pproduction`（production profile 启用 Vaadin 生产模式前端构建），产物 `target/agent-platform-1.0.0.jar`，`java -jar` 直接运行。
- 依赖外部服务：MySQL 8（先执行 `sql/` 脚本）与 Redis。
- K8s 场景使用 `/actuator/health/liveness` 与 `/actuator/health/readiness` 探针（readiness 汇聚 db/redis 状态，依赖故障自动摘流）。

# agent-platform

基于 **AgentScope Java 2.0 + Spring Boot 3 + Vaadin 24 + MySQL** 的一站式智能体管理平台：模型、智能体、工具、MCP 服务、知识库、技能仓库统一配置，开箱即用的流式对话与数据看板。

![数据看板预览](imgs/home.png)

> 更多详细介绍，参考 [doc](docs/doc.md) 的引导。

## 功能特性

- **数据看板**：资源统计、对话趋势、活跃智能体、模型可用环图、Token 摘要、快捷入口
- **智能体管理**：CRUD + 启用/禁用；可挂载系统工具、自定义工具、MCP 服务、知识库、技能仓库
- **模型管理**：CRUD + 保存时真实调用检测；支持 DashScope / Kimi / DeepSeek / GLM / MiniMax / OpenAI / Anthropic / Gemini / Ollama
- **系统工具**：`@Tool` 注解组件自动注册，内置天气（wttr.in）、联网搜索、日期时间
- **自定义工具**：配置化 HTTP 工具，不写代码接入任意 REST 接口
- **MCP 服务**：接入 MCP Server，工具自动并入智能体工具箱
- **知识库**：阿里云百炼 / Dify RAG 检索增强
- **技能仓库**：Git 远程仓库或本地路径，按 AgentScope Skill 规范扫描 SKILL.md
- **流式对话**：多智能体流式对话，对话记录异步落库供看板统计
- **Token 监控**：模型调用 token 消耗实时埋点，指标卡 / 趋势图 / 智能体排行 / 消耗明细
- **开放接口**：`/api/agent/**`（请求头 `X-Api-Key` 鉴权），SSE 流式对话 + 会话列表/详情/删除/中断，Knife4j 文档 `/doc.html`
- **数据存储**：内存 / JSON 文件 / Redis / MySQL 四种会话状态存储，按智能体独立选择、数据隔离
- **登录与权限**：sa-token 认证（Redis 会话），多用户数据隔离，用户管理 / 操作日志仅管理员可见
- **健康检查**：`/actuator/health` + K8s liveness / readiness 探针

## 技术栈

Java 17 · Spring Boot 3.3 · Vaadin 24.4 · AgentScope Java 2.0 · MyBatis-Plus · Sa-Token · Hutool · MySQL 8 · Redis

## 快速开始

1. **初始化数据库**（本机 MySQL 8）：

   ```bash
   mysql -uroot -p < sql/agent_platform_schema.sql   # 建库建表（会 drop 重建整个库）
   mysql -uroot -p < sql/agent_platform_data.sql     # 演示数据（仅限全新库）
   ```

2. **准备依赖**：启动本地 Redis；按需修改 `src/main/resources/application.yml` 中的数据源 / Redis 连接。

3. **启动**：

   ```bash
   mvn spring-boot:run
   ```

4. **访问**：<http://localhost:8081>，管理员 **admin / admin123** 登录（首次启动自动创建，请及时修改密码）。

## 打包部署

```bash
mvn clean package -Pproduction
java -jar target/agent-platform-1.0.0.jar
```

## 目录结构

```
├── sql/                        # 建库建表 + 演示数据
└── src/main/
    ├── java/com/example/agent/
    │   ├── config/             # 框架配置（MyBatis-Plus、管理员初始化、OpenAPI 等）
    │   ├── proxy/              # 开放接口（/api/agent/**，X-Api-Key 鉴权）
    │   ├── system/             # 业务核心：entity / mapper / service / agent / chat / auth / log
    │   ├── tool/               # 内置系统工具（@Tool 注解自动注册）
    │   └── ui/                 # Vaadin 界面：view 管理页 / chat 对话面板 / component 通用组件
    └── resources/
        ├── application.yml
        └── META-INF/resources/styles/  # 全局与页面样式
```

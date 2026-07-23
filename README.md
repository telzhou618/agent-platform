# agent-platform

基于 **AgentScope Java 2.0 + Spring Boot 3 + Vaadin + MySQL + MyBatis-Plus + Java 17** 的综合性 Agent 管理平台，代码风格参考 [vaadin-admin](../vaadin-admin)。

## 功能

- 模型管理（`/models`）：CRUD，支持 DashScope、OpenAI、Anthropic、自定义（baseUrl / apiKey）
- 智能体管理（`/agents`）：CRUD，字段含名称、关联模型、系统提示词、工具列表
- 工具管理（`/tools`）：解析系统中 `@Tool` 注解标注的工具，展示名称、描述、参数 JSON Schema；内置查天气、查日期示例工具，不落库
- 流式对话（侧边栏「流式对话」弹窗）：选择智能体进行流式对话；切换智能体或点击「新会话」清空消息并生成新 sessionId
- 无登录、无权限控制，经典管理后台布局

## 全局智能体

- 启动时注册**全局唯一** `ReActAgent` Bean（`config/AgentScopeConfig.java`），所有会话共用
- 全局默认模型：DashScope `qwen-flash`，apiKey 读环境变量 **`YOKA_DASHSCOPE_API_KEY`**（未设置则默认模型不可用并打警告日志）
- 对话时按所选智能体的配置动态加载：
  - 系统提示词 → `DynamicAgentMiddleware#onSystemPrompt` 替换
  - 模型 → `DynamicAgentMiddleware#onModelCall` 切换（`ModelFactory` 按 model_config 记录实时构建，DashScope 的 apiKey 留空时回退环境变量）
  - 工具 → `DynamicAgentMiddleware#onReasoning` 过滤工具 schema，全局工具箱保持不变
- 会话历史由 AgentScope 按 (userId, sessionId) 自动维护（内存态，重启清空）；userId 暂固定为 `default`

## 技术栈

| 组件 | 版本 |
|---|---|
| Java | 17 |
| Spring Boot | 3.3.4 |
| Vaadin | 24.4.x |
| AgentScope Java | 2.0.0 |
| MyBatis-Plus | 3.5.7 |
| Hutool | 5.8.32 |
| MySQL | 8.x |

## 快速开始

1. **初始化数据库**：本机安装 MySQL 8，执行脚本（会创建 `agent_platform` 库并写入种子数据）：

   ```bash
   mysql -uroot -p < sql/agent_platform.sql
   ```

2. **修改数据源**：编辑 `src/main/resources/application.yml` 中的 `spring.datasource` 用户名和密码。

3. **启动**：

   ```bash
   mvn spring-boot:run
   ```

4. **访问**：浏览器打开 <http://localhost:8080>。

## 打包部署

```bash
mvn clean package -Pproduction
java -jar target/agent-platform-1.0.0.jar
```

## 目录结构

```
├── sql/agent_platform.sql      # 建库建表 + 种子数据
└── src/main/
    ├── java/com/example/agent/
    │   ├── config/             # MyBatis-Plus 分页、字段自动填充
    │   ├── system/
    │   │   ├── entity/         # model_config / agent_info
    │   │   ├── mapper/         # BaseMapper
    │   │   ├── dto/            # ToolInfo（系统工具，不落库）
    │   │   └── service/        # 业务逻辑 + ToolService（@Tool 解析）
    │   ├── tool/               # 示例工具：WeatherTools、DateTimeTools（@Tool 注解）
    │   └── ui/                 # MainLayout、HomeView、ModelView、AgentView、ToolView
    └── resources/application.yml
```

## 关键设计说明

- **系统工具解析**：`ToolService` 在 Spring 单例就绪后扫描所有 Bean，找出带 `@Tool` 注解方法的组件，通过 AgentScope 的 `Toolkit.registerTool()` 反射注册，再读回工具名称、描述、参数 JSON Schema。新增工具只需再写一个带 `@Tool` 注解的 `@Component`，无需改任何代码。
- **智能体工具列表**：`agent_info.tools` 以 JSON 数组字符串存储工具名，编辑页用多选框从系统工具中选择。
- **逻辑删除**：两张表均有 `deleted` 字段，MyBatis-Plus 全局配置逻辑删除。

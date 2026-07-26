-- ----------------------------
-- agent_platform 数据库初始化脚本（MySQL 8）
-- ----------------------------

drop database if exists agent_platform;
create database agent_platform default character set utf8mb4 collate utf8mb4_general_ci;
use agent_platform;

-- ----------------------------
-- 管理端用户表
-- ----------------------------
drop table if exists sys_user;
create table sys_user (
    id          bigint auto_increment primary key,
    username    varchar(64)  not null comment '登录账号',
    password    varchar(128) not null comment '密码（BCrypt）',
    phone       varchar(32)  null comment '手机号',
    email       varchar(128) null comment '邮箱',
    is_admin    tinyint      not null default 0 comment '是否管理员：1 是 0 否',
    create_time datetime     null comment '创建时间',
    update_time datetime     null comment '更新时间',
    deleted     tinyint      not null default 0 comment '逻辑删除：0 正常 1 已删除'
) engine = innodb comment '管理端用户表';

-- ----------------------------
-- 模型配置表
-- ----------------------------
drop table if exists model_config;
create table model_config (
    id          bigint auto_increment primary key,
    name        varchar(64)  not null comment '模型名称（展示用）',
    provider    varchar(32)  not null comment '供应商：dashscope/kimi/deepseek/glm/minimax/openai/anthropic/custom 自定义',
    model       varchar(128) not null comment '模型标识，如 qwen-plus、gpt-4o、claude-sonnet-4',
    base_url    varchar(256) null comment 'API 地址，自定义供应商时必填',
    api_key     varchar(256) null comment 'API Key',
    available   tinyint      not null default 0 comment '可用状态（保存时真实调用验证）：1 可用 0 不可用',
    check_msg   varchar(512) null comment '最近一次的不可用原因',
    remark      varchar(256) null comment '备注',
    user_id     bigint       null comment '创建人（sys_user.id），管理员可看全部',
    create_time datetime     null comment '创建时间',
    update_time datetime     null comment '更新时间',
    deleted     tinyint      not null default 0 comment '逻辑删除：0 正常 1 已删除',
    key idx_user_id (user_id)
) engine = innodb comment '模型配置表';

-- ----------------------------
-- 智能体表
-- ----------------------------
drop table if exists agent_info;
create table agent_info (
    id          bigint auto_increment primary key,
    name        varchar(64)   not null comment '智能体名称',
    model_id    bigint        null comment '关联模型 ID（model_config.id）',
    sys_prompt  text          null comment '系统提示词',
    tools       varchar(1024) null comment '工具名称列表，JSON 数组',
    mcp_servers varchar(1024) null comment 'MCP 服务 ID 列表，JSON 数组',
    knowledge_bases varchar(1024) null comment '知识库 ID 列表，JSON 数组',
    skill_repos varchar(1024) null comment '技能仓库 ID 列表，JSON 数组',
    custom_tools varchar(1024) null comment '自定义工具 ID 列表，JSON 数组',
    description varchar(256)  null comment '描述',
    user_id     bigint        null comment '创建人（sys_user.id），管理员可看全部',
    create_time datetime      null comment '创建时间',
    update_time datetime      null comment '更新时间',
    deleted     tinyint       not null default 0 comment '逻辑删除：0 正常 1 已删除',
    key idx_model_id (model_id),
    key idx_user_id (user_id)
) engine = innodb comment '智能体表';

-- ----------------------------
-- MCP 服务表
-- ----------------------------
drop table if exists mcp_server;
create table mcp_server (
    id          bigint auto_increment primary key,
    name        varchar(64)   not null comment 'MCP 服务名称',
    description varchar(256)  null comment '描述',
    type        varchar(32)   not null default 'streamableHttp' comment '传输类型：streamableHttp/sse',
    url         varchar(512)  not null comment '服务地址',
    headers     varchar(2048) null comment '请求头，JSON 对象 {key:value}',
    timeout     int           not null default 30000 comment '超时时间（毫秒）',
    user_id     bigint        null comment '创建人（sys_user.id），管理员可看全部',
    create_time datetime      null comment '创建时间',
    update_time datetime      null comment '更新时间',
    deleted     tinyint       not null default 0 comment '逻辑删除：0 正常 1 已删除',
    key idx_user_id (user_id)
) engine = innodb comment 'MCP 服务表';

-- ----------------------------
-- 知识库表
-- ----------------------------
drop table if exists knowledge_base;
create table knowledge_base (
    id              bigint auto_increment primary key,
    name            varchar(64)  not null comment '知识库名称',
    type            varchar(32)  not null comment '类型：bailian/dify，未来可扩展',
    config          text         null comment '类型相关配置，JSON 对象',
    retrieve_limit  int          not null default 5 comment '默认检索条数',
    score_threshold double       not null default 0.5 comment '默认分数阈值',
    remark          varchar(256) null comment '备注',
    user_id         bigint       null comment '创建人（sys_user.id），管理员可看全部',
    create_time     datetime     null comment '创建时间',
    update_time     datetime     null comment '更新时间',
    deleted         tinyint      not null default 0 comment '逻辑删除：0 正常 1 已删除',
    key idx_user_id (user_id)
) engine = innodb comment '知识库表';

-- ----------------------------
-- 技能仓库表（HarnessAgent 技能来源，类型可扩展）
-- ----------------------------
drop table if exists skill_repo;
create table skill_repo (
    id          bigint auto_increment primary key,
    name        varchar(128) not null comment '技能仓库名称',
    type        varchar(32)  not null comment '来源类型：git/mysql/classpath，未来可扩展',
    config      text         null comment '类型相关配置，JSON 对象',
    remark      varchar(512) null comment '备注',
    user_id     bigint       null comment '创建人（sys_user.id），管理员可看全部',
    create_time datetime     null comment '创建时间',
    update_time datetime     null comment '更新时间',
    deleted     tinyint      not null default 0 comment '逻辑删除：0 正常 1 已删除',
    key idx_user_id (user_id)
) engine = innodb comment '技能仓库表';

-- ----------------------------
-- 自定义工具表（HTTP 远程接口代理工具，用户级数据权限）
-- ----------------------------
drop table if exists custom_tool;
create table custom_tool (
    id           bigint auto_increment primary key,
    tool_key     varchar(64)  not null comment '工具标识（唯一，小写字母/数字/下划线，模型调用名）',
    name         varchar(64)  not null comment '工具名称（展示用）',
    description  varchar(512) not null comment '工具描述（模型据此决定何时调用）',
    url          varchar(512) not null comment '接口地址，支持 {参数名} 路径占位符',
    method       varchar(8)   not null default 'GET' comment '请求方式：GET/POST/PUT/DELETE',
    request_type varchar(16)  not null default 'json' comment '请求体类型（POST/PUT 时生效）：json/form',
    headers      varchar(2048) null comment '请求头，JSON 对象 {key:value}',
    params       text         null comment '参数定义，JSON 数组 [{name,type,description,required}]，type: string/number/boolean',
    user_id      bigint       null comment '创建人（sys_user.id），管理员可看全部',
    create_time  datetime     null comment '创建时间',
    update_time  datetime     null comment '更新时间',
    deleted      tinyint      not null default 0 comment '逻辑删除：0 正常 1 已删除',
    unique key uk_tool_key (tool_key),
    key idx_user_id (user_id)
) engine = innodb comment '自定义工具表';

-- ----------------------------
-- ApiKey 表（将来用于访问智能体）
-- ----------------------------
drop table if exists api_key;
create table api_key (
    id          bigint auto_increment primary key,
    name        varchar(64)  not null comment 'ApiKey 名称',
    api_key     varchar(80)  not null comment 'ApiKey 值（ak- 前缀，全局唯一）',
    status      tinyint      not null default 1 comment '状态：1 启用 0 禁用',
    remark      varchar(256) null comment '备注',
    user_id     bigint       null comment '创建人（sys_user.id），管理员可看全部',
    create_time datetime     null comment '创建时间',
    update_time datetime     null comment '更新时间',
    deleted     tinyint      not null default 0 comment '逻辑删除：0 正常 1 已删除',
    unique key uk_api_key (api_key),
    key idx_user_id (user_id)
) engine = innodb comment 'ApiKey 表（将来用于访问智能体）';

-- ----------------------------
-- 操作日志表（AOP 记录，仅管理员查看）
-- ----------------------------
drop table if exists operation_log;
create table operation_log (
    id          bigint auto_increment primary key,
    user_id     bigint       null comment '操作人（sys_user.id），未登录为 null',
    username    varchar(64)  null comment '操作人账号（冗余，防用户删除后丢失）',
    module      varchar(32)  not null comment '模块，如 模型管理',
    action      varchar(16)  not null comment '操作，如 保存/删除/登录',
    summary     varchar(512) null comment '操作摘要，如 模型名称',
    success     tinyint      not null default 1 comment '是否成功：1 成功 0 失败',
    error_msg   varchar(512) null comment '失败原因',
    create_time datetime     null comment '操作时间',
    key idx_create_time (create_time),
    key idx_user_id (user_id)
) engine = innodb comment '操作日志表';

-- ----------------------------
-- 对话记录表（dashboard 统计数据来源）
-- ----------------------------
drop table if exists chat_record;
create table chat_record (
    id          bigint auto_increment primary key,
    agent_id    bigint      not null comment '智能体 ID（agent_info.id）',
    session_id  varchar(64) not null comment '会话 ID',
    tool_calls  int         not null default 0 comment '本轮工具调用次数',
    duration_ms bigint      not null default 0 comment '本轮耗时（毫秒）',
    success     tinyint     not null default 1 comment '是否成功：1 成功 0 失败',
    create_time datetime    null comment '创建时间',
    update_time datetime    null comment '更新时间',
    deleted     tinyint     not null default 0 comment '逻辑删除：0 正常 1 已删除',
    key idx_agent_id (agent_id),
    key idx_create_time (create_time)
) engine = innodb comment '对话记录表';

-- ----------------------------
-- 种子数据（user_id=1 归内置管理员，admin 账号由应用启动时自动创建：admin/admin123）
-- ----------------------------
insert into model_config (name, provider, model, base_url, api_key, remark, user_id, create_time, update_time) values
('通义千问 Flash', 'dashscope', 'qwen-flash', null, '', '阿里云百炼平台，base_url 留空用默认端点', 1, now(), now()),
('GPT-4o', 'openai', 'gpt-4o', 'https://api.openai.com/v1', '', 'OpenAI 官方', 1, now(), now()),
('Claude Sonnet', 'anthropic', 'claude-sonnet-4-20250514', 'https://api.anthropic.com', '', 'Anthropic 官方', 1, now(), now());

insert into agent_info (name, model_id, sys_prompt, tools, description, user_id, create_time, update_time) values
('天气小助手', 1, '你是一个贴心的天气助手，回答用户关于天气和日期的问题。', '["get_weather","get_current_date","get_current_time"]', '示例智能体：查询天气和日期', 1, now(), now());

-- ----------------------------
-- 测试数据：自定义工具（公开演示接口）
-- ----------------------------
insert into custom_tool (tool_key, name, description, url, method, request_type, headers, params, user_id, create_time, update_time) values
('get_cat_fact', '随机猫咪知识', '获取一条随机的猫咪冷知识。当用户想要有趣的猫咪知识时使用。', 'https://catfact.ninja/fact', 'GET', 'json', null, null, 1, now(), now()),
('get_todo', '查询待办事项', '按 ID 查询一条待办事项（JSONPlaceholder 演示接口）。当用户要查某个编号的待办时使用。', 'https://jsonplaceholder.typicode.com/todos/{id}', 'GET', 'json', null,
 '[{"name":"id","type":"number","description":"待办事项 ID，1-200","required":true}]', 1, now(), now()),
('create_post', '创建演示帖子', '向 JSONPlaceholder 演示接口创建一条帖子并返回结果（POST JSON 测试）。当用户要发帖或测试 POST 时使用。', 'https://jsonplaceholder.typicode.com/posts', 'POST', 'json', null,
 '[{"name":"title","type":"string","description":"帖子标题","required":true},{"name":"body","type":"string","description":"帖子内容","required":false}]', 1, now(), now()),
('get_city_weather', '查询城市天气', '查询指定城市的实时天气概况。当用户问某个城市的天气时使用。', 'https://wttr.in/{city}?format=3', 'GET', 'json',
 '{"User-Agent":"curl/8.0"}',
 '[{"name":"city","type":"string","description":"城市名，如 Beijing、Shanghai","required":true}]', 1, now(), now());

-- ----------------------------
-- 测试数据：技能仓库（git/mysql/classpath 三种来源）
-- 注意：git 类型的 url 是本机路径，换环境请改成实际的技能 git 仓库地址
-- ----------------------------
insert into skill_repo (name, type, config, remark, user_id, create_time, update_time) values
('测试Git技能仓库', 'git', '{"url":"D:/code/agent-platform/testdata/git-skills","autoSync":true}', '本地 git 仓库验证（含 demo-git-skill）', 1, now(), now()),
('测试MySQL技能仓库', 'mysql', '{"databaseName":"agent_platform","skillsTableName":"skills","writeable":false}', '平台库 skills 表验证（含 demo-mysql-skill）', 1, now(), now()),
('测试Classpath技能仓库', 'classpath', '{"directory":"skills"}', 'src/main/resources/skills 验证（含 demo-classpath-skill）', 1, now(), now());

-- ----------------------------
-- 测试数据：MySQL 技能仓库的技能内容表（与 MysqlSkillRepository 自动建表结构一致，
-- 显式建表以便写入测试技能；表已存在时跳过）
-- ----------------------------
create table if not exists skills (
    id             bigint auto_increment,
    name           varchar(255) not null,
    description    text         not null,
    skill_content  longtext     not null,
    source         varchar(255) not null,
    metadata_json  longtext     null,
    created_at     timestamp    null default current_timestamp,
    updated_at     timestamp    null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_skills_name (name)
) engine = innodb comment '技能表（AgentScope MySQL 技能仓库）';

create table if not exists agentscope_skill_resources (
    id               bigint       not null,
    resource_path    varchar(500) not null,
    resource_content longtext     not null,
    created_at       timestamp    null default current_timestamp,
    updated_at       timestamp    null default current_timestamp on update current_timestamp,
    primary key (id, resource_path)
) engine = innodb comment '技能资源表（AgentScope MySQL 技能仓库）';

insert into skills (name, description, skill_content, source) values
('demo-mysql-skill', '测试技能（mysql 来源）。当用户要求演示或验证 mysql 技能时使用。',
 '# Demo MySQL Skill\n\n这是一个来自 mysql 技能仓库的技能，用于验证技能仓库接入。\n\n步骤：\n1. 告诉用户「mysql 技能已加载」\n2. 回显固定文本 DEMO-MYSQL-OK\n',
 'manual');

-- ----------------------------
-- 测试数据：演示智能体（技能验证 + 自定义工具验证）
-- ----------------------------
insert into agent_info (name, model_id, sys_prompt, skill_repos, custom_tools, description, user_id, create_time, update_time) values
('技能测试Agent', 1, '你是技能验证助手。当用户提到技能测试时，先查看可用技能列表，有相关技能就加载使用。', '[1,2,3]', null, '验证 git/mysql/classpath 三种技能来源', 1, now(), now()),
('工具测试Agent', 1, '你是工具验证助手。用户的问题能用自定义工具解决时，优先调用工具，再根据工具结果回答。', null, '[1,2,3,4]', '验证自定义工具（HTTP 远程接口）调用', 1, now(), now());

-- ----------------------------
-- 测试数据：免费 MCP 服务（无需 Key，DeepWiki + GitMCP）
-- ----------------------------
insert into mcp_server (name, description, type, url, timeout, user_id, create_time, update_time) values
('DeepWiki', '免费 MCP：GitHub 仓库文档问答，可询问任意公开仓库的结构与用法', 'streamableHttp', 'https://mcp.deepwiki.com/mcp', 30000, 1, now(), now()),
('GitMCP(AgentScope)', '免费 MCP：agentscope-ai/agentscope 仓库文档与代码检索（GitMCP 按仓库提供）', 'streamableHttp', 'https://gitmcp.io/agentscope-ai/agentscope', 30000, 1, now(), now());

insert into agent_info (name, model_id, sys_prompt, mcp_servers, description, user_id, create_time, update_time) values
('MCP测试Agent', 1, '你是 MCP 验证助手。回答依赖外部知识的问题时，优先调用可用的 MCP 工具，再根据结果回答。', '[1,2]', '验证 MCP 服务（DeepWiki/GitMCP）调用', 1, now(), now());

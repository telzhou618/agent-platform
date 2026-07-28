-- ----------------------------
-- agent_platform 建库建表脚本（MySQL 8）
-- 会 drop 并重建数据库，仅用于初始化；种子/测试数据见 agent_platform_data.sql
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
    provider    varchar(32)  not null comment '供应商：dashscope/kimi/deepseek/glm/minimax/openai/anthropic/gemini/ollama/custom 自定义',
    model       varchar(128) not null comment '模型标识，如 qwen-plus、gpt-4o、claude-sonnet-4',
    base_url    varchar(256) null comment 'API 地址，自定义供应商必填，Ollama 选填（默认本地端点）',
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
    status      tinyint       not null default 1 comment '状态：1 启用 0 禁用（禁用后不注册实例、不可对话）',
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
-- AgentScope MySQL 技能仓库的内容表（与 MysqlSkillRepository 自动建表结构一致，
-- 显式建表以便初始化测试技能；不显式创建时运行期也会自动建）
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

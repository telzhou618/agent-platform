-- ----------------------------
-- agent_platform 数据库初始化脚本（MySQL 8）
-- ----------------------------

drop database if exists agent_platform;
create database agent_platform default character set utf8mb4 collate utf8mb4_general_ci;
use agent_platform;

-- ----------------------------
-- 模型配置表
-- ----------------------------
drop table if exists model_config;
create table model_config (
    id          bigint auto_increment primary key,
    name        varchar(64)  not null comment '模型名称（展示用）',
    provider    varchar(32)  not null comment '供应商：dashscope/openai/anthropic/custom 自定义',
    model       varchar(128) not null comment '模型标识，如 qwen-plus、gpt-4o、claude-sonnet-4',
    base_url    varchar(256) null comment 'API 地址，自定义供应商时必填',
    api_key     varchar(256) null comment 'API Key',
    remark      varchar(256) null comment '备注',
    create_time datetime     null comment '创建时间',
    update_time datetime     null comment '更新时间',
    deleted     tinyint      not null default 0 comment '逻辑删除：0 正常 1 已删除'
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
    description varchar(256)  null comment '描述',
    create_time datetime      null comment '创建时间',
    update_time datetime      null comment '更新时间',
    deleted     tinyint       not null default 0 comment '逻辑删除：0 正常 1 已删除',
    key idx_model_id (model_id)
) engine = innodb comment '智能体表';

-- ----------------------------
-- 种子数据
-- ----------------------------
insert into model_config (name, provider, model, base_url, api_key, remark, create_time, update_time) values
('通义千问 Plus', 'dashscope', 'qwen-plus', 'https://dashscope.aliyuncs.com/compatible-mode/v1', '', '阿里云百炼平台', now(), now()),
('GPT-4o', 'openai', 'gpt-4o', 'https://api.openai.com/v1', '', 'OpenAI 官方', now(), now()),
('Claude Sonnet', 'anthropic', 'claude-sonnet-4-20250514', 'https://api.anthropic.com', '', 'Anthropic 官方', now(), now());

insert into agent_info (name, model_id, sys_prompt, tools, description, create_time, update_time) values
('天气小助手', 1, '你是一个贴心的天气助手，回答用户关于天气和日期的问题。', '["get_weather","get_current_date","get_current_time"]', '示例智能体：查询天气和日期', now(), now());

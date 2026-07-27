-- ----------------------------
-- agent_platform 种子/测试数据脚本（MySQL 8）
-- 需在 agent_platform_schema.sql 之后执行。
-- 注意：演示数据按自增 ID 从 1 开始互相引用（model_id=1、skill_repos='[1,2,3]' 等），
--       仅适用于全新初始化的库，请勿在已有数据的库上重复执行
-- ----------------------------
use agent_platform;

-- ----------------------------
-- 种子数据：模型 + 示例智能体
-- （user_id=1 归内置管理员，admin 账号由应用启动时自动创建：admin/admin123）
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
-- 注意：第一条 git 类型的 url 是本机路径，换环境请改成实际的技能 git 仓库地址；
--       第四条为 GitHub 免费公开技能仓库示例（AgentScope 官方 skills 仓库，只读，需能访问 GitHub），
--       localPath 为本地克隆缓存目录（相对项目根，复用克隆、避免每次临时克隆）
-- ----------------------------
insert into skill_repo (name, type, config, remark, user_id, create_time, update_time) values
('测试Git技能仓库', 'git', '{"url":"D:/code/agent-platform/testdata/git-skills","autoSync":true}', '本地 git 仓库验证（含 demo-git-skill）', 1, now(), now()),
('测试MySQL技能仓库', 'mysql', '{"databaseName":"agent_platform","skillsTableName":"skills","writeable":false}', '平台库 skills 表验证（含 demo-mysql-skill）', 1, now(), now()),
('测试Classpath技能仓库', 'classpath', '{"directory":"skills"}', 'src/main/resources/skills 验证（含 demo-classpath-skill）', 1, now(), now()),
('AgentScope官方技能仓库', 'git', '{"url":"https://github.com/agentscope-ai/skills.git","autoSync":true,"localPath":"workspaces/skill-repos/agentscope-ai-skills"}', 'GitHub 免费公开仓库，skills/ 目录下含 agentscope-skill、nano-memory 等技能', 1, now(), now());

-- ----------------------------
-- 测试数据：MySQL 技能仓库的演示技能（skills 表结构见 agent_platform_schema.sql）
-- ----------------------------
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

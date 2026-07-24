package com.example.agent;

import cn.hutool.core.util.StrUtil;
import com.example.agent.system.agent.AgentRegistry;
import com.example.agent.system.agent.ChatChunk;
import com.example.agent.system.agent.ChatService;
import com.example.agent.system.entity.AgentInfo;
import com.example.agent.system.service.AgentInfoService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流式对话端到端测试：真实调用 DashScope（需要环境变量 YOKA_DASHSCOPE_API_KEY 和本地 MySQL）。
 * 验证动态模型加载、工具调用、会话记忆隔离。
 */
@SpringBootTest
class ChatServiceTest {

    @Autowired
    private ChatService chatService;
    @Autowired
    private AgentInfoService agentInfoService;
    @Autowired
    private AgentRegistry agentRegistry;

    @BeforeAll
    static void requireApiKey() {
        Assumptions.assumeTrue(StrUtil.isNotBlank(System.getenv("YOKA_DASHSCOPE_API_KEY")),
                "未设置 YOKA_DASHSCOPE_API_KEY，跳过端到端测试");
    }

    /** 启动注册：agent_info 表中的智能体应已注册为容器中的 ReActAgent 实例 */
    @Test
    void agentsRegisteredOnStartup() {
        AgentInfo agent = agentInfoService.lambdaQuery().eq(AgentInfo::getName, "天气小助手").one();
        assertNotNull(agentRegistry.find(agent.getId()), "智能体应在启动时注册到容器");
    }

    private List<ChatChunk> chatChunks(String sessionId, Long agentId, String text) {
        return chatService.streamChat(sessionId, agentId, text).collectList().block();
    }

    /** 收集完整回复文本（只取 TEXT 增量） */
    private String chat(String sessionId, Long agentId, String text) {
        return chatChunks(sessionId, agentId, text).stream()
                .filter(c -> c.kind() == ChatChunk.Kind.TEXT)
                .map(ChatChunk::delta)
                .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                .toString();
    }

    /** 动态模型 + 动态工具：天气小助手应调用 get_weather / get_current_time 回答 */
    @Test
    void streamChatWithTools() {
        AgentInfo agent = agentInfoService.lambdaQuery().eq(AgentInfo::getName, "天气小助手").one();
        List<ChatChunk> chunks = chatChunks(chatService.newSessionId(), agent.getId(),
                "北京现在天气怎么样？今天几号？");
        String reply = chunks.stream()
                .filter(c -> c.kind() == ChatChunk.Kind.TEXT)
                .map(ChatChunk::delta)
                .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                .toString();
        System.out.println("[工具调用回复] " + reply);
        assertFalse(reply.isBlank());
        //  mock 工具返回“气温 N℃”“yyyy-MM-dd”，回复中出现即证明工具被真正调用
        assertTrue(reply.contains("℃") || reply.matches(".*\\d{4}[-年]\\d{1,2}.*"),
                "回复中应包含工具返回的天气或日期信息");
        // 流中应携带完整的工具调用过程信息：开始 -> 结束（成功）
        assertTrue(chunks.stream().anyMatch(c -> c.kind() == ChatChunk.Kind.TOOL_CALL_START),
                "应输出工具调用开始信息");
        assertTrue(chunks.stream().anyMatch(c -> c.kind() == ChatChunk.Kind.TOOL_CALL_END
                && "success".equals(c.delta())), "工具应执行成功");
    }

    /** 动态系统提示词：智能体应按配置的人设回答 */
    @Test
    void dynamicSysPrompt() {
        AgentInfo agent = agentInfoService.lambdaQuery().eq(AgentInfo::getName, "天气小助手").one();
        String reply = chat(chatService.newSessionId(), agent.getId(), "你是谁？");
        System.out.println("[人设回复] " + reply);
        assertFalse(reply.isBlank());
        assertTrue(reply.contains("天气"), "回复应体现天气助手人设");
    }

    /** 会话记忆：同 sessionId 记得上下文；新 sessionId 不记得（用生僻词防止模型瞎猜） */
    @Test
    void sessionMemory() {
        String token = "荧惑星城";
        String sessionId = chatService.newSessionId();
        chat(sessionId, null, "记住：我最喜欢的城市是" + token + "。");
        String remembered = chat(sessionId, null, "我最喜欢的城市是哪个？");
        System.out.println("[同会话回复] " + remembered);
        assertTrue(remembered.contains(token), "同一会话应记得上下文");

        String newSession = chat(chatService.newSessionId(), null, "我最喜欢的城市是哪个？");
        System.out.println("[新会话回复] " + newSession);
        assertFalse(newSession.contains(token), "新会话不应记得旧会话内容");
    }
}

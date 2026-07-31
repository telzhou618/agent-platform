package com.example.agent;

import cn.hutool.core.util.StrUtil;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HITL 回归测试：总是要求人工确认的工具（模拟非只读 MCP 工具的默认行为），
 * 在 PermissionMode.BYPASS 下应直接执行。
 * 锁定「MCP 工具调用挂起、继续对话报 Agent is paused」问题的修复。
 */
@SpringBootTest
class PermissionBypassTest {

    @Autowired
    private Model defaultModel;

    @BeforeAll
    static void requireApiKey() {
        Assumptions.assumeTrue(StrUtil.isNotBlank(System.getenv("DASHSCOPE_API_KEY")),
                "未设置 DASHSCOPE_API_KEY，跳过端到端测试");
    }

    /** 总是要求人工确认的工具 */
    private static class AlwaysAskTool extends ToolBase {

        AlwaysAskTool() {
            super(ToolBase.builder()
                    .name("get_secret_number")
                    .description("获取秘密数字")
                    .inputSchema(Map.of("type", "object", "properties", Map.of())));
        }

        @Override
        public Mono<PermissionDecision> checkPermissions(Map<String, Object> toolInput,
                                                         PermissionContextState context) {
            return Mono.just(PermissionDecision.ask("模拟非只读 MCP 工具：调用前需授权"));
        }

        @Override
        public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            return Mono.just(ToolResultBlock.text("42"));
        }
    }

    @Test
    void bypassModeExecutesAskTool() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerAgentTool(new AlwaysAskTool());
        ReActAgent agent = ReActAgent.builder()
                .name("permission-test")
                .sysPrompt("你可以调用工具 get_secret_number 获取秘密数字，用户问起时必须调用该工具。")
                .model(defaultModel)
                .toolkit(toolkit)
                .build();
        RuntimeContext ctx = RuntimeContext.builder()
                .userId("test")
                .sessionId("permission-bypass-test")
                .build();
        // ChatService 中的修复：逐会话设置 BYPASS，工具调用不再挂起等待人工确认
        agent.setPermissionMode(ctx, PermissionMode.BYPASS);

        String reply = agent.streamEvents(new UserMessage("秘密数字是多少？请调用工具获取。"), ctx)
                .filter(TextBlockDeltaEvent.class::isInstance)
                .map(e -> ((TextBlockDeltaEvent) e).getDelta())
                .collect(StringBuilder::new, StringBuilder::append)
                .block()
                .toString();
        System.out.println("[BYPASS 回复] " + reply);
        assertTrue(reply.contains("42"), "BYPASS 下工具应直接执行并在回复中给出结果 42");
    }
}

package com.example.agent.controller;

import com.alibaba.fastjson2.JSON;
import com.example.agent.common.Result;
import com.example.agent.controller.dto.AgentSession;
import com.example.agent.controller.dto.AgentSseEvent;
import com.example.agent.controller.dto.ChatRequest;
import com.example.agent.system.agent.AgentProxyException;
import com.example.agent.system.agent.AgentProxyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.Msg;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 智能体代理开放接口：供外部系统调用，不经过管理端登录（sa-token），
 * 统一通过请求头 X-Api-Key 鉴权，并按 key 归属用户校验智能体访问权限。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Tag(name = "智能体代理接口", description = "对外开放，请求头 X-Api-Key 鉴权，无需登录管理端")
public class AgentProxyController {

    /** ApiKey 请求头名 */
    public static final String API_KEY_HEADER = "X-Api-Key";

    private final AgentProxyService agentProxyService;
    private final ObjectMapper objectMapper;

    /**
     * 流式对话：SSE 流式返回 AgentSseEvent 事件序列（data 为 JSON 字符串）
     * （agent_start → thinking / text_block / tool_call / tool_result … → agent_result → agent_end）。
     * 鉴权失败在流开始前返回 JSON 错误体（HTTP 401）；流内异常以 event=error 事件下发。
     */
    @Operation(summary = "流式对话",
            description = "SSE 流式返回 AgentSseEvent 事件序列（data 为 JSON 字符串）；鉴权失败返回 JSON 错误体（HTTP 401）")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(
            @RequestHeader(name = API_KEY_HEADER, required = false) String apiKey,
            @RequestBody ChatRequest request,
            HttpServletResponse response) throws IOException {
        Flux<AgentSseEvent> events;
        try {
            events = agentProxyService.streamChat(apiKey, request);
        } catch (AgentProxyException e) {
            // produces=text/event-stream 下 Result 走不通消息转换器，直接手写 401 错误体
            writeUnauthorized(response, e.getMessage());
            return null;
        }
        return events
                .map(e -> ServerSentEvent.<String>builder()
                        .data(JSON.toJSONString(e))
                        .build())
                .onErrorResume(error -> {
                    log.error("Agent 流执行出错", error);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data(error.getMessage())
                            .build());
                });
    }

    /** 手写 401 JSON 错误体（SSE 端点鉴权失败场景，绕开 text/event-stream 内容协商） */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.unauthorized(message)));
        response.getWriter().flush();
    }

    /** 会话列表：按 userId 列出该用户与指定智能体的全部会话 */
    @Operation(summary = "会话列表", description = "按 userId 列出该用户与指定智能体的全部会话")
    @GetMapping("/sessions")
    public Result<List<AgentSession>> sessions(
            @RequestHeader(name = API_KEY_HEADER, required = false) String apiKey,
            @RequestParam Long agentId,
            @RequestParam String userId) {
        return Result.okData(agentProxyService.listSessions(apiKey, agentId, userId));
    }

    /** 会话详情：一个会话的全部历史消息（AgentScope Msg 原样返回，不分页） */
    @Operation(summary = "会话详情", description = "一个会话的全部历史消息（AgentScope Msg 原样返回，不分页）")
    @GetMapping("/session/messages")
    public Result<List<Msg>> messages(@RequestHeader(name = API_KEY_HEADER, required = false) String apiKey,
                                      @RequestParam Long agentId,
                                      @RequestParam String userId,
                                      @RequestParam String sessionId) {
        return Result.okData(agentProxyService.listMessages(apiKey, agentId, userId, sessionId));
    }

    /** 删除会话 */
    @Operation(summary = "删除会话")
    @DeleteMapping("/session")
    public Result<Void> deleteSession(@RequestHeader(name = API_KEY_HEADER, required = false) String apiKey,
                                      @RequestParam Long agentId,
                                      @RequestParam String userId,
                                      @RequestParam String sessionId) {
        agentProxyService.deleteSession(apiKey, agentId, userId, sessionId);
        return Result.ok();
    }

    /** 中断会话：协作式中断正在进行的回复；会话未在运行时无实际效果 */
    @Operation(summary = "中断会话", description = "协作式中断正在进行的回复；会话未在运行时无实际效果")
    @PostMapping("/session/interrupt")
    public Result<Void> interruptSession(
            @RequestHeader(name = API_KEY_HEADER, required = false) String apiKey,
            @RequestParam Long agentId,
            @RequestParam String userId,
            @RequestParam String sessionId) {
        agentProxyService.interruptSession(apiKey, agentId, userId, sessionId);
        return Result.ok();
    }

    /** 鉴权/参数异常统一返回 code=401（HTTP 200） */
    @ExceptionHandler(AgentProxyException.class)
    public Result<Void> handleProxyException(AgentProxyException e) {
        return Result.unauthorized(e.getMessage());
    }
}

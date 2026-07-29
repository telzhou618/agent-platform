package com.example.agent.system.agent;

import lombok.Getter;

/**
 * 代理开放接口鉴权/参数异常：消息原样返回给调用方。
 * 错误码同时用作 HTTP 状态码与 Result.code，默认 401（鉴权类失败）。
 */
@Getter
public class AgentProxyException extends RuntimeException {

    private final int code;

    public AgentProxyException(String message) {
        this(401, message);
    }

    public AgentProxyException(int code, String message) {
        super(message);
        this.code = code;
    }
}

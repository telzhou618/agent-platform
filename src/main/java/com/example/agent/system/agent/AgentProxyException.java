package com.example.agent.system.agent;

/**
 * 代理开放接口鉴权/参数异常：消息原样返回给调用方（code=401）
 */
public class AgentProxyException extends RuntimeException {

    public AgentProxyException(String message) {
        super(message);
    }
}

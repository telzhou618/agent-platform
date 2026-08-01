package com.example.agent.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import reactor.core.publisher.Mono;

/**
 * Redis 健康检查：覆盖默认实现。
 * 默认的 RedisReactiveHealthIndicator 用 INFO 命令 + java.util.Properties 解析返回，
 * Redis 运行在 Windows 且安装/数据路径含反斜杠（如 C:\\Users\\...）时，
 * Properties 把 \\u 当 Unicode 转义解析，抛 Malformed \\uxxxx encoding 导致健康检查误报 DOWN。
 * 这里改为 PING 探活，语义不变（能连通即 UP），readiness 探针仍汇聚 Redis 状态。
 */
@Configuration
public class RedisHealthConfig {

    @Bean("redisHealthIndicator")
    public ReactiveHealthIndicator redisHealthIndicator(ReactiveRedisConnectionFactory factory) {
        return () -> factory.getReactiveConnection().ping()
                .map(pong -> Health.up().build())
                .onErrorResume(ex -> Mono.just(Health.down(ex).build()));
    }
}

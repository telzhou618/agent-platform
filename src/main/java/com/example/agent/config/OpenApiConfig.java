package com.example.agent.config;

import com.example.agent.proxy.AgentProxyController;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / springdoc 接口文档配置：UI 入口 /doc.html，OpenAPI JSON 在 /v3/api-docs。
 * 只覆盖对外开放接口（/api/**）；文档走的是普通 MVC/静态资源，不受 Vaadin 登录守卫影响，无需登录。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI agentPlatformOpenApi() {
        String header = AgentProxyController.API_KEY_HEADER;
        return new OpenAPI()
                .info(new Info()
                        .title("agent-platform 开放接口")
                        .version("1.0.0")
                        .description("智能体代理聊天接口。所有接口需在请求头传入 `" + header
                                + "`（ApiKey 管理页创建），并按 key 归属用户校验智能体访问权限。"))
                .components(new Components().addSecuritySchemes(header, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(header)))
                .addSecurityItem(new SecurityRequirement().addList(header));
    }

    @Bean
    public GroupedOpenApi agentProxyApi() {
        return GroupedOpenApi.builder()
                .group("智能体代理接口")
                .pathsToMatch("/api/**")
                .build();
    }
}

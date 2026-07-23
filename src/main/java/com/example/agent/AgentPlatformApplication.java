package com.example.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.agent.system.mapper")
public class AgentPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentPlatformApplication.class, args);
    }
}

package com.example.agent.system.agent;

import cn.hutool.core.util.StrUtil;
import com.example.agent.config.StateStoreProperties;
import com.example.agent.system.dto.StateStoreInfo;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * AgentStateStore 工厂：按类型为智能体构建会话状态存储，四种实现之间数据互相隔离。
 * <ul>
 *   <li>memory：每个智能体独立内存实例，天然隔离（重建/重启丢会话）</li>
 *   <li>jsonfile：每智能体一个子目录（json-dir/agent-&lt;id&gt;）</li>
 *   <li>redis：共享一个 RedisClient，每智能体独立 key 前缀</li>
 *   <li>mysql：所有智能体共用一张表，官方按 userId:sessionId 寻址天然隔离</li>
 * </ul>
 * 同时提供数据存储页所需的配置信息与可用性检测。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentStateStoreFactory {

    public static final String TYPE_MEMORY = "memory";
    public static final String TYPE_JSONFILE = "jsonfile";
    public static final String TYPE_REDIS = "redis";
    public static final String TYPE_MYSQL = "mysql";

    /** 默认存储：本地 JSON 文件（与 AgentScope HarnessAgent 默认行为一致，落盘可恢复） */
    public static final String TYPE_DEFAULT = TYPE_JSONFILE;

    private final StateStoreProperties properties;
    private final DataSource dataSource;
    private final RedisProperties redisProperties;

    /** 全部 redis 存储共享的客户端（lettuce 连接按会话池化，懒加载构建） */
    private volatile RedisClient redisClient;

    /**
     * 按类型为指定智能体构建状态存储；类型未知或构建失败时回退本地 JSON 文件并记警告。
     */
    public AgentStateStore create(String type, Long agentId) {
        try {
            return switch (StrUtil.blankToDefault(type, TYPE_DEFAULT)) {
                case TYPE_MEMORY -> new InMemoryAgentStateStore();
                case TYPE_JSONFILE -> jsonFileStore(agentId);
                case TYPE_REDIS -> RedisAgentStateStore.builder()
                        .lettuceClient(redisClient())
                        .keyPrefix(properties.getRedisKeyPrefix() + ":agent-" + agentId + ":")
                        .build();
                case TYPE_MYSQL -> new MysqlAgentStateStore(dataSource,
                        mysqlDatabase(), properties.getMysqlTable(), true);
                default -> throw new IllegalArgumentException("未知存储类型 " + type);
            };
        } catch (Exception e) {
            log.warn("构建智能体 id={} 的状态存储（{}）失败，回退本地 JSON 文件：{}", agentId, type, e.getMessage());
            return jsonFileStore(agentId);
        }
    }

    /**
     * 数据存储页展示：四种存储的基本信息 + 实时可用性 + 使用中的智能体数
     *
     * @param usage 存储类型 -> 使用该存储的智能体数
     */
    public List<StateStoreInfo> listStores(Map<String, Long> usage) {
        return List.of(
                new StateStoreInfo(TYPE_MEMORY, "内存存储",
                        "会话状态保存在 JVM 内存；重启或编辑智能体后丢失，仅适合演示",
                        "JVM 进程内存", true, "无需外部服务", usage.getOrDefault(TYPE_MEMORY, 0L)),
                new StateStoreInfo(TYPE_JSONFILE, "本地 JSON 文件",
                        "按智能体分目录落盘，单机可恢复，默认方式",
                        "目录：" + jsonDir() + "/agent-<id>", true, "无需外部服务",
                        usage.getOrDefault(TYPE_JSONFILE, 0L)),
                redisInfo(usage.getOrDefault(TYPE_REDIS, 0L)),
                mysqlInfo(usage.getOrDefault(TYPE_MYSQL, 0L)));
    }

    /** Redis 存储信息 + PING 检测 */
    private StateStoreInfo redisInfo(long agentCount) {
        String config = redisProperties.getHost() + ":" + redisProperties.getPort()
                + "/" + redisProperties.getDatabase() + "，key 前缀：" + properties.getRedisKeyPrefix() + ":agent-<id>:";
        boolean available = true;
        String detail = "PING 正常";
        // 只开一条短连接做检测，不动共享 client
        try (StatefulRedisConnection<String, String> conn = redisClient().connect()) {
            detail = "PING -> " + conn.sync().ping();
        } catch (Exception e) {
            available = false;
            detail = "连接失败：" + e.getMessage();
        }
        return new StateStoreInfo(TYPE_REDIS, "Redis",
                "分布式共享，多副本/重启均可恢复，生产推荐",
                config, available, detail, agentCount);
    }

    /** MySQL 存储信息 + SELECT 1 检测 */
    private StateStoreInfo mysqlInfo(long agentCount) {
        String database = mysqlDatabase();
        String config = "库表：" + database + "." + properties.getMysqlTable()
                + "（与主库同库，表不存在自动创建），按 userId:sessionId 寻址";
        boolean available = true;
        String detail = "连接正常";
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute("SELECT 1");
        } catch (Exception e) {
            available = false;
            detail = "连接失败：" + e.getMessage();
        }
        return new StateStoreInfo(TYPE_MYSQL, "MySQL",
                "状态沉淀到关系库，便于审计与查询",
                config, available, detail, agentCount);
    }

    /**
     * MySQL 状态库名：配置项优先；默认取主数据源连接当前的库名（与项目主库同库），
     * 读取失败时回退主库常用名 agent_platform
     */
    private String mysqlDatabase() {
        if (StrUtil.isNotBlank(properties.getMysqlDatabase())) {
            return properties.getMysqlDatabase();
        }
        try (Connection conn = dataSource.getConnection()) {
            String catalog = conn.getCatalog();
            if (StrUtil.isNotBlank(catalog)) {
                return catalog;
            }
        } catch (Exception e) {
            log.warn("读取主数据源库名失败，回退 agent_platform：{}", e.getMessage());
        }
        return "agent_platform";
    }

    private AgentStateStore jsonFileStore(Long agentId) {
        return new JsonFileAgentStateStore(Path.of(jsonDir(), "agent-" + agentId));
    }

    private String jsonDir() {
        return StrUtil.blankToDefault(properties.getJsonDir(), "workspaces/state");
    }

    /** 共享 RedisClient：连接参数复用 spring.data.redis.*，首次使用时构建 */
    private RedisClient redisClient() {
        if (redisClient == null) {
            synchronized (this) {
                if (redisClient == null) {
                    RedisURI.Builder uri = RedisURI.builder()
                            .withHost(redisProperties.getHost())
                            .withPort(redisProperties.getPort())
                            .withDatabase(redisProperties.getDatabase());
                    if (StrUtil.isNotBlank(redisProperties.getUsername())) {
                        uri.withAuthentication(redisProperties.getUsername(),
                                StrUtil.nullToEmpty(redisProperties.getPassword()));
                    } else if (StrUtil.isNotBlank(redisProperties.getPassword())) {
                        uri.withPassword(redisProperties.getPassword().toCharArray());
                    }
                    redisClient = RedisClient.create(uri.build());
                }
            }
        }
        return redisClient;
    }

    @PreDestroy
    void shutdown() {
        if (redisClient != null) {
            redisClient.shutdown();
            redisClient = null;
        }
    }
}

package com.example.agent.system.dto;

/**
 * 数据存储（AgentStateStore）展示信息：数据存储页用
 *
 * @param key             类型标识：memory/jsonfile/redis/mysql
 * @param name            展示名
 * @param description     说明
 * @param config          基本配置信息（目录 / 地址 / 库表等）
 * @param available       可用性检测结果
 * @param availableDetail 可用性说明（失败时的原因）
 * @param agentCount      使用该存储的智能体数
 */
public record StateStoreInfo(String key, String name, String description, String config,
                             boolean available, String availableDetail, long agentCount) {
}

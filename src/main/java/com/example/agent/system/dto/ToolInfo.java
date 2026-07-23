package com.example.agent.system.dto;

import lombok.Data;

/** 系统工具信息（由 @Tool 注解解析而来，不落库） */
@Data
public class ToolInfo {

    /** 工具名称 */
    private String name;

    /** 工具描述 */
    private String description;

    /** 参数 JSON Schema */
    private String paramsJson;

    /** 工具类型：系统工具 */
    private String type;

    /** 来源类 */
    private String sourceClass;
}

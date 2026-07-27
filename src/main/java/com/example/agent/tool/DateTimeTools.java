package com.example.agent.tool;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 日期时间示例工具
 */
@Component
public class DateTimeTools {

    @Tool(name = "get_current_date_time", description = "获取当前日期和时间（yyyy-MM-dd HH:mm:ss）", readOnly = true, concurrencySafe = true)
    public String getCurrentDateTime() {
        return DateUtil.format(new Date(), DatePattern.NORM_DATETIME_PATTERN);
    }
}

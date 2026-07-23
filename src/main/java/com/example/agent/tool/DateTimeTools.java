package com.example.agent.tool;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/** 日期时间示例工具 */
@Component
public class DateTimeTools {

    @Tool(name = "get_current_date", description = "获取当前日期（yyyy-MM-dd）", readOnly = true, concurrencySafe = true)
    public String getCurrentDate() {
        return DateUtil.format(new Date(), DatePattern.NORM_DATE_PATTERN);
    }

    @Tool(name = "get_current_time", description = "获取指定时区的当前时间", readOnly = true, concurrencySafe = true)
    public String getCurrentTime(
            @ToolParam(name = "timezone", description = "IANA 时区，如 Asia/Shanghai；留空默认 Asia/Shanghai") String timezone) {
        ZoneId zone = timezone == null || timezone.isBlank() ? ZoneId.of("Asia/Shanghai") : ZoneId.of(timezone);
        return DateUtil.format(LocalDateTime.now(zone), DatePattern.NORM_DATETIME_PATTERN);
    }
}

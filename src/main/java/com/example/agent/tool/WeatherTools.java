package com.example.agent.tool;

import cn.hutool.core.util.RandomUtil;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

/** 天气查询示例工具 */
@Component
public class WeatherTools {

    /** 示例实现：返回模拟天气数据，将来可替换为真实天气 API */
    @Tool(name = "get_weather", description = "查询指定城市的天气情况", readOnly = true, concurrencySafe = true)
    public String getWeather(
            @ToolParam(name = "city", description = "城市名称，如 北京、上海") String city) {
        String[] weathers = {"晴", "多云", "阴", "小雨", "雷阵雨"};
        String weather = weathers[RandomUtil.randomInt(weathers.length)];
        int temp = RandomUtil.randomInt(-5, 36);
        return city + "今天" + weather + "，气温 " + temp + "℃";
    }
}

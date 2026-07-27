package com.example.agent.tool;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 天气查询工具（wttr.in 免费服务，无需 Key）
 */
@Component
public class WeatherTools {

    /**
     * wttr.in JSON 接口：免费无需 Key；需非浏览器 UA 才返回 JSON 而非网页
     */
    private static final String WEATHER_URL = "https://wttr.in/%s?format=j1&lang=zh";
    private static final int TIMEOUT_MS = 15_000;

    @Tool(name = "get_weather", description = "查询指定城市的实时天气，返回天气状况、气温、体感温度、湿度、风力和当日最高最低气温",
            readOnly = true, concurrencySafe = true)
    public String getWeather(
            @ToolParam(name = "city", description = "城市名称，如 北京、上海、Beijing") String city) {
        if (StrUtil.isBlank(city)) {
            return "城市名称不能为空";
        }
        try {
            String url = String.format(WEATHER_URL, URLEncoder.encode(city.trim(), StandardCharsets.UTF_8));
            String body = HttpRequest.get(url)
                    .header("User-Agent", "curl/8.0")
                    .timeout(TIMEOUT_MS)
                    .execute()
                    .body();
            return parseWeather(city.trim(), body);
        } catch (Exception e) {
            return "天气查询失败：" + e.getMessage();
        }
    }

    /**
     * 解析 j1 JSON 为简明文本；响应异常（限流页/错误页）或无数据时返回提示
     */
    private String parseWeather(String city, String body) {
        if (StrUtil.isBlank(body) || !body.contains("current_condition")) {
            return "未查询到「" + city + "」的天气信息";
        }
        JSONObject root = JSONUtil.parseObj(body);
        JSONArray current = root.getJSONArray("current_condition");
        if (current == null || current.isEmpty()) {
            return "未查询到「" + city + "」的天气信息";
        }
        JSONObject cond = current.getJSONObject(0);
        String area = city;
        JSONArray nearest = root.getJSONArray("nearest_area");
        if (nearest != null && !nearest.isEmpty()) {
            JSONObject area0 = nearest.getJSONObject(0);
            area = firstValue(area0, "areaName") + "，" + firstValue(area0, "country");
        }
        StringBuilder sb = new StringBuilder()
                .append(area).append(" 当前天气：").append(firstValue(cond, "weatherDesc"))
                .append("，气温 ").append(cond.getStr("temp_C")).append("°C")
                .append("（体感 ").append(cond.getStr("FeelsLikeC")).append("°C）")
                .append("，湿度 ").append(cond.getStr("humidity")).append("%")
                .append("，风 ").append(cond.getStr("winddir16Point")).append(' ')
                .append(cond.getStr("windspeedKmph")).append("km/h");
        JSONArray weather = root.getJSONArray("weather");
        if (weather != null && !weather.isEmpty()) {
            JSONObject today = weather.getJSONObject(0);
            sb.append("；今日 ").append(today.getStr("mintempC")).append("~")
                    .append(today.getStr("maxtempC")).append("°C");
        }
        return sb.toString();
    }

    /**
     * 取 [{"value": "..."}] 结构的首个 value，缺失返回空串
     */
    private String firstValue(JSONObject obj, String key) {
        JSONArray arr = obj.getJSONArray(key);
        return arr == null || arr.isEmpty() ? "" : StrUtil.nullToEmpty(arr.getJSONObject(0).getStr("value")).trim();
    }
}

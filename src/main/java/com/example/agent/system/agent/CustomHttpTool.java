package com.example.agent.system.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.agent.system.entity.CustomTool;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用 HTTP 代理工具：按 custom_tool 配置把一次工具调用翻译成远程接口调用。
 * URL 支持 {参数名} 路径占位符；GET/DELETE 参数进 query，POST/PUT 按 requestType 进 JSON body 或表单。
 */
public class CustomHttpTool implements AgentTool {

    /** 返回给模型的响应体最大长度，防止超大响应撑爆上下文 */
    private static final int MAX_BODY_LENGTH = 8000;
    private static final int TIMEOUT_MILLIS = 30000;

    private final CustomTool config;
    private final List<ParamDef> paramDefs;

    public CustomHttpTool(CustomTool config) {
        this.config = config;
        this.paramDefs = parseParams(config.getParams());
    }

    @Override
    public String getName() {
        return config.getToolKey();
    }

    @Override
    public String getDescription() {
        return config.getDescription();
    }

    /** GET 只读查询，不做人工确认；写操作由平台按 PermissionMode 控制 */
    @Override
    public boolean isReadOnly() {
        return "GET".equalsIgnoreCase(config.getMethod());
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (ParamDef p : paramDefs) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", switch (p.type()) {
                case "number" -> "number";
                case "boolean" -> "boolean";
                default -> "string";
            });
            if (StrUtil.isNotBlank(p.description())) {
                prop.put("description", p.description());
            }
            properties.put(p.name(), prop);
            if (p.required()) {
                required.add(p.name());
            }
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> execute(param.getInput() == null ? Map.of() : param.getInput()))
                .onErrorResume(e -> Mono.just(ToolResultBlock.error("自定义工具调用失败：" + e.getMessage())));
    }

    /** 发起远程调用，返回结果块 */
    private ToolResultBlock execute(Map<String, Object> args) {
        Map<String, Object> remaining = new LinkedHashMap<>(args);
        String url = substitutePathParams(config.getUrl(), remaining);

        cn.hutool.http.HttpRequest request = cn.hutool.http.HttpRequest
                .of(url)
                .method(cn.hutool.http.Method.valueOf(StrUtil.blankToDefault(config.getMethod(), "GET").toUpperCase()))
                .timeout(TIMEOUT_MILLIS);
        if (StrUtil.isNotBlank(config.getHeaders())) {
            Map<String, String> headers = new HashMap<>();
            JSONUtil.parseObj(config.getHeaders())
                    .forEach((k, v) -> headers.put(k, String.valueOf(v)));
            request.headerMap(headers, true);
        }
        String method = StrUtil.blankToDefault(config.getMethod(), "GET").toUpperCase();
        if ("GET".equals(method) || "DELETE".equals(method)) {
            if (!remaining.isEmpty()) {
                request.form(remaining);
            }
        } else if ("form".equalsIgnoreCase(config.getRequestType())) {
            if (!remaining.isEmpty()) {
                request.form(remaining);
            }
        } else {
            request.body(JSONUtil.toJsonStr(remaining), "application/json");
        }

        try (cn.hutool.http.HttpResponse response = request.execute()) {
            String body = StrUtil.maxLength(StrUtil.nullToEmpty(response.body()), MAX_BODY_LENGTH);
            if (response.getStatus() >= 400) {
                return ToolResultBlock.error("远程接口返回 HTTP " + response.getStatus() + "：" + body);
            }
            return ToolResultBlock.text(body);
        }
    }

    /** 替换 URL 中的 {参数名} 占位符（URL 编码），并从待发送参数中移除已消费的项 */
    private String substitutePathParams(String url, Map<String, Object> args) {
        String result = url;
        for (ParamDef p : paramDefs) {
            String placeholder = "{" + p.name() + "}";
            if (result.contains(placeholder)) {
                Object value = args.remove(p.name());
                if (value == null) {
                    throw new IllegalArgumentException("缺少路径参数 " + p.name());
                }
                result = result.replace(placeholder,
                        URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    /** 解析参数定义 JSON 数组；非法行跳过 */
    private static List<ParamDef> parseParams(String paramsJson) {
        if (StrUtil.isBlank(paramsJson)) {
            return List.of();
        }
        List<ParamDef> defs = new ArrayList<>();
        JSONArray array = JSONUtil.parseArray(paramsJson);
        for (int i = 0; i < array.size(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String name = obj.getStr("name");
            if (StrUtil.isBlank(name)) {
                continue;
            }
            defs.add(new ParamDef(name, StrUtil.blankToDefault(obj.getStr("type"), "string"),
                    obj.getStr("description"), obj.getBool("required", false)));
        }
        return defs;
    }

    private record ParamDef(String name, String type, String description, boolean required) {
    }
}

package com.example.agent.system.agent;

import cn.hutool.core.util.StrUtil;
import com.example.agent.system.entity.ModelConfig;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 模型可用性验证：按配置真实构建模型并发起一次最小调用（"ping"，maxTokens=1），
 * 调用失败视为不可用，抛出带原因的异常；超时 30 秒。
 */
@Component
@RequiredArgsConstructor
public class ModelAvailabilityChecker {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ModelFactory modelFactory;

    /**
     * 验证模型可用性；不可用抛 IllegalArgumentException（消息含原因，可直接展示给用户）
     */
    public void check(ModelConfig config) {
        Model model = modelFactory.buildStrict(config);
        try {
            model.stream(List.of(new UserMessage("ping")), List.of(),
                            GenerateOptions.builder().maxTokens(1).build())
                    .blockFirst(TIMEOUT);
        } catch (Exception e) {
            throw new IllegalArgumentException("模型不可用：" + rootMessage(e));
        }
    }

    /**
     * 取异常链最底层的原因信息，去掉堆栈噪音；reactor 超时转成人话
     */
    private String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message != null && message.contains("Timeout on blocking read")) {
            return "调用超时（" + TIMEOUT.getSeconds() + " 秒无响应）";
        }
        return StrUtil.blankToDefault(message, e.getClass().getSimpleName());
    }
}

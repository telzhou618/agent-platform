package com.example.agent.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** 某一天的 token 消耗（趋势图数据来源） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyTokenUsage {

    /** 日期 */
    private LocalDate date;
    /** 输入 token 数 */
    private Long inputTokens;
    /** 输出 token 数 */
    private Long outputTokens;

    /** 总 token 数（input + output） */
    public long getTotalTokens() {
        return (inputTokens == null ? 0 : inputTokens) + (outputTokens == null ? 0 : outputTokens);
    }
}

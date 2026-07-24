package com.example.agent.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

/** 某一天的对话轮数（dashboard 近 7 日趋势用） */
@Data
@AllArgsConstructor
public class DailyCount {

    private LocalDate date;

    private Long count;
}

package com.example.agent.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.agent.system.agent.AgentRegistry;
import com.example.agent.system.entity.CustomTool;
import com.example.agent.system.log.OperationLog;
import com.example.agent.system.mapper.CustomToolMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomToolService extends ServiceImpl<CustomToolMapper, CustomTool> {

    /** 工具标识格式：小写字母开头，小写字母/数字/下划线（模型调用名约束） */
    public static final String TOOL_KEY_PATTERN = "^[a-z][a-z0-9_]{1,63}$";

    private final AgentRegistry agentRegistry;

    /** 分页查询自定义工具，关键字匹配标识 / 名称 / 描述 */
    public Page<CustomTool> pageCustomTools(String keyword, int page, int size) {
        return lambdaQuery()
                .and(StrUtil.isNotBlank(keyword), q -> q
                        .like(CustomTool::getToolKey, keyword).or()
                        .like(CustomTool::getName, keyword).or()
                        .like(CustomTool::getDescription, keyword))
                .orderByDesc(CustomTool::getCreateTime)
                .page(new Page<>(page, size));
    }

    /** 保存自定义工具（新增/编辑）：落库后级联重建引用它的智能体实例 */
    @OperationLog(module = "自定义工具", action = "保存", summary = "#customTool.toolKey")
    public void saveCustomTool(CustomTool customTool) {
        if (StrUtil.isBlank(customTool.getToolKey())
                || !customTool.getToolKey().matches(TOOL_KEY_PATTERN)) {
            throw new IllegalArgumentException("工具标识应为小写字母开头的小写字母/数字/下划线");
        }
        if (StrUtil.isBlank(customTool.getName())) {
            throw new IllegalArgumentException("工具名称不能为空");
        }
        if (StrUtil.isBlank(customTool.getDescription())) {
            throw new IllegalArgumentException("工具描述不能为空");
        }
        if (StrUtil.isBlank(customTool.getUrl())) {
            throw new IllegalArgumentException("接口地址不能为空");
        }
        long duplicates = lambdaQuery()
                .eq(CustomTool::getToolKey, customTool.getToolKey())
                .ne(customTool.getId() != null, CustomTool::getId, customTool.getId())
                .count();
        if (duplicates > 0) {
            throw new IllegalArgumentException("工具标识 " + customTool.getToolKey() + " 已存在");
        }
        saveOrUpdate(customTool);
        agentRegistry.onCustomToolChanged(customTool);
    }

    /** 删除自定义工具：落库后级联重建引用它的智能体实例（移除该工具） */
    @OperationLog(module = "自定义工具", action = "删除", summary = "#id")
    public void deleteCustomTool(Long id) {
        removeById(id);
        agentRegistry.onCustomToolDeleted(id);
    }
}

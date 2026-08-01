package com.example.agent.ui.view;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.example.agent.system.agent.AgentStateStoreFactory;
import com.example.agent.system.chat.ChatService;
import com.example.agent.system.dto.ToolInfo;
import com.example.agent.system.entity.AgentInfo;
import com.example.agent.system.entity.CustomTool;
import com.example.agent.system.entity.KnowledgeBase;
import com.example.agent.system.entity.McpServer;
import com.example.agent.system.entity.ModelConfig;
import com.example.agent.system.entity.SkillRepo;
import com.example.agent.system.service.AgentInfoService;
import com.example.agent.system.service.CustomToolService;
import com.example.agent.system.service.KnowledgeBaseService;
import com.example.agent.system.service.McpServerService;
import com.example.agent.system.service.ModelConfigService;
import com.example.agent.system.service.SkillRepoService;
import com.example.agent.system.service.ToolService;
import com.example.agent.ui.MainLayout;
import com.example.agent.ui.chat.ChatPanel;
import com.example.agent.ui.component.AgentAvatar;
import com.example.agent.ui.component.Notify;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 智能体配置页：新增/编辑智能体，主面板打开（非弹窗），左窄右宽分栏。
 * 左侧为返回按钮 + 8 个配置分区菜单 + 保存按钮；右侧为对应分区的配置卡片。
 * 路由：/agent-config/new（新增）、/agent-config/{id}（编辑），当前分区以 ?section= 记录，可直达。
 * 保存是重量级操作（注册/重建实例），先展示配置摘要让用户确认；保存成功停留在当前页。
 */
@Route(value = "agent-config", layout = MainLayout.class)
@PageTitle("智能体配置 - agent-platform")
@StyleSheet("context://styles/agent-config.css")
public class AgentConfigView extends HorizontalLayout implements HasUrlParameter<String> {

    private static final String SECTION_BASIC = "basic";
    private static final String SECTION_MODEL = "model";
    private static final String SECTION_PROMPT = "prompt";
    private static final String SECTION_TOOLS = "tools";
    private static final String SECTION_MCP = "mcp";
    private static final String SECTION_KNOWLEDGE = "knowledge";
    private static final String SECTION_SKILL = "skill";
    private static final String SECTION_STORAGE = "storage";
    private static final String SECTION_CHAT = "chat";

    /** 分区菜单项 */
    private record SectionDef(String key, String label, VaadinIcon icon) {
    }

    private static final List<SectionDef> SECTIONS = List.of(
            new SectionDef(SECTION_BASIC, "基础配置", VaadinIcon.USER),
            new SectionDef(SECTION_MODEL, "模型配置", VaadinIcon.COG),
            new SectionDef(SECTION_PROMPT, "系统提示词", VaadinIcon.FILE_TEXT),
            new SectionDef(SECTION_TOOLS, "工具配置", VaadinIcon.TOOLS),
            new SectionDef(SECTION_MCP, "MCP服务", VaadinIcon.BOLT),
            new SectionDef(SECTION_KNOWLEDGE, "知识库", VaadinIcon.BOOK),
            new SectionDef(SECTION_SKILL, "技能配置", VaadinIcon.LIGHTBULB),
            new SectionDef(SECTION_STORAGE, "存储配置", VaadinIcon.DATABASE),
            new SectionDef(SECTION_CHAT, "对话测试", VaadinIcon.CHAT));

    /** 会话状态存储 -> 展示名 */
    private static final Map<String, String> STATE_STORE_NAMES = new LinkedHashMap<>() {{
        put(AgentStateStoreFactory.TYPE_MEMORY, "内存 Memory（默认）");
        put(AgentStateStoreFactory.TYPE_JSONFILE, "本地 JSON 文件");
        put(AgentStateStoreFactory.TYPE_REDIS, "Redis");
        put(AgentStateStoreFactory.TYPE_MYSQL, "MySQL");
    }};

    /** 会话状态存储 -> 说明 */
    private static final Map<String, String> STATE_STORE_DESCS = new LinkedHashMap<>() {{
        put(AgentStateStoreFactory.TYPE_MEMORY, "会话状态保存在 JVM 内存；重启或编辑智能体后丢失，适合演示");
        put(AgentStateStoreFactory.TYPE_JSONFILE, "按智能体分目录落盘，单机可恢复");
        put(AgentStateStoreFactory.TYPE_REDIS, "分布式共享，多副本/重启均可恢复，生产推荐");
        put(AgentStateStoreFactory.TYPE_MYSQL, "状态沉淀到关系库（与主库同库），便于审计与查询");
    }};

    /** 知识库类型 -> 展示名 */
    private static final Map<String, String> KNOWLEDGE_TYPES = Map.of(
            KnowledgeBase.TYPE_BAILIAN, "阿里云百炼",
            KnowledgeBase.TYPE_DIFY, "Dify");

    /** MCP 传输类型 -> 展示名 */
    private static final Map<String, String> MCP_TYPES = Map.of(
            McpServer.TYPE_STREAMABLE_HTTP, "Streamable HTTP",
            McpServer.TYPE_SSE, "SSE");

    /** 技能仓库类型 -> 展示名 */
    private static final Map<String, String> SKILL_REPO_TYPES = Map.of(
            SkillRepo.TYPE_GIT, "Git 仓库",
            SkillRepo.TYPE_MYSQL, "MySQL",
            SkillRepo.TYPE_CLASSPATH, "Classpath");

    /** 新建智能体默认勾选的系统工具 */
    private static final Set<String> DEFAULT_TOOLS = Set.of("get_current_date_time", "web_search");

    /** 表情头像候选 */
    private static final List<String> EMOJIS = List.of(
            "🤖", "🧠", "🦾", "🛰️", "🚀", "🌟", "🔮", "💡",
            "🎯", "🧭", "📚", "✏️", "🗂️", "📊", "📈", "🔍",
            "🌤️", "☀️", "🌧️", "❄️", "⚡", "🌈", "🌍", "🌙",
            "🐱", "🐶", "🦊", "🐼", "🦁", "🐸", "🐵", "🦄",
            "🦉", "🐝", "🌸", "🌵", "🍀", "🍉", "☕", "🎧",
            "🎨", "🎮", "🏆", "⚙️", "🔧", "📦", "💬", "🛡️");

    private final AgentInfoService agentService;
    private final ChatService chatService;

    /** 可选数据快照（打开页面时加载一次） */
    private final List<ModelConfig> models;
    private final List<ToolInfo> systemTools;
    private final List<CustomTool> customTools;
    private final List<McpServer> mcpServers;
    private final List<KnowledgeBase> knowledgeBases;
    private final List<SkillRepo> skillRepos;
    /** ID -> 名称，保存确认摘要展示用 */
    private final Map<Long, String> customToolNames;
    private final Map<Long, String> mcpServerNames;
    private final Map<Long, String> knowledgeBaseNames;
    private final Map<Long, String> skillRepoNames;

    /** 正在编辑的智能体（保存时回写） */
    private AgentInfo agent;
    private boolean isNew;
    /** 有未保存修改 */
    private boolean dirty;

    // ---- 基础配置 ----
    /** 当前头像值（emoji 或图片 URL） */
    private String avatarValue;
    /** 程序化回填 URL 输入框时不触发头像变更 */
    private boolean suppressAvatarUrl;
    private Div avatarPreview;
    private TextField avatarUrlField;
    private TextField nameField;
    private TextArea descField;

    // ---- 模型配置 ----
    private ComboBox<ModelConfig> modelCombo;
    private NumberField temperatureField;
    private IntegerField contextCountField;
    private Checkbox topPToggle;
    private NumberField topPField;
    private Checkbox maxTokensToggle;
    private IntegerField maxTokensField;

    // ---- 提示词 / 存储 ----
    private TextArea sysPromptArea;
    private Select<String> stateStoreSelect;

    // ---- 多选关系（key 集合，保存时转 JSON 数组） ----
    private final Set<String> selectedToolNames = new LinkedHashSet<>();
    private final Set<Long> selectedCustomToolIds = new LinkedHashSet<>();
    private final Set<Long> selectedMcpIds = new LinkedHashSet<>();
    private final Set<Long> selectedKbIds = new LinkedHashSet<>();
    private final Set<Long> selectedSkillRepoIds = new LinkedHashSet<>();
    /** 各关系组的重渲染器（选中集变化或回填后刷新列表与计数） */
    private final List<Runnable> renderers = new ArrayList<>();

    // ---- 布局 ----
    private final Map<String, Component> sections = new LinkedHashMap<>();
    private final Map<String, Div> menuItems = new LinkedHashMap<>();
    private String currentSection = SECTION_BASIC;
    private Div agentBarAvatar;
    private Span agentBarName;
    private Span dirtyBadge;
    /** 对话测试分区容器（内容依赖当前智能体，setParameter 后填充） */
    private final Div chatSection = new Div();

    public AgentConfigView(AgentInfoService agentService, ModelConfigService modelService,
                           ToolService toolService, McpServerService mcpServerService,
                           KnowledgeBaseService knowledgeBaseService, SkillRepoService skillRepoService,
                           CustomToolService customToolService, ChatService chatService) {
        this.agentService = agentService;
        this.chatService = chatService;
        addClassName("ac-layout");
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        models = modelService.list();
        systemTools = toolService.listTools();
        customTools = customToolService.list();
        mcpServers = mcpServerService.list();
        knowledgeBases = knowledgeBaseService.list();
        skillRepos = skillRepoService.list();
        customToolNames = namesById(customTools, CustomTool::getId, CustomTool::getName);
        mcpServerNames = namesById(mcpServers, McpServer::getId, McpServer::getName);
        knowledgeBaseNames = namesById(knowledgeBases, KnowledgeBase::getId, KnowledgeBase::getName);
        skillRepoNames = namesById(skillRepos, SkillRepo::getId, SkillRepo::getName);

        sections.put(SECTION_BASIC, buildBasicSection());
        sections.put(SECTION_MODEL, buildModelSection());
        sections.put(SECTION_PROMPT, buildPromptSection());
        sections.put(SECTION_TOOLS, buildToolsSection());
        sections.put(SECTION_MCP, buildMcpSection());
        sections.put(SECTION_KNOWLEDGE, buildKnowledgeSection());
        sections.put(SECTION_SKILL, buildSkillSection());
        sections.put(SECTION_STORAGE, buildStorageSection());
        chatSection.addClassName("ac-chat");
        sections.put(SECTION_CHAT, chatSection);

        Div main = new Div();
        main.addClassName("ac-main");
        main.add(buildAgentBar());
        sections.values().forEach(section -> {
            section.setVisible(false);
            main.add(section);
        });
        add(buildSide(), main);
    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        isNew = StrUtil.isBlank(parameter) || "new".equals(parameter);
        if (isNew) {
            agent = new AgentInfo();
            // 新建默认勾选基础工具：当前时间、联网搜索
            systemTools.stream().map(ToolInfo::getName)
                    .filter(DEFAULT_TOOLS::contains)
                    .forEach(selectedToolNames::add);
        } else {
            agent = loadAgent(parameter);
            if (agent == null) {
                Notify.error("智能体不存在或已被删除");
                navigateToList();
                return;
            }
            selectedToolNames.addAll(parseStringArray(agent.getTools()));
            selectedCustomToolIds.addAll(parseIdArray(agent.getCustomTools()));
            selectedMcpIds.addAll(parseIdArray(agent.getMcpServers()));
            selectedKbIds.addAll(parseIdArray(agent.getKnowledgeBases()));
            selectedSkillRepoIds.addAll(parseIdArray(agent.getSkillRepos()));
        }
        populate();
        buildChatContent();
        selectSection(event.getLocation().getQueryParameters()
                .getSingleParameter("section").orElse(SECTION_BASIC));
        // 回填触发的脏标记复位
        dirty = false;
        dirtyBadge.setVisible(false);
    }

    private AgentInfo loadAgent(String parameter) {
        try {
            return agentService.getById(Long.parseLong(parameter));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 实体 -> 表单回填 */
    private void populate() {
        nameField.setValue(StrUtil.nullToEmpty(agent.getName()));
        descField.setValue(StrUtil.nullToEmpty(agent.getDescription()));
        avatarValue = agent.getAvatar();
        suppressAvatarUrl = true;
        avatarUrlField.setValue(AgentAvatar.isImageUrl(avatarValue) ? avatarValue : "");
        suppressAvatarUrl = false;
        updateAvatarPreview();

        modelCombo.setValue(agent.getModelId() == null ? null : models.stream()
                .filter(m -> m.getId().equals(agent.getModelId())).findFirst().orElse(null));
        temperatureField.setValue(agent.getTemperature() == null ? 1.0 : agent.getTemperature());
        contextCountField.setValue(agent.getContextCount() == null ? 5 : agent.getContextCount());
        topPToggle.setValue(agent.getTopP() != null);
        topPField.setValue(agent.getTopP() == null ? 0.9 : agent.getTopP());
        maxTokensToggle.setValue(agent.getMaxTokens() != null);
        maxTokensField.setValue(agent.getMaxTokens() == null ? 2048 : agent.getMaxTokens());
        sysPromptArea.setValue(StrUtil.nullToEmpty(agent.getSysPrompt()));
        stateStoreSelect.setValue(
                StrUtil.blankToDefault(agent.getStateStore(), AgentStateStoreFactory.TYPE_MEMORY));
        renderers.forEach(Runnable::run);
        refreshAgentBar();
    }

    // ==================== 左侧：返回 + 分区菜单 + 保存 ====================

    private Component buildSide() {
        Button back = new Button("返回列表", new Icon(VaadinIcon.ARROW_LEFT), e -> backToList());
        back.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        back.addClassName("ac-back");

        Div menu = new Div();
        menu.addClassName("ac-menu");
        for (SectionDef section : SECTIONS) {
            Div item = new Div(section.icon().create(), new Span(section.label()));
            item.addClassName("ac-menu-item");
            item.addClickListener(e -> selectSection(section.key()));
            menuItems.put(section.key(), item);
            menu.add(item);
        }

        Button save = new Button("保存智能体", new Icon(VaadinIcon.CHECK), e -> save());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClassName("ac-save");

        Div side = new Div(back, menu, save);
        side.addClassName("ac-side");
        return side;
    }

    private Component buildAgentBar() {
        agentBarAvatar = new Div();
        agentBarName = new Span();
        agentBarName.addClassName("ac-agent-bar-name");
        dirtyBadge = new Span("未保存");
        dirtyBadge.addClassName("ac-dirty-badge");
        dirtyBadge.setVisible(false);
        Div bar = new Div(agentBarAvatar, agentBarName, dirtyBadge);
        bar.addClassName("ac-agent-bar");
        return bar;
    }

    /** 切换右侧分区：更新内容可见性、菜单选中态与 URL（?section=，不重建页面） */
    private void selectSection(String section) {
        currentSection = sections.containsKey(section) ? section : SECTION_BASIC;
        sections.forEach((key, component) -> component.setVisible(key.equals(currentSection)));
        menuItems.forEach((key, item) -> {
            if (key.equals(currentSection)) {
                item.addClassName("ac-selected");
            } else {
                item.removeClassName("ac-selected");
            }
        });
        getUI().ifPresent(ui -> ui.getPage().getHistory().replaceState(null, currentPath()));
    }

    private String currentPath() {
        return "agent-config/" + (isNew || agent.getId() == null ? "new" : agent.getId())
                + "?section=" + currentSection;
    }

    /**
     * 对话测试分区内容：编辑态嵌入复用的对话面板（下拉锁定当前智能体）；
     * 新建未保存时没有智能体实例，先展示提示。对话使用的是最近一次保存的配置。
     */
    private void buildChatContent() {
        chatSection.removeAll();
        if (isNew || agent.getId() == null) {
            Span hint = new Span("保存智能体后可在此进行对话测试");
            hint.getStyle().set("color", "var(--lumo-secondary-text-color)");
            chatSection.add(hint);
            return;
        }
        ChatPanel chatPanel = new ChatPanel(List.of(agent), chatService, agent);
        chatPanel.lockAgentSelect();
        chatSection.add(chatPanel);
    }

    /** 返回列表：有未保存修改时先确认 */
    private void backToList() {
        if (!dirty) {
            navigateToList();
            return;
        }
        ConfirmDialog dialog = new ConfirmDialog("离开配置页",
                "有未保存的修改，确定离开吗？", "离开", e -> navigateToList());
        dialog.setConfirmButtonTheme("error primary");
        dialog.setCancelable(true);
        dialog.setCancelText("继续编辑");
        dialog.open();
    }

    private void navigateToList() {
        getUI().ifPresent(ui -> ui.navigate("agents"));
    }

    // ==================== 分区一：基础配置 ====================

    private Component buildBasicSection() {
        Div panel = panel(SECTION_BASIC);

        avatarPreview = new Div();
        avatarPreview.addClassName("ac-avatar-preview");
        Button pickEmoji = new Button("选择表情", new Icon(VaadinIcon.SMILEY_O), e -> openEmojiPicker());
        pickEmoji.addThemeVariants(ButtonVariant.LUMO_SMALL);
        avatarUrlField = new TextField();
        avatarUrlField.setPlaceholder("或输入头像图片 URL，如 https://example.com/avatar.png");
        avatarUrlField.setMaxLength(512);
        avatarUrlField.setClearButtonVisible(true);
        avatarUrlField.setHelperText("头像展示在智能体列表与对话页；点击「选择表情」快捷设置，或直接输入图片 URL（填写 URL 时优先展示）");
        avatarUrlField.addValueChangeListener(e -> {
            if (suppressAvatarUrl) {
                return;
            }
            String url = StrUtil.trimToEmpty(e.getValue());
            if (StrUtil.isBlank(url)) {
                // 清空 URL：当前头像是 URL 才清除（表情头像不受影响）
                if (AgentAvatar.isImageUrl(avatarValue)) {
                    avatarValue = null;
                }
            } else {
                avatarValue = url;
            }
            updateAvatarPreview();
            markDirty();
        });
        Div controls = new Div(pickEmoji, avatarUrlField);
        controls.addClassName("ac-avatar-controls");
        Div avatarRow = new Div(avatarPreview, controls);
        avatarRow.addClassName("ac-avatar-row");

        nameField = new TextField("名称");
        nameField.setRequiredIndicatorVisible(true);
        nameField.setMaxLength(64);
        nameField.setWidthFull();
        nameField.setPlaceholder("如：天气小助手");
        nameField.setHelperText("智能体的展示名称，必填，最长 64 字");
        nameField.addValueChangeListener(e -> {
            nameField.setInvalid(false);
            if (StrUtil.isBlank(avatarValue)) {
                updateAvatarPreview();
            }
            refreshAgentBar();
            markDirty();
        });

        descField = new TextArea("描述");
        descField.setMaxLength(256);
        descField.setWidthFull();
        descField.setMinHeight("5em");
        descField.setPlaceholder("说明这个智能体的用途、适用场景……");
        descField.setHelperText("展示在智能体列表页，最长 256 字");
        descField.addValueChangeListener(e -> markDirty());

        Div fields = new Div(avatarRow, nameField, descField);
        fields.addClassName("ac-fields");
        panel.add(fields);
        return panel;
    }

    /** 表情头像选择弹窗 */
    private void openEmojiPicker() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("选择头像表情");
        dialog.setWidth("460px");
        Div grid = new Div();
        grid.addClassName("ac-emoji-grid");
        for (String emoji : EMOJIS) {
            Button button = new Button(emoji);
            button.addClassName("ac-emoji-btn");
            button.addClickListener(e -> {
                // 选中表情后清掉 URL 输入框（表情优先）
                suppressAvatarUrl = true;
                avatarUrlField.clear();
                suppressAvatarUrl = false;
                avatarValue = emoji;
                updateAvatarPreview();
                markDirty();
                dialog.close();
            });
            grid.add(button);
        }
        dialog.add(grid);
        dialog.open();
    }

    // ==================== 分区二：模型配置 ====================

    private Component buildModelSection() {
        Div panel = panel(SECTION_MODEL);

        modelCombo = new ComboBox<>("模型");
        modelCombo.setItems(models);
        modelCombo.setItemLabelGenerator(m -> m.getName() + "（" + m.getModel() + "）");
        modelCombo.setRequiredIndicatorVisible(true);
        modelCombo.setWidth("420px");
        modelCombo.setHelperText("智能体使用的大模型，在「模型管理」中维护；模型被删除时回退全局默认模型");
        modelCombo.addValueChangeListener(e -> {
            modelCombo.setInvalid(false);
            markDirty();
        });

        temperatureField = new NumberField("模型温度");
        temperatureField.setMin(0);
        temperatureField.setMax(2);
        temperatureField.setStep(0.1);
        temperatureField.setStepButtonsVisible(true);
        temperatureField.setWidth("240px");
        temperatureField.setHelperText("控制输出的随机性，值越高输出越随机，值越低输出越确定。范围 0-2，默认 1.0");
        temperatureField.addValueChangeListener(e -> {
            temperatureField.setInvalid(false);
            markDirty();
        });

        contextCountField = new IntegerField("上下文数");
        contextCountField.setMin(1);
        contextCountField.setMax(20);
        contextCountField.setStep(1);
        contextCountField.setStepButtonsVisible(true);
        contextCountField.setWidth("240px");
        contextCountField.setHelperText("每次对话包含的历史消息数量。范围 1-20，默认 5；超出窗口的旧消息会在下一轮对话后从历史中淘汰");
        contextCountField.addValueChangeListener(e -> {
            contextCountField.setInvalid(false);
            markDirty();
        });

        topPToggle = new Checkbox();
        topPField = new NumberField();
        topPField.setMin(0.01);
        topPField.setMax(1);
        topPField.setStep(0.05);
        topPField.setStepButtonsVisible(true);
        topPField.setWidthFull();
        topPField.setHelperText("范围 0.01-1.0，值越高输出越多样，默认 0.9");
        topPField.addValueChangeListener(e -> {
            topPField.setInvalid(false);
            markDirty();
        });
        Div topPCard = switchCard("设置 Top P", "控制输出的多样性，值越高输出越多样；关闭时使用模型默认值",
                topPToggle, topPField);

        maxTokensToggle = new Checkbox();
        maxTokensField = new IntegerField();
        maxTokensField.setMin(1);
        maxTokensField.setStep(100);
        maxTokensField.setStepButtonsVisible(true);
        maxTokensField.setWidthFull();
        maxTokensField.setHelperText("正整数，默认 2048；超过模型实际上限时以模型为准");
        maxTokensField.addValueChangeListener(e -> {
            maxTokensField.setInvalid(false);
            markDirty();
        });
        Div maxTokensCard = switchCard("设置最大 Token 数", "限制每次回复的最大 Token 数量；关闭时按模型默认上限",
                maxTokensToggle, maxTokensField);

        Div fields = new Div(modelCombo, temperatureField, contextCountField);
        fields.addClassName("ac-fields");
        panel.add(fields, topPCard, maxTokensCard);
        return panel;
    }

    /** 开关卡片：标题 + 说明 + 开关；开关打开后展开数值输入 */
    private Div switchCard(String title, String desc, Checkbox toggle, Component body) {
        Span titleSpan = new Span(title);
        titleSpan.addClassName("ac-switch-title");
        Span descSpan = new Span(desc);
        descSpan.addClassName("ac-switch-desc");
        Div text = new Div(titleSpan, descSpan);
        text.addClassName("ac-switch-text");
        Div head = new Div(text, toggle);
        head.addClassName("ac-switch-head");
        Div bodyDiv = new Div(body);
        bodyDiv.addClassName("ac-switch-body");
        bodyDiv.setVisible(false);
        toggle.addValueChangeListener(e -> {
            bodyDiv.setVisible(e.getValue());
            markDirty();
        });
        Div card = new Div(head, bodyDiv);
        card.addClassName("ac-switch-card");
        return card;
    }

    // ==================== 分区三：系统提示词 ====================

    private Component buildPromptSection() {
        Div panel = panel(SECTION_PROMPT);
        sysPromptArea = new TextArea();
        sysPromptArea.setWidthFull();
        sysPromptArea.setMinHeight("14em");
        sysPromptArea.setPlaceholder("如：你是一个专业的天气助手，擅长解答天气相关问题……");
        sysPromptArea.setHelperText("定义智能体的角色、能力与行为边界；保存后会自动追加默认要求：结构化方式输出、回答简洁明了；留空则使用兜底提示词");
        sysPromptArea.addValueChangeListener(e -> markDirty());
        panel.add(sysPromptArea);
        return panel;
    }

    // ==================== 分区四~七：工具 / MCP / 知识库 / 技能 ====================

    private Component buildToolsSection() {
        Div panel = panel(SECTION_TOOLS);
        panel.add(relationGroup("系统工具", systemTools,
                ToolInfo::getName, ToolInfo::getName,
                t -> StrUtil.brief(StrUtil.nullToEmpty(t.getDescription()), 40),
                selectedToolNames));
        panel.add(relationGroup("自定义工具", customTools,
                CustomTool::getId, CustomTool::getName,
                t -> "HTTP · " + t.getToolKey(),
                selectedCustomToolIds));
        return panel;
    }

    private Component buildMcpSection() {
        Div panel = panel(SECTION_MCP);
        panel.add(relationGroup("MCP 服务", mcpServers,
                McpServer::getId, McpServer::getName,
                s -> MCP_TYPES.getOrDefault(s.getType(), StrUtil.nullToEmpty(s.getType()))
                        + " · " + StrUtil.brief(StrUtil.nullToEmpty(s.getUrl()), 36),
                selectedMcpIds));
        return panel;
    }

    private Component buildKnowledgeSection() {
        Div panel = panel(SECTION_KNOWLEDGE);
        panel.add(relationGroup("知识库", knowledgeBases,
                KnowledgeBase::getId, KnowledgeBase::getName,
                k -> KNOWLEDGE_TYPES.getOrDefault(k.getType(), StrUtil.nullToEmpty(k.getType()))
                        + (StrUtil.isBlank(k.getRemark()) ? "" : " · " + StrUtil.brief(k.getRemark(), 30)),
                selectedKbIds));
        return panel;
    }

    private Component buildSkillSection() {
        Div panel = panel(SECTION_SKILL);
        panel.add(relationGroup("技能仓库", skillRepos,
                SkillRepo::getId, SkillRepo::getName,
                r -> SKILL_REPO_TYPES.getOrDefault(r.getType(), StrUtil.nullToEmpty(r.getType()))
                        + (StrUtil.isBlank(r.getRemark()) ? "" : " · " + StrUtil.brief(r.getRemark(), 30)),
                selectedSkillRepoIds));
        return panel;
    }

    /**
     * 多选关系组：已配置列表（名称 + 摘要 + 移除）+ 添加选择器。
     * 选中集保存在 key 集合中，保存时统一转 JSON 数组。
     */
    private <T, K> Div relationGroup(String title, List<T> allItems,
                                     Function<T, K> keyFn, Function<T, String> nameFn,
                                     Function<T, String> subFn, Set<K> selected) {
        Span titleSpan = new Span(title);
        titleSpan.addClassName("ac-rel-title");
        Span count = new Span();
        count.addClassName("ac-rel-count");
        Button addButton = new Button("添加", new Icon(VaadinIcon.PLUS));
        addButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        addButton.addClassName("ac-rel-add");
        Div head = new Div(titleSpan, count, addButton);
        head.addClassName("ac-rel-head");
        Div list = new Div();
        list.addClassName("ac-rel-list");

        final Runnable[] renderRef = new Runnable[1];
        Runnable render = () -> {
            count.setText("已配置 " + selected.size());
            list.removeAll();
            if (selected.isEmpty()) {
                Div empty = new Div(new Span("暂未配置，点击「添加」选择"));
                empty.addClassName("ac-rel-empty");
                list.add(empty);
                return;
            }
            for (T item : allItems) {
                K key = keyFn.apply(item);
                if (selected.contains(key)) {
                    list.add(relationRow(nameFn.apply(item), subFn.apply(item), () -> {
                        selected.remove(key);
                        markDirty();
                        renderRef[0].run();
                    }));
                }
            }
        };
        renderRef[0] = render;
        addButton.addClickListener(e -> openPicker(title, allItems, keyFn, nameFn, subFn, selected, render));
        renderers.add(render);
        render.run();

        Div group = new Div(head, list);
        group.addClassName("ac-rel-group");
        return group;
    }

    /** 已配置项一行：名称 + 摘要 + 移除按钮 */
    private Component relationRow(String name, String sub, Runnable onRemove) {
        Span nameSpan = new Span(name);
        nameSpan.addClassName("ac-rel-row-name");
        Div text = new Div(nameSpan);
        text.addClassName("ac-rel-row-text");
        if (StrUtil.isNotBlank(sub)) {
            Span subSpan = new Span(sub);
            subSpan.addClassName("ac-rel-row-sub");
            text.add(subSpan);
        }
        Button remove = new Button(new Icon(VaadinIcon.CLOSE));
        remove.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        remove.setTooltipText("移除");
        remove.addClickListener(e -> onRemove.run());
        Div row = new Div(text, remove);
        row.addClassName("ac-rel-row");
        return row;
    }

    /**
     * 添加选择器弹窗：只列未配置的；卡片式条目（点击整行勾选）、
     * 支持按名称/摘要搜索、全选/清空，底部实时显示已选数量。
     */
    private <T, K> void openPicker(String title, List<T> allItems, Function<T, K> keyFn,
                                   Function<T, String> nameFn, Function<T, String> subFn,
                                   Set<K> selected, Runnable after) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("添加" + title);
        dialog.setWidth("620px");

        List<T> available = allItems.stream()
                .filter(item -> !selected.contains(keyFn.apply(item)))
                .toList();
        if (available.isEmpty()) {
            dialog.add(new Paragraph("暂无可添加的选项"));
            dialog.open();
            return;
        }

        Set<T> chosen = new LinkedHashSet<>();

        // 工具栏：搜索 + 全选/清空
        TextField search = new TextField();
        search.setPlaceholder("搜索名称 / 描述");
        search.setClearButtonVisible(true);
        search.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        search.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setWidthFull();

        Div list = new Div();
        list.addClassName("ac-picker-list");

        Span chosenCount = new Span("已选 0 项");
        chosenCount.addClassName("ac-picker-count");

        Button ok = new Button("添加");
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        ok.setEnabled(false);

        // 仅刷新计数与按钮（行内勾选时调用，不重建列表以保留滚动位置）
        Runnable refreshCounts = () -> {
            chosenCount.setText("已选 " + chosen.size() + " 项");
            ok.setEnabled(!chosen.isEmpty());
            ok.setText(chosen.isEmpty() ? "添加" : "添加（" + chosen.size() + "）");
        };

        // 按搜索关键字重建可见行
        Runnable render = () -> {
            String kw = StrUtil.trimToEmpty(search.getValue()).toLowerCase();
            list.removeAll();
            List<T> visible = available.stream()
                    .filter(item -> StrUtil.isBlank(kw)
                            || nameFn.apply(item).toLowerCase().contains(kw)
                            || StrUtil.nullToEmpty(subFn.apply(item)).toLowerCase().contains(kw))
                    .toList();
            if (visible.isEmpty()) {
                Div empty = new Div(new Span("无匹配项"));
                empty.addClassName("ac-rel-empty");
                list.add(empty);
                return;
            }
            for (T item : visible) {
                list.add(pickerRow(item, nameFn, subFn, chosen, refreshCounts));
            }
        };
        // 全选/清空后重建列表并刷新计数
        Runnable refreshAll = () -> {
            render.run();
            refreshCounts.run();
        };

        search.addValueChangeListener(e -> render.run());

        Button selectAll = new Button("全选", e -> {
            chosen.addAll(available);
            refreshAll.run();
        });
        selectAll.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        Button clear = new Button("清空", e -> {
            chosen.clear();
            refreshAll.run();
        });
        clear.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout toolbar = new HorizontalLayout(search, selectAll, clear);
        toolbar.addClassName("ac-picker-toolbar");
        toolbar.setWidthFull();
        toolbar.expand(search);
        toolbar.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        Button cancel = new Button("取消", e -> dialog.close());
        ok.addClickListener(e -> {
            chosen.forEach(item -> selected.add(keyFn.apply(item)));
            markDirty();
            after.run();
            dialog.close();
        });

        render.run();

        dialog.add(toolbar, list);
        dialog.getFooter().add(chosenCount, cancel, ok);
        dialog.open();
    }

    /** 选择器条目卡片：名称 + 摘要 + 选中指示圈，点击整行切换选中 */
    private <T> Div pickerRow(T item, Function<T, String> nameFn, Function<T, String> subFn,
                              Set<T> chosen, Runnable onToggle) {
        Span name = new Span(nameFn.apply(item));
        name.addClassName("ac-rel-row-name");
        Div text = new Div(name);
        text.addClassName("ac-picker-item-text");
        String sub = subFn.apply(item);
        if (StrUtil.isNotBlank(sub)) {
            Span subSpan = new Span(sub);
            subSpan.addClassName("ac-rel-row-sub");
            text.add(subSpan);
        }
        Icon checkIcon = new Icon(VaadinIcon.CHECK);
        Div indicator = new Div(checkIcon);
        indicator.addClassName("ac-picker-check");
        Div row = new Div(text, indicator);
        row.addClassName("ac-picker-item");
        if (chosen.contains(item)) {
            row.addClassName("ac-selected");
        }
        row.addClickListener(e -> {
            boolean nowSelected;
            if (chosen.remove(item)) {
                nowSelected = false;
            } else {
                chosen.add(item);
                nowSelected = true;
            }
            if (nowSelected) {
                row.addClassName("ac-selected");
            } else {
                row.removeClassName("ac-selected");
            }
            onToggle.run();
        });
        return row;
    }

    // ==================== 分区八：存储配置 ====================

    private Component buildStorageSection() {
        Div panel = panel(SECTION_STORAGE);
        stateStoreSelect = new Select<>();
        stateStoreSelect.setLabel("会话状态存储");
        stateStoreSelect.setItems(STATE_STORE_NAMES.keySet());
        stateStoreSelect.setItemLabelGenerator(STATE_STORE_NAMES::get);
        stateStoreSelect.setWidth("420px");
        stateStoreSelect.setHelperText("四种实现之间会话数据互相隔离；配置与实时可用性见「数据存储」页");
        stateStoreSelect.addValueChangeListener(e -> markDirty());

        Div options = new Div();
        options.addClassName("ac-fields");
        for (Map.Entry<String, String> entry : STATE_STORE_NAMES.entrySet()) {
            Span name = new Span(entry.getValue());
            name.addClassName("ac-rel-row-name");
            Span desc = new Span(STATE_STORE_DESCS.get(entry.getKey()));
            desc.addClassName("ac-rel-row-sub");
            Div text = new Div(name, desc);
            text.addClassName("ac-rel-row-text");
            Div row = new Div(text);
            row.addClassName("ac-rel-row");
            options.add(row);
        }
        panel.add(stateStoreSelect, options);
        return panel;
    }

    // ==================== 保存 ====================

    private void save() {
        if (!validateForm()) {
            return;
        }
        applyToAgent();
        confirmSave();
    }

    private boolean validateForm() {
        if (StrUtil.isBlank(nameField.getValue())) {
            invalid(nameField, "名称不能为空");
            selectSection(SECTION_BASIC);
            Notify.error("请填写智能体名称");
            return false;
        }
        if (modelCombo.getValue() == null) {
            invalid(modelCombo, "请选择模型");
            selectSection(SECTION_MODEL);
            Notify.error("请选择模型");
            return false;
        }
        if (temperatureField.getValue() == null
                || temperatureField.getValue() < 0 || temperatureField.getValue() > 2) {
            invalid(temperatureField, "温度范围 0-2");
            selectSection(SECTION_MODEL);
            Notify.error("模型温度需在 0-2 之间");
            return false;
        }
        Integer contextCount = contextCountField.getValue();
        if (contextCount == null || contextCount < 1 || contextCount > 20) {
            invalid(contextCountField, "上下文数范围 1-20");
            selectSection(SECTION_MODEL);
            Notify.error("上下文数需在 1-20 之间");
            return false;
        }
        if (Boolean.TRUE.equals(topPToggle.getValue())) {
            Double topP = topPField.getValue();
            if (topP == null || topP < 0.01 || topP > 1) {
                invalid(topPField, "Top P 范围 0.01-1.0");
                selectSection(SECTION_MODEL);
                Notify.error("Top P 需在 0.01-1.0 之间");
                return false;
            }
        }
        if (Boolean.TRUE.equals(maxTokensToggle.getValue())) {
            Integer maxTokens = maxTokensField.getValue();
            if (maxTokens == null || maxTokens < 1) {
                invalid(maxTokensField, "最大 Token 数需为正整数");
                selectSection(SECTION_MODEL);
                Notify.error("最大 Token 数需为正整数");
                return false;
            }
        }
        return true;
    }

    private void invalid(HasValidation field, String message) {
        field.setInvalid(true);
        field.setErrorMessage(message);
    }

    /** 表单 -> 实体回写 */
    private void applyToAgent() {
        agent.setName(nameField.getValue().trim());
        agent.setAvatar(blankToNull(avatarValue));
        agent.setDescription(blankToNull(descField.getValue()));
        agent.setModelId(modelCombo.getValue().getId());
        agent.setTemperature(temperatureField.getValue());
        agent.setContextCount(contextCountField.getValue());
        agent.setTopP(Boolean.TRUE.equals(topPToggle.getValue()) ? topPField.getValue() : null);
        agent.setMaxTokens(Boolean.TRUE.equals(maxTokensToggle.getValue()) ? maxTokensField.getValue() : null);
        agent.setSysPrompt(blankToNull(sysPromptArea.getValue()));
        agent.setStateStore(stateStoreSelect.getValue());
        agent.setTools(jsonStringArray(selectedToolNames));
        agent.setCustomTools(jsonIdArray(selectedCustomToolIds));
        agent.setMcpServers(jsonIdArray(selectedMcpIds));
        agent.setKnowledgeBases(jsonIdArray(selectedKbIds));
        agent.setSkillRepos(jsonIdArray(selectedSkillRepoIds));
    }

    /**
     * 保存前确认弹窗：展示配置摘要，确认信息完整无误后才执行保存；
     * 保存成功停留在当前页，新增场景把 URL 换成编辑态
     */
    private void confirmSave() {
        Dialog confirm = new Dialog();
        confirm.setHeaderTitle(isNew ? "确认创建智能体" : "确认保存修改");
        confirm.setWidth("520px");
        confirm.setMaxHeight("80vh");

        ModelConfig model = modelCombo.getValue();
        String avatarText = StrUtil.isBlank(agent.getAvatar()) ? ""
                : AgentAvatar.isImageUrl(agent.getAvatar()) ? "（图片头像）" : "（" + agent.getAvatar() + "）";
        String modelParams = "温度 " + agent.getTemperature()
                + " · 上下文 " + agent.getContextCount() + " 条"
                + " · TopP " + (agent.getTopP() == null ? "默认" : agent.getTopP())
                + " · 最大Token " + (agent.getMaxTokens() == null ? "默认" : agent.getMaxTokens());

        Div summary = new Div();
        summary.addClassName("ac-summary");
        addSummaryRow(summary, "名称", agent.getName() + avatarText);
        addSummaryRow(summary, "模型", model.getName() + "（" + model.getModel() + "）");
        addSummaryRow(summary, "模型参数", modelParams);
        addSummaryRow(summary, "状态存储", STATE_STORE_NAMES.getOrDefault(agent.getStateStore(), "-"));
        addSummaryRow(summary, "系统工具", summarize(selectedToolNames, Function.identity()));
        addSummaryRow(summary, "自定义工具", summarizeIds(selectedCustomToolIds, customToolNames));
        addSummaryRow(summary, "MCP服务", summarizeIds(selectedMcpIds, mcpServerNames));
        addSummaryRow(summary, "知识库", summarizeIds(selectedKbIds, knowledgeBaseNames));
        addSummaryRow(summary, "技能仓库", summarizeIds(selectedSkillRepoIds, skillRepoNames));

        Paragraph hint = new Paragraph("保存后将立即注册/重建智能体实例，请确认以上信息完整无误。");
        hint.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("margin-bottom", "0");
        confirm.add(summary, hint);

        Button back = new Button("返回修改", e -> confirm.close());
        Button ok = new Button("确认保存", e -> {
            try {
                agentService.saveAgent(agent);
                confirm.close();
                dirty = false;
                refreshAgentBar();
                if (isNew) {
                    isNew = false;
                    getUI().ifPresent(ui -> ui.getPage().getHistory().replaceState(null, currentPath()));
                }
                // 新建保存后才有智能体实例，重建对话测试分区为可用状态
                buildChatContent();
                Notify.success("保存成功");
            } catch (Exception ex) {
                Notify.error(ex.getMessage());
            }
        });
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirm.getFooter().add(back, ok);
        confirm.open();
    }

    /** 摘要区一行：灰色标签 + 值（CSS 网格对齐） */
    private void addSummaryRow(Div summary, String label, String value) {
        Span labelSpan = new Span(label);
        labelSpan.addClassName("ac-summary-label");
        Span valueSpan = new Span(StrUtil.nullToDefault(value, "-"));
        valueSpan.addClassName("ac-summary-value");
        summary.add(labelSpan, valueSpan);
    }

    // ==================== 通用辅助 ====================

    private Div panel(String sectionKey) {
        SectionDef def = SECTIONS.stream()
                .filter(s -> s.key().equals(sectionKey)).findFirst().orElseThrow();
        H3 title = new H3(def.label());
        title.addClassName("ac-panel-title");
        Paragraph desc = new Paragraph(sectionDesc(sectionKey));
        desc.addClassName("ac-panel-desc");
        Div panel = new Div(title, desc);
        panel.addClassName("ac-panel");
        return panel;
    }

    private String sectionDesc(String sectionKey) {
        return switch (sectionKey) {
            case SECTION_BASIC -> "智能体的头像、名称与用途描述，展示在列表页与对话页";
            case SECTION_MODEL -> "选择大模型并调整生成参数；参数对管理端对话与开放接口同时生效";
            case SECTION_PROMPT -> "定义智能体的角色、能力与行为边界";
            case SECTION_TOOLS -> "系统工具为平台内置能力，自定义工具为 HTTP 远程接口代理；见「系统工具」「自定义工具」菜单";
            case SECTION_MCP -> "挂载后自动接入所选服务的全部工具；不可达的服务会跳过挂载并记录日志，见「MCP服务管理」";
            case SECTION_KNOWLEDGE -> "挂载后智能体可通过 retrieve_knowledge 工具自主检索，见「知识库管理」";
            case SECTION_SKILL -> "接入技能仓库中的技能，构建失败的仓库会跳过并记录日志，见「技能仓库管理」";
            case SECTION_STORAGE -> "会话历史的持久化方式，四种实现数据互相隔离，见「数据存储」页";
            default -> "";
        };
    }

    private void updateAvatarPreview() {
        avatarPreview.removeAll();
        avatarPreview.add(AgentAvatar.create(avatarValue, nameField.getValue(), 64));
        refreshAgentBar();
    }

    private void refreshAgentBar() {
        agentBarAvatar.removeAll();
        agentBarAvatar.add(AgentAvatar.create(avatarValue, nameField.getValue(), 28));
        agentBarName.setText(StrUtil.blankToDefault(nameField.getValue(), isNew ? "新智能体" : "未命名"));
        dirtyBadge.setVisible(dirty);
    }

    private void markDirty() {
        dirty = true;
        if (dirtyBadge != null) {
            dirtyBadge.setVisible(true);
        }
    }

    /** 字符串集合 -> 排序后的 JSON 数组，空集合返回 null（保存时补 []） */
    private String jsonStringArray(Set<String> values) {
        if (CollUtil.isEmpty(values)) {
            return null;
        }
        List<String> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return JSONUtil.toJsonStr(sorted);
    }

    /** ID 集合 -> 排序后的 JSON 数组，空集合返回 null（保存时补 []） */
    private String jsonIdArray(Set<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return null;
        }
        return JSONUtil.toJsonStr(ids.stream().sorted().toList());
    }

    private List<String> parseStringArray(String json) {
        return StrUtil.isBlank(json) ? List.of() : JSONUtil.toList(json, String.class);
    }

    private List<Long> parseIdArray(String json) {
        return StrUtil.isBlank(json) ? List.of() : JSONUtil.toList(json, Long.class);
    }

    /** 多选集合 -> 「名称1、名称2、名称3 等（共 n 个）」，超过 3 个截断，空集合显示「无」 */
    private <T> String summarize(Set<T> items, Function<T, String> nameFn) {
        if (CollUtil.isEmpty(items)) {
            return "无";
        }
        List<String> names = items.stream().map(nameFn).limit(3).toList();
        String joined = String.join("、", names);
        return (items.size() > 3 ? joined + " 等" : joined) + "（共 " + items.size() + " 个）";
    }

    /** ID 集合 -> 名称摘要（ID 已被删除时显示 ID:x） */
    private String summarizeIds(Set<Long> ids, Map<Long, String> names) {
        return summarize(ids, id -> names.getOrDefault(id, "ID:" + id));
    }

    private static <T> Map<Long, String> namesById(List<T> items, Function<T, Long> idFn,
                                                   Function<T, String> nameFn) {
        return items.stream().collect(Collectors.toMap(idFn, nameFn));
    }

    /** 空白字符串 -> null */
    private static String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value;
    }
}

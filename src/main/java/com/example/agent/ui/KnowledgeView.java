package com.example.agent.ui;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.system.entity.KnowledgeBase;
import com.example.agent.system.service.KnowledgeBaseService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.Map;

@Route(value = "knowledge", layout = MainLayout.class)
@PageTitle("知识库管理 - agent-platform")
public class KnowledgeView extends VerticalLayout {

    /**
     * 知识库类型 -> 展示名
     */
    private static final Map<String, String> TYPES = Map.of(
            KnowledgeBase.TYPE_BAILIAN, "阿里云百炼（bailian）",
            KnowledgeBase.TYPE_DIFY, "Dify（dify）");
    /**
     * Dify 检索模式 -> 展示名
     */
    private static final Map<String, String> RETRIEVAL_MODES = Map.of(
            "HYBRID_SEARCH", "混合检索（HYBRID_SEARCH）",
            "SEMANTIC_SEARCH", "语义检索（SEMANTIC_SEARCH）",
            "KEYWORD_SEARCH", "关键词检索（KEYWORD_SEARCH）",
            "FULL_TEXT_SEARCH", "全文检索（FULL_TEXT_SEARCH）");

    private final KnowledgeBaseService knowledgeBaseService;
    private final Grid<KnowledgeBase> grid = new Grid<>(KnowledgeBase.class, false);
    private final TextField keyword = new TextField();
    private final PaginationBar paginationBar = new PaginationBar(this::loadPage);

    public KnowledgeView(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
        setSizeFull();

        H2 title = new H2("知识库管理");
        title.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-xl)");

        keyword.setPlaceholder("名称 / 备注");
        keyword.setClearButtonVisible(true);
        keyword.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        keyword.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        keyword.addKeyPressListener(Key.ENTER, e -> paginationBar.reset());
        Button search = new Button("搜索", e -> paginationBar.reset());
        search.addThemeVariants(ButtonVariant.LUMO_SMALL);
        Button add = new Button("新增知识库", new Icon(VaadinIcon.PLUS), e -> openDialog(new KnowledgeBase()));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        HorizontalLayout toolbar = new HorizontalLayout(title, keyword, search, add);
        toolbar.setWidthFull();
        toolbar.expand(title);
        toolbar.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        grid.addColumn(KnowledgeBase::getId).setHeader("ID").setWidth("80px").setFlexGrow(0);
        grid.addColumn(KnowledgeBase::getName).setHeader("名称");
        grid.addComponentColumn(k -> typeBadge(k.getType())).setHeader("类型").setWidth("190px").setFlexGrow(0);
        grid.addColumn(KnowledgeBase::getRetrieveLimit).setHeader("检索条数").setWidth("100px").setFlexGrow(0);
        grid.addColumn(KnowledgeBase::getScoreThreshold).setHeader("分数阈值").setWidth("100px").setFlexGrow(0);
        grid.addColumn(k -> StrUtil.nullToEmpty(k.getRemark())).setHeader("备注");
        grid.addColumn(k -> DateUtil.format(k.getUpdateTime(), "yyyy-MM-dd HH:mm:ss")).setHeader("更新时间");
        grid.addComponentColumn(this::actionButtons).setHeader("操作").setWidth("180px").setFlexGrow(0);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

        add(toolbar, grid, paginationBar);
        refresh();
    }

    private Component actionButtons(KnowledgeBase kb) {
        Button edit = new Button("编辑", e -> openDialog(kb));
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        Button delete = new Button("删除", e -> confirmDelete(kb));
        delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        return new HorizontalLayout(edit, delete);
    }

    /** 类型徽标 */
    private Component typeBadge(String type) {
        Span badge = new Span(TYPES.getOrDefault(type, StrUtil.nullToEmpty(type)));
        badge.getElement().getThemeList().add(
                KnowledgeBase.TYPE_BAILIAN.equals(type) ? "badge success" : "badge contrast");
        return badge;
    }

    private void refresh() {
        paginationBar.refresh();
    }

    private void loadPage(int page, int pageSize) {
        Page<KnowledgeBase> result = knowledgeBaseService.pageKnowledgeBases(keyword.getValue(), page, pageSize);
        grid.setItems(result.getRecords());
        paginationBar.setTotal(result.getTotal());
    }

    /**
     * 新增 / 编辑对话框：通用字段走 Binder；类型专属字段不参与 Binder，
     * 编辑时按当前类型从 config JSON 回填，保存时校验必填项后组装 JSON 存入 config
     */
    private void openDialog(KnowledgeBase kb) {
        boolean isNew = kb.getId() == null;
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isNew ? "新增知识库" : "编辑知识库");
        // 固定弹框宽度，避免切换类型时随内容动态变化
        dialog.setWidth("640px");

        TextField name = new TextField("名称");
        name.setPlaceholder("如：产品文档库");
        name.setMaxLength(64);
        Select<String> type = new Select<>();
        type.setLabel("类型");
        type.setItems(TYPES.keySet());
        type.setItemLabelGenerator(TYPES::get);
        IntegerField retrieveLimit = new IntegerField("检索条数");
        retrieveLimit.setMin(1);
        retrieveLimit.setStepButtonsVisible(true);
        retrieveLimit.setHelperText("每次检索返回的最大文档数");
        NumberField scoreThreshold = new NumberField("分数阈值");
        scoreThreshold.setMin(0);
        scoreThreshold.setMax(1);
        scoreThreshold.setStep(0.1);
        scoreThreshold.setStepButtonsVisible(true);
        scoreThreshold.setHelperText("0~1，越高匹配越严格");
        TextField remark = new TextField("备注");
        remark.setMaxLength(256);

        // 百炼专属字段（不挂 Binder，手动 get/set）
        TextField accessKeyId = new TextField("AccessKeyId");
        accessKeyId.setPlaceholder("阿里云 AccessKeyId");
        PasswordField accessKeySecret = new PasswordField("AccessKeySecret");
        accessKeySecret.setPlaceholder("阿里云 AccessKeySecret");
        TextField workspaceId = new TextField("WorkspaceId");
        workspaceId.setPlaceholder("百炼业务空间 ID");
        TextField indexId = new TextField("IndexId");
        indexId.setPlaceholder("知识索引 ID");
        TextField endpoint = new TextField("Endpoint");
        endpoint.setPlaceholder("默认 bailian.cn-beijing.aliyuncs.com");
        endpoint.setWidthFull();
        Checkbox enableReranking = new Checkbox("启用重排序（enableReranking）");
        Checkbox enableRewrite = new Checkbox("启用查询改写（enableRewrite）");
        FormLayout bailianSection = new FormLayout(accessKeyId, accessKeySecret, workspaceId, indexId,
                endpoint, enableReranking, enableRewrite);
        bailianSection.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
        bailianSection.setColspan(endpoint, 2);

        // Dify 专属字段（不挂 Binder，手动 get/set）
        PasswordField apiKey = new PasswordField("API Key");
        apiKey.setPlaceholder("Dify 知识库 API Key");
        TextField datasetId = new TextField("DatasetId");
        datasetId.setPlaceholder("Dify 知识库 Dataset ID");
        TextField baseUrl = new TextField("API 地址");
        baseUrl.setPlaceholder("默认 https://api.dify.ai/v1");
        baseUrl.setWidthFull();
        Select<String> retrievalMode = new Select<>();
        retrievalMode.setLabel("检索模式");
        retrievalMode.setItems(RETRIEVAL_MODES.keySet());
        retrievalMode.setItemLabelGenerator(RETRIEVAL_MODES::get);
        Checkbox enableRerank = new Checkbox("启用重排序（enableRerank）");
        FormLayout difySection = new FormLayout(apiKey, datasetId, baseUrl, retrievalMode, enableRerank);
        difySection.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
        difySection.setColspan(baseUrl, 2);

        // 类型切换只控制区块显隐，不影响另一区块已填的值
        type.addValueChangeListener(e -> {
            bailianSection.setVisible(KnowledgeBase.TYPE_BAILIAN.equals(e.getValue()));
            difySection.setVisible(KnowledgeBase.TYPE_DIFY.equals(e.getValue()));
        });

        Binder<KnowledgeBase> binder = new Binder<>(KnowledgeBase.class);
        binder.forField(name).asRequired("名称不能为空").bind(KnowledgeBase::getName, KnowledgeBase::setName);
        binder.forField(type).asRequired("请选择类型").bind(KnowledgeBase::getType, KnowledgeBase::setType);
        binder.forField(retrieveLimit).asRequired("检索条数不能为空")
                .bind(KnowledgeBase::getRetrieveLimit, KnowledgeBase::setRetrieveLimit);
        binder.forField(scoreThreshold).asRequired("分数阈值不能为空")
                .bind(KnowledgeBase::getScoreThreshold, KnowledgeBase::setScoreThreshold);
        binder.bind(remark, KnowledgeBase::getRemark, KnowledgeBase::setRemark);

        name.setRequiredIndicatorVisible(true);
        type.setRequiredIndicatorVisible(true);
        accessKeyId.setRequiredIndicatorVisible(true);
        accessKeySecret.setRequiredIndicatorVisible(true);
        workspaceId.setRequiredIndicatorVisible(true);
        indexId.setRequiredIndicatorVisible(true);
        apiKey.setRequiredIndicatorVisible(true);
        datasetId.setRequiredIndicatorVisible(true);

        // 新增时的默认值写在 bean 上：readBean 会用 bean 值刷新字段，
        // 若直接 setValue 会被随后的 readBean 覆盖清空
        if (isNew) {
            kb.setType(KnowledgeBase.TYPE_BAILIAN);
            kb.setRetrieveLimit(5);
            kb.setScoreThreshold(0.5);
        }
        binder.readBean(kb);
        // 类型专属字段：编辑时从 config JSON 回填；新建给 Dify 默认值
        fillTypeFields(kb, accessKeyId, accessKeySecret, workspaceId, indexId, endpoint,
                enableReranking, enableRewrite, apiKey, datasetId, baseUrl, retrievalMode, enableRerank);
        // readBean 不触发 ValueChange（初始同值时），显式同步一次区块显隐
        bailianSection.setVisible(KnowledgeBase.TYPE_BAILIAN.equals(type.getValue()));
        difySection.setVisible(KnowledgeBase.TYPE_DIFY.equals(type.getValue()));

        FormLayout form = new FormLayout(name, type, retrieveLimit, scoreThreshold, remark);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
        VerticalLayout layout = new VerticalLayout(form, bailianSection, difySection);
        layout.setPadding(false);
        dialog.add(layout);

        Button cancel = new Button("取消", e -> dialog.close());
        Button save = new Button("保存", e -> {
            if (!binder.writeBeanIfValid(kb)) {
                return;
            }
            String config = buildConfigJson(kb.getType(), accessKeyId, accessKeySecret, workspaceId, indexId,
                    endpoint, enableReranking, enableRewrite, apiKey, datasetId, baseUrl, retrievalMode,
                    enableRerank);
            if (config == null) {
                return;
            }
            kb.setConfig(config);
            try {
                knowledgeBaseService.saveKnowledgeBase(kb);
                dialog.close();
                refresh();
                Notify.success("保存成功");
            } catch (Exception ex) {
                Notify.error(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    /** 编辑时按类型从 config JSON 回填专属字段；新建时给 Dify 检索模式 / 重排序默认值 */
    private void fillTypeFields(KnowledgeBase kb, TextField accessKeyId, PasswordField accessKeySecret,
                                TextField workspaceId, TextField indexId, TextField endpoint,
                                Checkbox enableReranking, Checkbox enableRewrite,
                                PasswordField apiKey, TextField datasetId, TextField baseUrl,
                                Select<String> retrievalMode, Checkbox enableRerank) {
        JSONObject config = StrUtil.isBlank(kb.getConfig())
                ? JSONUtil.createObj() : JSONUtil.parseObj(kb.getConfig());
        accessKeyId.setValue(StrUtil.nullToEmpty(config.getStr("accessKeyId")));
        accessKeySecret.setValue(StrUtil.nullToEmpty(config.getStr("accessKeySecret")));
        workspaceId.setValue(StrUtil.nullToEmpty(config.getStr("workspaceId")));
        indexId.setValue(StrUtil.nullToEmpty(config.getStr("indexId")));
        endpoint.setValue(StrUtil.nullToEmpty(config.getStr("endpoint")));
        enableReranking.setValue(config.getBool("enableReranking", false));
        enableRewrite.setValue(config.getBool("enableRewrite", false));
        apiKey.setValue(StrUtil.nullToEmpty(config.getStr("apiKey")));
        datasetId.setValue(StrUtil.nullToEmpty(config.getStr("datasetId")));
        baseUrl.setValue(StrUtil.nullToEmpty(config.getStr("baseUrl")));
        retrievalMode.setValue(StrUtil.blankToDefault(config.getStr("retrievalMode"), "HYBRID_SEARCH"));
        enableRerank.setValue(config.getBool("enableRerank", true));
    }

    /**
     * 校验当前类型必填项并组装 config JSON；校验失败提示后返回 null。
     * 选填项只存非空值，布尔项始终存。
     */
    private String buildConfigJson(String type, TextField accessKeyId, PasswordField accessKeySecret,
                                   TextField workspaceId, TextField indexId, TextField endpoint,
                                   Checkbox enableReranking, Checkbox enableRewrite,
                                   PasswordField apiKey, TextField datasetId, TextField baseUrl,
                                   Select<String> retrievalMode, Checkbox enableRerank) {
        JSONObject config = JSONUtil.createObj();
        if (KnowledgeBase.TYPE_BAILIAN.equals(type)) {
            if (StrUtil.hasBlank(accessKeyId.getValue(), accessKeySecret.getValue(),
                    workspaceId.getValue(), indexId.getValue())) {
                Notify.error("百炼知识库必须填写 AccessKeyId / AccessKeySecret / WorkspaceId / IndexId");
                return null;
            }
            config.set("accessKeyId", accessKeyId.getValue().trim());
            config.set("accessKeySecret", accessKeySecret.getValue());
            config.set("workspaceId", workspaceId.getValue().trim());
            config.set("indexId", indexId.getValue().trim());
            if (StrUtil.isNotBlank(endpoint.getValue())) {
                config.set("endpoint", endpoint.getValue().trim());
            }
            config.set("enableReranking", enableReranking.getValue());
            config.set("enableRewrite", enableRewrite.getValue());
        } else if (KnowledgeBase.TYPE_DIFY.equals(type)) {
            if (StrUtil.hasBlank(apiKey.getValue(), datasetId.getValue())) {
                Notify.error("Dify 知识库必须填写 API Key / DatasetId");
                return null;
            }
            if (StrUtil.isNotBlank(baseUrl.getValue())
                    && !baseUrl.getValue().trim().matches(FormValidators.URL_PATTERN)) {
                Notify.error("API 地址应以 http:// 或 https:// 开头");
                return null;
            }
            config.set("apiKey", apiKey.getValue());
            config.set("datasetId", datasetId.getValue().trim());
            if (StrUtil.isNotBlank(baseUrl.getValue())) {
                config.set("baseUrl", baseUrl.getValue().trim());
            }
            config.set("retrievalMode", retrievalMode.getValue());
            config.set("enableRerank", enableRerank.getValue());
        }
        return config.toString();
    }

    private void confirmDelete(KnowledgeBase kb) {
        ConfirmDialog dialog = new ConfirmDialog("删除知识库",
                "确定删除知识库「" + kb.getName() + "」吗？引用它的智能体将移除对应知识库。",
                "删除", e -> {
            try {
                knowledgeBaseService.deleteKnowledgeBase(kb.getId());
                refresh();
                Notify.success("删除成功");
            } catch (Exception ex) {
                Notify.error(ex.getMessage());
            }
        });
        dialog.setConfirmButtonTheme("error primary");
        dialog.setCancelable(true);
        dialog.setCancelText("取消");
        dialog.open();
    }
}

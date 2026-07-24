package com.example.agent.ui;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.system.entity.ModelConfig;
import com.example.agent.system.service.ModelConfigService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.Map;

@Route(value = "models", layout = MainLayout.class)
@PageTitle("模型管理 - agent-platform")
public class ModelView extends VerticalLayout {

    /** 供应商 -> 展示名 */
    private static final Map<String, String> PROVIDERS = Map.of(
            "dashscope", "DashScope（阿里云）",
            "openai", "OpenAI",
            "anthropic", "Anthropic",
            "custom", "自定义");

    private final ModelConfigService modelService;
    private final Grid<ModelConfig> grid = new Grid<>(ModelConfig.class, false);
    private final TextField keyword = new TextField();
    private final PaginationBar paginationBar = new PaginationBar(this::loadPage);

    public ModelView(ModelConfigService modelService) {
        this.modelService = modelService;
        setSizeFull();

        H2 title = new H2("模型管理");
        title.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-xl)");

        keyword.setPlaceholder("名称 / 模型标识");
        keyword.setClearButtonVisible(true);
        keyword.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        keyword.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        keyword.addKeyPressListener(Key.ENTER, e -> paginationBar.reset());
        Button search = new Button("搜索", e -> paginationBar.reset());
        search.addThemeVariants(ButtonVariant.LUMO_SMALL);
        Button add = new Button("新增模型", new Icon(VaadinIcon.PLUS), e -> openDialog(new ModelConfig()));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        HorizontalLayout toolbar = new HorizontalLayout(title, keyword, search, add);
        toolbar.setWidthFull();
        toolbar.expand(title);
        toolbar.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        grid.addColumn(ModelConfig::getId).setHeader("ID").setWidth("80px").setFlexGrow(0);
        grid.addColumn(ModelConfig::getName).setHeader("名称");
        grid.addComponentColumn(m -> providerBadge(m.getProvider())).setHeader("供应商").setWidth("170px").setFlexGrow(0);
        grid.addColumn(ModelConfig::getModel).setHeader("模型标识");
        grid.addColumn(m -> StrUtil.nullToEmpty(m.getBaseUrl())).setHeader("API 地址");
        grid.addColumn(m -> maskKey(m.getApiKey())).setHeader("API Key").setWidth("140px").setFlexGrow(0);
        grid.addColumn(m -> StrUtil.nullToEmpty(m.getRemark())).setHeader("备注");
        grid.addColumn(m -> DateUtil.format(m.getCreateTime(), "yyyy-MM-dd HH:mm:ss")).setHeader("创建时间");
        grid.addComponentColumn(this::actionButtons).setHeader("操作").setWidth("180px").setFlexGrow(0);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

        add(toolbar, grid, paginationBar);
        refresh();
    }

    private Component actionButtons(ModelConfig model) {
        Button edit = new Button("编辑", e -> openDialog(model));
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        Button delete = new Button("删除", e -> confirmDelete(model));
        delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        return new HorizontalLayout(edit, delete);
    }

    /** 供应商徽标 */
    private Component providerBadge(String provider) {
        String label = PROVIDERS.getOrDefault(provider, StrUtil.nullToEmpty(provider));
        Span badge = new Span(label);
        String theme = switch (StrUtil.nullToEmpty(provider)) {
            case "dashscope" -> "badge success";
            case "openai" -> "badge";
            case "anthropic" -> "badge contrast";
            default -> "badge error";
        };
        badge.getElement().getThemeList().add(theme);
        return badge;
    }

    /** API Key 脱敏：只显示前 4 位 */
    private String maskKey(String key) {
        if (StrUtil.isBlank(key)) {
            return "";
        }
        return key.length() <= 4 ? "****" : key.substring(0, 4) + "****";
    }

    private void refresh() {
        paginationBar.refresh();
    }

    private void loadPage(int page, int pageSize) {
        Page<ModelConfig> result = modelService.pageModels(keyword.getValue(), page, pageSize);
        grid.setItems(result.getRecords());
        paginationBar.setTotal(result.getTotal());
    }

    private void openDialog(ModelConfig model) {
        boolean isNew = model.getId() == null;
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isNew ? "新增模型" : "编辑模型");

        TextField name = new TextField("名称");
        Select<String> provider = new Select<>();
        provider.setLabel("供应商");
        provider.setItems(PROVIDERS.keySet());
        provider.setItemLabelGenerator(PROVIDERS::get);
        TextField modelName = new TextField("模型标识");
        modelName.setHelperText("如 qwen-plus、gpt-4o、claude-sonnet-4");
        TextField baseUrl = new TextField("API 地址");
        TextField apiKey = new TextField("API Key");
        TextArea remark = new TextArea("备注");
        remark.setMaxHeight("8em");

        // Binder 绑定与校验：校验失败时错误信息红色显示在字段下方
        Binder<ModelConfig> binder = new Binder<>(ModelConfig.class);
        binder.forField(name)
                .asRequired("名称不能为空")
                .bind(ModelConfig::getName, ModelConfig::setName);
        binder.forField(provider)
                .asRequired("请选择供应商")
                .bind(ModelConfig::getProvider, ModelConfig::setProvider);
        binder.forField(modelName)
                .asRequired("模型标识不能为空")
                .bind(ModelConfig::getModel, ModelConfig::setModel);
        binder.forField(baseUrl)
                .withValidator(url -> !"custom".equals(provider.getValue()) || StrUtil.isNotBlank(url),
                        "自定义供应商必须填写 API 地址")
                .bind(ModelConfig::getBaseUrl, ModelConfig::setBaseUrl);
        binder.bind(apiKey, ModelConfig::getApiKey, ModelConfig::setApiKey);
        binder.bind(remark, ModelConfig::getRemark, ModelConfig::setRemark);

        name.setRequiredIndicatorVisible(true);
        provider.setRequiredIndicatorVisible(true);
        modelName.setRequiredIndicatorVisible(true);

        binder.readBean(model);

        FormLayout form = new FormLayout(name, provider, modelName, baseUrl, apiKey, remark);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
        dialog.add(form);

        Button cancel = new Button("取消", e -> dialog.close());
        Button save = new Button("保存", e -> {
            if (!binder.writeBeanIfValid(model)) {
                return;
            }
            try {
                modelService.saveModel(model);
                dialog.close();
                refresh();
                Notification.show("保存成功");
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 3000, Notification.Position.MIDDLE);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private void confirmDelete(ModelConfig model) {
        ConfirmDialog dialog = new ConfirmDialog("删除模型",
                "确定删除模型「" + model.getName() + "」吗？", "删除", e -> {
            try {
                modelService.deleteModel(model.getId());
                refresh();
                Notification.show("删除成功");
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 3000, Notification.Position.MIDDLE);
            }
        });
        dialog.setConfirmButtonTheme("error primary");
        dialog.setCancelable(true);
        dialog.setCancelText("取消");
        dialog.open();
    }
}

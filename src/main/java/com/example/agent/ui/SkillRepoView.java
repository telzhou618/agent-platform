package com.example.agent.ui;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.agent.system.entity.SkillRepo;
import com.example.agent.system.service.SkillRepoService;
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
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.agentscope.core.skill.AgentSkill;

import java.util.LinkedHashMap;
import java.util.Map;

@Route(value = "skills", layout = MainLayout.class)
@PageTitle("技能仓库管理 - agent-platform")
public class SkillRepoView extends VerticalLayout {

    /**
     * 技能仓库类型 -> 展示名
     */
    private static final Map<String, String> TYPES = Map.of(
            SkillRepo.TYPE_GIT, "Git 仓库（git）",
            SkillRepo.TYPE_MYSQL, "MySQL 数据库（mysql）",
            SkillRepo.TYPE_CLASSPATH, "Classpath 目录（classpath）");

    private final SkillRepoService skillRepoService;
    private final Grid<SkillRepo> grid = new Grid<>(SkillRepo.class, false);
    private final TextField keyword = new TextField();
    private final PaginationBar paginationBar = new PaginationBar(this::loadPage);

    public SkillRepoView(SkillRepoService skillRepoService) {
        this.skillRepoService = skillRepoService;
        setSizeFull();

        H2 title = new H2("技能仓库管理");
        title.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-xl)");

        keyword.setPlaceholder("名称 / 备注");
        keyword.setClearButtonVisible(true);
        keyword.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        keyword.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        keyword.addKeyPressListener(Key.ENTER, e -> paginationBar.reset());
        Button search = new Button("搜索", e -> paginationBar.reset());
        search.addThemeVariants(ButtonVariant.LUMO_SMALL);
        Button add = new Button("新增技能仓库", new Icon(VaadinIcon.PLUS), e -> openDialog(new SkillRepo()));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        HorizontalLayout toolbar = new HorizontalLayout(title, keyword, search, add);
        toolbar.setWidthFull();
        toolbar.expand(title);
        toolbar.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        grid.addColumn(SkillRepo::getId).setHeader("ID").setWidth("80px").setFlexGrow(0);
        grid.addColumn(SkillRepo::getName).setHeader("名称");
        grid.addComponentColumn(r -> typeBadge(r.getType())).setHeader("类型").setWidth("220px").setFlexGrow(0);
        grid.addColumn(r -> StrUtil.nullToEmpty(r.getRemark())).setHeader("备注");
        grid.addColumn(r -> DateUtil.format(r.getUpdateTime(), "yyyy-MM-dd HH:mm:ss")).setHeader("更新时间");
        grid.addComponentColumn(this::actionButtons).setHeader("操作").setWidth("250px").setFlexGrow(0);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

        add(toolbar, grid, paginationBar);
        refresh();
    }

    private Component actionButtons(SkillRepo repo) {
        Button skills = new Button("技能", e -> openSkillsDialog(repo));
        skills.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        Button edit = new Button("编辑", e -> openDialog(repo));
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        Button delete = new Button("删除", e -> confirmDelete(repo));
        delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        return new HorizontalLayout(skills, edit, delete);
    }

    /** 类型徽标 */
    private Component typeBadge(String type) {
        Span badge = new Span(TYPES.getOrDefault(type, StrUtil.nullToEmpty(type)));
        badge.getElement().getThemeList().add(
                SkillRepo.TYPE_GIT.equals(type) ? "badge success" : "badge contrast");
        return badge;
    }

    private void refresh() {
        paginationBar.refresh();
    }

    private void loadPage(int page, int pageSize) {
        Page<SkillRepo> result = skillRepoService.pageSkillRepos(keyword.getValue(), page, pageSize);
        grid.setItems(result.getRecords());
        paginationBar.setTotal(result.getTotal());
    }

    /**
     * 新增 / 编辑对话框：通用字段走 Binder；类型专属字段不参与 Binder，
     * 编辑时按当前类型从 config JSON 回填，保存时校验必填项后组装 JSON 存入 config
     */
    private void openDialog(SkillRepo repo) {
        boolean isNew = repo.getId() == null;
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isNew ? "新增技能仓库" : "编辑技能仓库");
        // 固定弹框宽度，避免切换类型时随内容动态变化
        dialog.setWidth("640px");

        TextField name = new TextField("名称");
        name.setPlaceholder("如：团队技能市场");
        name.setMaxLength(128);
        Select<String> type = new Select<>();
        type.setLabel("类型");
        type.setItems(TYPES.keySet());
        type.setItemLabelGenerator(TYPES::get);
        TextField remark = new TextField("备注");
        remark.setMaxLength(512);

        // Git 专属字段（不挂 Binder，手动 get/set）
        TextField gitUrl = new TextField("仓库地址");
        gitUrl.setPlaceholder("如：https://github.com/your-org/team-skills.git");
        gitUrl.setWidthFull();
        Checkbox autoSync = new Checkbox("自动同步（autoSync）");
        autoSync.setHelperText("开启后每次读取做轻量化远端检查，HEAD 变化才 pull");
        TextField localPath = new TextField("本地缓存目录");
        localPath.setPlaceholder("选填，如 workspaces/skill-repos/team-skills");
        localPath.setHelperText("填写后克隆到固定目录复用，读取更快（推荐）；留空则每次克隆到临时目录");
        localPath.setWidthFull();
        FormLayout gitSection = new FormLayout(gitUrl, autoSync, localPath);
        gitSection.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
        gitSection.setColspan(gitUrl, 2);
        gitSection.setColspan(localPath, 2);

        // MySQL 专属字段（不挂 Binder，手动 get/set）；使用平台自己的数据源
        TextField databaseName = new TextField("数据库名");
        databaseName.setPlaceholder("技能表所在数据库，如 agent_platform");
        TextField skillsTableName = new TextField("技能表名");
        skillsTableName.setPlaceholder("默认 skills");
        Checkbox writeable = new Checkbox("可写（writeable）");
        writeable.setHelperText("开启后允许 agent 侧写回技能；只读分发保持关闭");
        FormLayout mysqlSection = new FormLayout(databaseName, skillsTableName, writeable);
        mysqlSection.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

        // Classpath 专属字段（不挂 Binder，手动 get/set）
        TextField directory = new TextField("目录");
        directory.setPlaceholder("如：skills（对应 src/main/resources/skills/）");
        directory.setHelperText("随应用打包发布的技能目录");
        FormLayout classpathSection = new FormLayout(directory);
        classpathSection.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

        // 类型切换只控制区块显隐，不影响其它区块已填的值
        type.addValueChangeListener(e -> {
            gitSection.setVisible(SkillRepo.TYPE_GIT.equals(e.getValue()));
            mysqlSection.setVisible(SkillRepo.TYPE_MYSQL.equals(e.getValue()));
            classpathSection.setVisible(SkillRepo.TYPE_CLASSPATH.equals(e.getValue()));
        });

        Binder<SkillRepo> binder = new Binder<>(SkillRepo.class);
        binder.forField(name).asRequired("名称不能为空").bind(SkillRepo::getName, SkillRepo::setName);
        binder.forField(type).asRequired("请选择类型").bind(SkillRepo::getType, SkillRepo::setType);
        binder.bind(remark, SkillRepo::getRemark, SkillRepo::setRemark);

        name.setRequiredIndicatorVisible(true);
        type.setRequiredIndicatorVisible(true);
        gitUrl.setRequiredIndicatorVisible(true);
        databaseName.setRequiredIndicatorVisible(true);
        directory.setRequiredIndicatorVisible(true);

        // 新增时的默认值写在 bean 上：readBean 会用 bean 值刷新字段
        if (isNew) {
            repo.setType(SkillRepo.TYPE_GIT);
        }
        binder.readBean(repo);
        // 类型专属字段：编辑时从 config JSON 回填；新建给默认值
        fillTypeFields(repo, gitUrl, autoSync, localPath, databaseName, skillsTableName, writeable, directory);
        // readBean 不触发 ValueChange（初始同值时），显式同步一次区块显隐
        gitSection.setVisible(SkillRepo.TYPE_GIT.equals(type.getValue()));
        mysqlSection.setVisible(SkillRepo.TYPE_MYSQL.equals(type.getValue()));
        classpathSection.setVisible(SkillRepo.TYPE_CLASSPATH.equals(type.getValue()));

        FormLayout form = new FormLayout(name, type, remark);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
        VerticalLayout layout = new VerticalLayout(form, gitSection, mysqlSection, classpathSection);
        layout.setPadding(false);
        dialog.add(layout);

        Button cancel = new Button("取消", e -> dialog.close());
        Button save = new Button("保存", e -> {
            if (!binder.writeBeanIfValid(repo)) {
                return;
            }
            String config = buildConfigJson(repo.getType(), gitUrl, autoSync, localPath,
                    databaseName, skillsTableName, writeable, directory);
            if (config == null) {
                return;
            }
            repo.setConfig(config);
            try {
                skillRepoService.saveSkillRepo(repo);
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

    /** 编辑时按类型从 config JSON 回填专属字段；新建时给 autoSync / 表名默认值 */
    private void fillTypeFields(SkillRepo repo, TextField gitUrl, Checkbox autoSync, TextField localPath,
                                TextField databaseName, TextField skillsTableName, Checkbox writeable,
                                TextField directory) {
        JSONObject config = StrUtil.isBlank(repo.getConfig())
                ? JSONUtil.createObj() : JSONUtil.parseObj(repo.getConfig());
        gitUrl.setValue(StrUtil.nullToEmpty(config.getStr("url")));
        autoSync.setValue(config.getBool("autoSync", true));
        localPath.setValue(StrUtil.nullToEmpty(config.getStr("localPath")));
        databaseName.setValue(StrUtil.nullToEmpty(config.getStr("databaseName")));
        skillsTableName.setValue(StrUtil.blankToDefault(config.getStr("skillsTableName"), "skills"));
        writeable.setValue(config.getBool("writeable", false));
        directory.setValue(StrUtil.blankToDefault(config.getStr("directory"), "skills"));
    }

    /**
     * 校验当前类型必填项并组装 config JSON；校验失败提示后返回 null。
     * 选填项只存非空值，布尔项始终存。
     */
    private String buildConfigJson(String type, TextField gitUrl, Checkbox autoSync, TextField localPath,
                                   TextField databaseName, TextField skillsTableName, Checkbox writeable,
                                   TextField directory) {
        JSONObject config = JSONUtil.createObj();
        if (SkillRepo.TYPE_GIT.equals(type)) {
            if (StrUtil.isBlank(gitUrl.getValue())) {
                Notify.error("Git 技能仓库必须填写仓库地址");
                return null;
            }
            if (!gitUrl.getValue().trim().matches(FormValidators.URL_PATTERN)
                    && !gitUrl.getValue().trim().matches("^git@[\\w.-]+:[\\w./-]+$")) {
                Notify.error("仓库地址应为 http(s):// 或 git@host:path 形式");
                return null;
            }
            config.set("url", gitUrl.getValue().trim());
            config.set("autoSync", autoSync.getValue());
            if (StrUtil.isNotBlank(localPath.getValue())) {
                config.set("localPath", localPath.getValue().trim());
            }
        } else if (SkillRepo.TYPE_MYSQL.equals(type)) {
            if (StrUtil.isBlank(databaseName.getValue())) {
                Notify.error("MySQL 技能仓库必须填写数据库名");
                return null;
            }
            config.set("databaseName", databaseName.getValue().trim());
            if (StrUtil.isNotBlank(skillsTableName.getValue())) {
                config.set("skillsTableName", skillsTableName.getValue().trim());
            }
            config.set("writeable", writeable.getValue());
        } else if (SkillRepo.TYPE_CLASSPATH.equals(type)) {
            if (StrUtil.isBlank(directory.getValue())) {
                Notify.error("Classpath 技能仓库必须填写目录");
                return null;
            }
            config.set("directory", directory.getValue().trim());
        }
        return config.toString();
    }

    private void confirmDelete(SkillRepo repo) {
        ConfirmDialog dialog = new ConfirmDialog("删除技能仓库",
                "确定删除技能仓库「" + repo.getName() + "」吗？引用它的智能体将移除对应技能来源。",
                "删除", e -> {
            try {
                skillRepoService.deleteSkillRepo(repo.getId());
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

    // ---------- 仓库内技能管理 ----------

    /**
     * 技能管理弹窗：列出仓库内全部技能。
     * git / classpath 仓库只读展示；mysql 仓库支持新增 / 编辑 / 删除。
     */
    private void openSkillsDialog(SkillRepo repo) {
        boolean mysql = SkillRepo.TYPE_MYSQL.equals(repo.getType());
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("技能管理 - " + repo.getName() + (mysql ? "" : "（只读）"));
        dialog.setWidth("760px");

        Grid<AgentSkill> skillGrid = new Grid<>(AgentSkill.class, false);
        Runnable reload = () -> {
            try {
                skillGrid.setItems(skillRepoService.listSkills(repo.getId()));
            } catch (Exception ex) {
                dialog.close();
                Notify.error(ex.getMessage());
            }
        };

        skillGrid.addColumn(AgentSkill::getName).setHeader("名称").setFlexGrow(2);
        skillGrid.addColumn(AgentSkill::getDescription).setHeader("描述").setFlexGrow(4);
        skillGrid.addColumn(s -> s.getResources().size() + " 个").setHeader("资源")
                .setWidth("80px").setFlexGrow(0);
        if (mysql) {
            skillGrid.addComponentColumn(skill -> skillActionButtons(repo, skill, reload))
                    .setHeader("操作").setWidth("170px").setFlexGrow(0);
        }
        skillGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        skillGrid.setHeight("420px");
        reload.run();

        VerticalLayout layout = new VerticalLayout(skillGrid);
        layout.setPadding(false);
        if (mysql) {
            Button add = new Button("新增技能", new Icon(VaadinIcon.PLUS),
                    e -> openSkillFormDialog(repo, null, reload));
            add.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            layout.addComponentAsFirst(add);
            layout.setAlignSelf(Alignment.END, add);
        }
        dialog.add(layout);
        dialog.open();
    }

    /** mysql 仓库的技能行操作：编辑 / 删除 */
    private Component skillActionButtons(SkillRepo repo, AgentSkill skill, Runnable reload) {
        Button edit = new Button("编辑", e -> openSkillFormDialog(repo, skill, reload));
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        Button delete = new Button("删除", e -> {
            ConfirmDialog confirm = new ConfirmDialog("删除技能",
                    "确定删除技能「" + skill.getName() + "」吗？引用该仓库的智能体将同步移除该技能。",
                    "删除", ev -> {
                try {
                    skillRepoService.deleteSkill(repo.getId(), skill.getName());
                    reload.run();
                    Notify.success("删除成功");
                } catch (Exception ex) {
                    Notify.error(ex.getMessage());
                }
            });
            confirm.setConfirmButtonTheme("error primary");
            confirm.setCancelable(true);
            confirm.setCancelText("取消");
            confirm.open();
        });
        delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        return new HorizontalLayout(edit, delete);
    }

    /**
     * 技能编辑弹窗（仅 mysql 仓库）：名称 / 描述 / 技能内容 + 资源（路径 -> 内容）列表。
     * 技能名是身份标识，编辑时不可改；资源行可动态增删。
     */
    private void openSkillFormDialog(SkillRepo repo, AgentSkill existing, Runnable reload) {
        boolean isNew = existing == null;
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isNew ? "新增技能" : "编辑技能 - " + existing.getName());
        dialog.setWidth("680px");

        TextField name = new TextField("名称");
        name.setPlaceholder("如：pdf-tools");
        name.setHelperText("技能唯一标识，不能包含 / \\ ..");
        name.setMaxLength(255);
        name.setRequiredIndicatorVisible(true);
        name.setEnabled(isNew);

        TextField description = new TextField("描述");
        description.setPlaceholder("一句话说明技能的用途，agent 据此判断是否加载");
        description.setRequiredIndicatorVisible(true);

        TextArea content = new TextArea("技能内容");
        content.setPlaceholder("Markdown 格式的技能指令（SKILL.md 正文）");
        content.setWidthFull();
        content.setHeight("220px");
        content.setRequiredIndicatorVisible(true);

        // 资源行：路径 + 内容，可动态增删
        VerticalLayout resourceRows = new VerticalLayout();
        resourceRows.setPadding(false);
        resourceRows.setSpacing(false);
        Button addResource = new Button("添加资源", new Icon(VaadinIcon.PLUS),
                e -> resourceRows.add(resourceRow(resourceRows, "", "")));
        addResource.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        if (!isNew) {
            name.setValue(existing.getName());
            description.setValue(existing.getDescription());
            content.setValue(existing.getSkillContent());
            existing.getResources().forEach((path, text) ->
                    resourceRows.add(resourceRow(resourceRows, path, text)));
        }

        FormLayout form = new FormLayout(name, description);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
        form.setColspan(description, 2);
        H3 resourceTitle = new H3("资源文件");
        resourceTitle.getStyle().set("margin", "var(--lumo-space-s) 0 0 0")
                .set("font-size", "var(--lumo-font-size-m)");
        VerticalLayout layout = new VerticalLayout(form, content, resourceTitle, resourceRows, addResource);
        layout.setPadding(false);
        dialog.add(layout);

        Button cancel = new Button("取消", e -> dialog.close());
        Button save = new Button("保存", e -> {
            AgentSkill skill = buildSkill(name, description, content, resourceRows);
            if (skill == null) {
                return;
            }
            try {
                skillRepoService.saveSkill(repo.getId(), skill);
                dialog.close();
                reload.run();
                Notify.success("保存成功");
            } catch (Exception ex) {
                Notify.error(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    /** 一行资源编辑：路径输入 + 内容输入 + 删除按钮 */
    private Component resourceRow(VerticalLayout container, String path, String text) {
        TextField pathField = new TextField();
        pathField.setPlaceholder("资源路径，如 scripts/run.py");
        pathField.setWidth("240px");
        pathField.setValue(path);
        TextArea contentField = new TextArea();
        contentField.setPlaceholder("资源内容");
        contentField.setHeight("72px");
        contentField.setWidthFull();
        contentField.setValue(text);
        HorizontalLayout row = new HorizontalLayout();
        Button remove = new Button(new Icon(VaadinIcon.CLOSE_SMALL), e -> container.remove(row));
        remove.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        row.add(pathField, contentField, remove);
        row.setWidthFull();
        row.expand(contentField);
        row.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        return row;
    }

    /**
     * 校验并组装 AgentSkill；失败提示后返回 null。
     * 规则与 MysqlSkillRepository 一致：名称非空且不含路径分隔符，资源路径非空且不重复。
     */
    private AgentSkill buildSkill(TextField name, TextField description, TextArea content,
                                  VerticalLayout resourceRows) {
        if (StrUtil.isBlank(name.getValue())) {
            Notify.error("技能名称不能为空");
            return null;
        }
        String skillName = name.getValue().trim();
        if (skillName.contains("..") || skillName.contains("/") || skillName.contains("\\")) {
            Notify.error("技能名称不能包含 / \\ ..");
            return null;
        }
        if (StrUtil.isBlank(description.getValue())) {
            Notify.error("技能描述不能为空");
            return null;
        }
        if (StrUtil.isBlank(content.getValue())) {
            Notify.error("技能内容不能为空");
            return null;
        }
        Map<String, String> resources = new LinkedHashMap<>();
        for (Component row : resourceRows.getChildren().toList()) {
            TextField pathField = (TextField) ((HorizontalLayout) row).getComponentAt(0);
            TextArea contentField = (TextArea) ((HorizontalLayout) row).getComponentAt(1);
            String path = StrUtil.trimToEmpty(pathField.getValue());
            if (path.isEmpty()) {
                Notify.error("资源路径不能为空");
                return null;
            }
            if (resources.put(path, contentField.getValue()) != null) {
                Notify.error("资源路径重复：" + path);
                return null;
            }
        }
        return new AgentSkill(skillName, description.getValue().trim(), content.getValue(), resources);
    }
}

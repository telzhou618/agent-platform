package com.example.agent.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;

/**
 * 简单分页条：总数 + 每页条数选择 + 上一页/下一页。
 * 页码或每页条数变化时回调 {@link PageLoader} 加载数据，
 * 加载完成后需调用 {@link #setTotal(long)} 刷新分页状态。
 */
public class PaginationBar extends HorizontalLayout {

    /** 按页码和每页条数加载数据 */
    @FunctionalInterface
    public interface PageLoader {
        void load(int page, int pageSize);
    }

    private final PageLoader loader;
    private final Span totalText = new Span();
    private final Span pageText = new Span();
    private final Button prev = new Button(new Icon(VaadinIcon.ANGLE_LEFT));
    private final Button next = new Button(new Icon(VaadinIcon.ANGLE_RIGHT));

    private int page = 1;
    private int pageSize = 10;
    private long total;

    public PaginationBar(PageLoader loader) {
        this.loader = loader;
        setWidthFull();
        setJustifyContentMode(JustifyContentMode.END);
        setDefaultVerticalComponentAlignment(Alignment.CENTER);

        prev.addClickListener(e -> load(page - 1));
        next.addClickListener(e -> load(page + 1));
        prev.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        next.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        Select<Integer> sizeSelect = new Select<>();
        sizeSelect.setItems(10, 20, 50, 100);
        sizeSelect.setValue(pageSize);
        sizeSelect.setWidth("6em");
        sizeSelect.addValueChangeListener(e -> {
            pageSize = e.getValue();
            load(1);
        });

        add(totalText, sizeSelect, prev, pageText, next);
        updateState();
    }

    /** 重新加载当前页（数据增删改后调用） */
    public void refresh() {
        load(page);
    }

    /** 回到第一页并重新加载（搜索条件变化后调用） */
    public void reset() {
        load(1);
    }

    /** 数据加载完成后更新总条数；若当前页超出末页（如删除了末页最后一条）则自动回退 */
    public void setTotal(long total) {
        this.total = total;
        if (page > totalPages()) {
            load(totalPages());
            return;
        }
        updateState();
    }

    private void load(int page) {
        this.page = Math.max(1, Math.min(page, totalPages()));
        loader.load(this.page, pageSize);
    }

    private int totalPages() {
        return (int) Math.max(1, (total + pageSize - 1) / pageSize);
    }

    private void updateState() {
        totalText.setText("共 " + total + " 条");
        pageText.setText(page + " / " + totalPages());
        prev.setEnabled(page > 1);
        next.setEnabled(page < totalPages());
    }
}

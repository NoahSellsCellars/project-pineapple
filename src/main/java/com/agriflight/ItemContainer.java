package com.agriflight;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Composite pattern: a composite. It holds child items and containers and delegates to them. */
final class ItemContainer extends FarmElement {
    private final List<FarmItem> children = new ArrayList<>();

    ItemContainer(FarmProperties properties) {
        super(properties);
    }

    @Override
    public List<FarmItem> children() {
        return List.copyOf(children);
    }

    @Override
    public BigDecimal totalPrice() {
        BigDecimal total = properties().price();
        for (FarmItem child : children) {
            total = total.add(child.totalPrice());
        }
        return total;
    }

    void add(FarmItem child) {
        children.add(child);
    }

    void remove(FarmItem child) {
        children.remove(child);
    }
}

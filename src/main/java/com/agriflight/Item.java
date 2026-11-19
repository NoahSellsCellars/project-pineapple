package com.agriflight;

import java.math.BigDecimal;
import java.util.List;

/** Composite pattern: a leaf. An item has no children, so its total value is its own price. */
final class Item extends FarmElement {
    Item(FarmProperties properties) {
        super(properties);
    }

    @Override
    public List<FarmItem> children() {
        return List.of();
    }

    @Override
    public BigDecimal totalPrice() {
        return properties().price();
    }
}

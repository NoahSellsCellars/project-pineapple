package com.noahsellers.agriflight;

import java.util.ArrayList;
import java.util.List;

final class ItemContainer extends FarmElement {
    private final List<FarmItem> children = new ArrayList<>();

    ItemContainer(FarmProperties properties) {
        super(properties);
    }

    @Override
    public List<FarmItem> children() {
        return List.copyOf(children);
    }

    void add(FarmItem child) {
        children.add(child);
    }

    void remove(FarmItem child) {
        children.remove(child);
    }
}

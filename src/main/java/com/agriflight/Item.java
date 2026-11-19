package com.agriflight;

import java.util.List;

final class Item extends FarmElement {
    Item(FarmProperties properties) {
        super(properties);
    }

    @Override
    public List<FarmItem> children() {
        return List.of();
    }
}

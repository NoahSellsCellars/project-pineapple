package com.agriflight;

import java.math.BigDecimal;
import java.util.List;

/** Composite pattern: the component interface shared by items (leaves) and item-containers (composites). */
interface FarmItem {
    FarmProperties properties();
    void update(FarmProperties properties);
    List<FarmItem> children();

    /**
     * The total value of this element: an item's own price, or a container's price plus the
     * total value of everything inside it.
     */
    BigDecimal totalPrice();
}

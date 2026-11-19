package com.noahsellers.agriflight;

import java.util.List;

interface FarmItem {
    FarmProperties properties();
    void update(FarmProperties properties);
    List<FarmItem> children();
}

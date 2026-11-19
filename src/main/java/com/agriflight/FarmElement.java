package com.agriflight;

abstract class FarmElement implements FarmItem {
    private FarmProperties properties;

    FarmElement(FarmProperties properties) {
        this.properties = properties;
    }

    @Override
    public FarmProperties properties() {
        return properties;
    }

    @Override
    public void update(FarmProperties properties) {
        this.properties = properties;
    }

    @Override
    public String toString() {
        return properties.name();
    }
}

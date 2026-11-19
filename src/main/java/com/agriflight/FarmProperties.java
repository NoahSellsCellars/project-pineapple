package com.agriflight;

import java.math.BigDecimal;

record FarmProperties(String name, BigDecimal price, double x, double y,
                      double length, double width, double height) {
    static FarmProperties parse(String name, String price, String x, String y,
                                String length, String width, String height) {
        FarmProperties result;
        try {
            result = new FarmProperties(name.strip(), new BigDecimal(price.strip()),
                    Double.parseDouble(x), Double.parseDouble(y), Double.parseDouble(length),
                    Double.parseDouble(width), Double.parseDouble(height));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid number for price, coordinates, and dimensions.");
        }
        if (result.name().isBlank() || result.price().signum() < 0) {
            throw new IllegalArgumentException("Enter a name and a nonnegative price.");
        }
        if (!Double.isFinite(result.x()) || !Double.isFinite(result.y())
                || !Double.isFinite(result.length()) || !Double.isFinite(result.width())
                || !Double.isFinite(result.height()) || result.length() <= 0
                || result.width() <= 0 || result.height() <= 0) {
            throw new IllegalArgumentException("Coordinates must be finite and dimensions must be positive and finite.");
        }
        return result;
    }
}

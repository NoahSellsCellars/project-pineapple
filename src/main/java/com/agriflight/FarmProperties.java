package com.agriflight;

import java.math.BigDecimal;

record FarmProperties(String name, BigDecimal price, double x, double y,
                      double length, double width, double height) {
    static FarmProperties parse(String name, String price, String x, String y,
                                String length, String width, String height) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("Name can't be blank.");
        }
        BigDecimal parsedPrice;
        try {
            parsedPrice = new BigDecimal(price.strip());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Price must be a number, for example 1500 or 12.50.");
        }
        if (parsedPrice.signum() < 0) {
            throw new IllegalArgumentException("Price can't be negative.");
        }
        return new FarmProperties(name.strip(), parsedPrice,
                coordinate("Location-X", x), coordinate("Location-Y", y),
                dimension("Length", length), dimension("Width", width), dimension("Height", height));
    }

    private static double coordinate(String field, String text) {
        double value = number(field, text);
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be a finite number.");
        }
        return value;
    }

    private static double dimension(String field, String text) {
        double value = number(field, text);
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(field + " must be a number greater than 0.");
        }
        return value;
    }

    private static double number(String field, String text) {
        try {
            return Double.parseDouble(text.strip());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be a number.");
        }
    }

    /** Parses the seven property values in the order name, price, x, y, length, width, height. */
    static FarmProperties parse(String... values) {
        if (values.length != 7) {
            throw new IllegalArgumentException("Expected 7 property values.");
        }
        return parse(values[0], values[1], values[2], values[3], values[4], values[5], values[6]);
    }
}

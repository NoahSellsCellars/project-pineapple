package com.agriflight;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader and writer for the API, so the app needs no external libraries.
 * Objects become {@code Map<String, Object>}, arrays {@code List<Object>}, numbers {@link BigDecimal}.
 */
final class Json {
    private Json() {}

    static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.value();
        parser.skipWhitespace();
        if (parser.pos < text.length()) {
            throw parser.error("unexpected text after the JSON value");
        }
        return value;
    }

    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            quote(text, out);
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            out.append(value);
        } else if (value instanceof BigDecimal number) {
            out.append(number.toPlainString());
        } else if (value instanceof Double number) {
            out.append(Double.isFinite(number) ? BigDecimal.valueOf(number).stripTrailingZeros().toPlainString() : "null");
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                quote(String.valueOf(entry.getKey()), out);
                out.append(':');
                write(entry.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                write(list.get(i), out);
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("Can't write " + value.getClass().getSimpleName() + " as JSON");
        }
    }

    private static void quote(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        Object value() {
            skipWhitespace();
            if (pos >= text.length()) {
                throw error("unexpected end of input");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> {
                    if (c == '-' || Character.isDigit(c)) {
                        yield number();
                    }
                    throw error("unexpected character '" + c + "'");
                }
            };
        }

        private Map<String, Object> object() {
            pos++;
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw error("expected a property name in double quotes");
                }
                String key = string();
                skipWhitespace();
                if (peek() != ':') {
                    throw error("expected ':'");
                }
                pos++;
                map.put(key, value());
                skipWhitespace();
                char next = peek();
                pos++;
                if (next == '}') {
                    return map;
                }
                if (next != ',') {
                    pos--;
                    throw error("expected ',' or '}'");
                }
            }
        }

        private List<Object> array() {
            pos++;
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(value());
                skipWhitespace();
                char next = peek();
                pos++;
                if (next == ']') {
                    return list;
                }
                if (next != ',') {
                    pos--;
                    throw error("expected ',' or ']'");
                }
            }
        }

        private String string() {
            pos++;
            StringBuilder out = new StringBuilder();
            while (true) {
                if (pos >= text.length()) {
                    throw error("unterminated string");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c < 0x20) {
                    throw error("control character in string");
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (pos >= text.length()) {
                    throw error("unterminated string");
                }
                char escape = text.charAt(pos++);
                switch (escape) {
                    case '"', '\\', '/' -> out.append(escape);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (pos + 4 > text.length()) {
                            throw error("incomplete \\u escape");
                        }
                        try {
                            out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException exception) {
                            throw error("invalid \\u escape");
                        }
                        pos += 4;
                    }
                    default -> throw error("invalid escape '\\" + escape + "'");
                }
            }
        }

        private BigDecimal number() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            digits();
            if (peek() == '.') {
                pos++;
                digits();
            }
            if (peek() == 'e' || peek() == 'E') {
                pos++;
                if (peek() == '+' || peek() == '-') {
                    pos++;
                }
                digits();
            }
            return new BigDecimal(text.substring(start, pos));
        }

        private void digits() {
            int start = pos;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            if (pos == start) {
                throw error("expected a digit");
            }
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, pos)) {
                throw error("unexpected text");
            }
            pos += word.length();
            return value;
        }

        private char peek() {
            return pos < text.length() ? text.charAt(pos) : '\0';
        }

        void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException("Invalid JSON: " + message + " at position " + pos + ".");
        }
    }
}

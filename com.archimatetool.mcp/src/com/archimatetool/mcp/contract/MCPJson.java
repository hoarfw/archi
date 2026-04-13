/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.mcp.contract;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer for MCP request and response payloads.
 */
@SuppressWarnings("nls")
final class MCPJson {

    private MCPJson() {
    }

    static Object parse(String json) {
        if(json == null || json.isBlank()) {
            throw new IllegalArgumentException("request body is empty");
        }

        Parser parser = new Parser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();

        if(!parser.isEnd()) {
            throw new IllegalArgumentException("invalid json");
        }

        return value;
    }

    static Map<String, Object> requireObject(Object value, String message) {
        if(!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException(message);
        }

        Map<String, Object> result = new LinkedHashMap<>();

        for(Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if(!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException(message);
            }

            result.put(key, entry.getValue());
        }

        return result;
    }

    static String write(Object value) {
        StringBuilder builder = new StringBuilder();
        appendValue(builder, value);
        return builder.toString();
    }

    private static void appendValue(StringBuilder builder, Object value) {
        if(value == null) {
            builder.append("null");
            return;
        }

        if(value instanceof String string) {
            builder.append('"').append(escape(string)).append('"');
            return;
        }

        if(value instanceof Boolean || value instanceof Integer || value instanceof Long || value instanceof Short
                || value instanceof Byte || value instanceof BigDecimal) {
            builder.append(value);
            return;
        }

        if(value instanceof Float floatValue) {
            appendFloatingPoint(builder, floatValue.doubleValue());
            return;
        }

        if(value instanceof Double doubleValue) {
            appendFloatingPoint(builder, doubleValue.doubleValue());
            return;
        }

        if(value instanceof Number number) {
            builder.append(number);
            return;
        }

        if(value instanceof Map<?, ?> map) {
            builder.append('{');
            boolean first = true;

            for(Map.Entry<?, ?> entry : map.entrySet()) {
                if(!first) {
                    builder.append(',');
                }

                builder.append('"').append(escape(String.valueOf(entry.getKey()))).append('"').append(':');
                appendValue(builder, entry.getValue());
                first = false;
            }

            builder.append('}');
            return;
        }

        if(value instanceof Iterable<?> iterable) {
            builder.append('[');
            boolean first = true;

            for(Object item : iterable) {
                if(!first) {
                    builder.append(',');
                }

                appendValue(builder, item);
                first = false;
            }

            builder.append(']');
            return;
        }

        throw new IllegalArgumentException("unsupported json value type: " + value.getClass().getName());
    }

    private static void appendFloatingPoint(StringBuilder builder, double value) {
        if(!Double.isFinite(value)) {
            throw new IllegalArgumentException("non-finite json number");
        }

        builder.append(Double.toString(value));
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 8);

        for(int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);

            switch(ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if(ch < 0x20) {
                        builder.append(String.format("\\u%04x", Integer.valueOf(ch)));
                    }
                    else {
                        builder.append(ch);
                    }
                }
            }
        }

        return builder.toString();
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text;
        }

        private Object parseValue() {
            skipWhitespace();

            if(isEnd()) {
                throw new IllegalArgumentException("invalid json");
            }

            return switch(text.charAt(index)) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            index++;
            skipWhitespace();

            if(consume('}')) {
                return result;
            }

            while(true) {
                skipWhitespace();
                if(isEnd() || text.charAt(index) != '"') {
                    throw new IllegalArgumentException("invalid json object");
                }

                String key = parseString();
                skipWhitespace();

                if(!consume(':')) {
                    throw new IllegalArgumentException("invalid json object");
                }

                result.put(key, parseValue());
                skipWhitespace();

                if(consume('}')) {
                    return result;
                }

                if(!consume(',')) {
                    throw new IllegalArgumentException("invalid json object");
                }
            }
        }

        private List<Object> parseArray() {
            List<Object> result = new ArrayList<>();
            index++;
            skipWhitespace();

            if(consume(']')) {
                return result;
            }

            while(true) {
                result.add(parseValue());
                skipWhitespace();

                if(consume(']')) {
                    return result;
                }

                if(!consume(',')) {
                    throw new IllegalArgumentException("invalid json array");
                }
            }
        }

        private String parseString() {
            StringBuilder builder = new StringBuilder();
            index++;

            while(!isEnd()) {
                char ch = text.charAt(index++);

                if(ch == '"') {
                    return builder.toString();
                }

                if(ch == '\\') {
                    if(isEnd()) {
                        throw new IllegalArgumentException("invalid json string escape");
                    }

                    char escaped = text.charAt(index++);

                    switch(escaped) {
                        case '"', '\\', '/' -> builder.append(escaped);
                        case 'b' -> builder.append('\b');
                        case 'f' -> builder.append('\f');
                        case 'n' -> builder.append('\n');
                        case 'r' -> builder.append('\r');
                        case 't' -> builder.append('\t');
                        case 'u' -> builder.append(parseUnicodeEscape());
                        default -> throw new IllegalArgumentException("invalid json string escape");
                    }
                }
                else {
                    builder.append(ch);
                }
            }

            throw new IllegalArgumentException("unterminated json string");
        }

        private char parseUnicodeEscape() {
            if(index + 4 > text.length()) {
                throw new IllegalArgumentException("invalid unicode escape");
            }

            String hex = text.substring(index, index + 4);
            index += 4;

            try {
                return (char)Integer.parseInt(hex, 16);
            }
            catch(NumberFormatException ex) {
                throw new IllegalArgumentException("invalid unicode escape", ex);
            }
        }

        private Object parseLiteral(String literal, Object value) {
            if(text.startsWith(literal, index)) {
                index += literal.length();
                return value;
            }

            throw new IllegalArgumentException("invalid json literal");
        }

        private Number parseNumber() {
            int start = index;

            if(consume('-') && (isEnd() || !Character.isDigit(text.charAt(index)))) {
                throw new IllegalArgumentException("invalid json number");
            }

            if(!consumeDigits()) {
                throw new IllegalArgumentException("invalid json number");
            }

            boolean decimal = false;

            if(consume('.')) {
                decimal = true;

                if(!consumeDigits()) {
                    throw new IllegalArgumentException("invalid json number");
                }
            }

            if(!isEnd() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                decimal = true;
                index++;

                if(!isEnd() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                    index++;
                }

                if(!consumeDigits()) {
                    throw new IllegalArgumentException("invalid json number");
                }
            }

            String token = text.substring(start, index);

            try {
                return decimal ? Double.valueOf(token) : Long.valueOf(token);
            }
            catch(NumberFormatException ex) {
                throw new IllegalArgumentException("invalid json number", ex);
            }
        }

        private boolean consumeDigits() {
            int start = index;

            while(!isEnd() && Character.isDigit(text.charAt(index))) {
                index++;
            }

            return index > start;
        }

        private boolean consume(char expected) {
            if(!isEnd() && text.charAt(index) == expected) {
                index++;
                return true;
            }

            return false;
        }

        private void skipWhitespace() {
            while(!isEnd() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        private boolean isEnd() {
            return index >= text.length();
        }
    }
}

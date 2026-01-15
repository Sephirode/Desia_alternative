package com.desia.game.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SimpleJsonParser {
    private final String input;
    private int index;

    private SimpleJsonParser(String input) {
        this.input = input;
    }

    static Object parse(String input) {
        SimpleJsonParser parser = new SimpleJsonParser(input);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.index < parser.input.length()) {
            throw new IllegalArgumentException("Trailing content after JSON value.");
        }
        return value;
    }

    private Object parseValue() {
        skipWhitespace();
        if (index >= input.length()) {
            throw new IllegalArgumentException("Unexpected end of input.");
        }
        char ch = input.charAt(index);
        if (ch == '{') {
            return parseObject();
        }
        if (ch == '[') {
            return parseArray();
        }
        if (ch == '"') {
            return parseString();
        }
        if (ch == 't' || ch == 'f') {
            return parseBoolean();
        }
        if (ch == 'n') {
            return parseNull();
        }
        if (ch == '-' || Character.isDigit(ch)) {
            return parseNumber();
        }
        throw new IllegalArgumentException("Unexpected character: " + ch);
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (peek('}')) {
            expect('}');
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            if (peek(',')) {
                expect(',');
                continue;
            }
            if (peek('}')) {
                expect('}');
                break;
            }
            throw new IllegalArgumentException("Expected ',' or '}' in object.");
        }
        return map;
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        expect('[');
        skipWhitespace();
        if (peek(']')) {
            expect(']');
            return list;
        }
        while (true) {
            Object value = parseValue();
            list.add(value);
            skipWhitespace();
            if (peek(',')) {
                expect(',');
                continue;
            }
            if (peek(']')) {
                expect(']');
                break;
            }
            throw new IllegalArgumentException("Expected ',' or ']' in array.");
        }
        return list;
    }

    private String parseString() {
        expect('"');
        StringBuilder builder = new StringBuilder();
        while (index < input.length()) {
            char ch = input.charAt(index++);
            if (ch == '"') {
                return builder.toString();
            }
            if (ch == '\\') {
                if (index >= input.length()) {
                    throw new IllegalArgumentException("Unexpected end of string escape.");
                }
                char escape = input.charAt(index++);
                switch (escape) {
                    case '"':
                    case '\\':
                    case '/':
                        builder.append(escape);
                        break;
                    case 'b':
                        builder.append('\b');
                        break;
                    case 'f':
                        builder.append('\f');
                        break;
                    case 'n':
                        builder.append('\n');
                        break;
                    case 'r':
                        builder.append('\r');
                        break;
                    case 't':
                        builder.append('\t');
                        break;
                    case 'u':
                        builder.append(parseUnicode());
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid escape sequence: \\" + escape);
                }
            } else {
                builder.append(ch);
            }
        }
        throw new IllegalArgumentException("Unterminated string literal.");
    }

    private char parseUnicode() {
        if (index + 4 > input.length()) {
            throw new IllegalArgumentException("Invalid unicode escape.");
        }
        String hex = input.substring(index, index + 4);
        index += 4;
        return (char) Integer.parseInt(hex, 16);
    }

    private Boolean parseBoolean() {
        if (input.startsWith("true", index)) {
            index += 4;
            return Boolean.TRUE;
        }
        if (input.startsWith("false", index)) {
            index += 5;
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("Invalid boolean literal.");
    }

    private Object parseNull() {
        if (input.startsWith("null", index)) {
            index += 4;
            return null;
        }
        throw new IllegalArgumentException("Invalid null literal.");
    }

    private Number parseNumber() {
        int start = index;
        if (input.charAt(index) == '-') {
            index++;
        }
        while (index < input.length() && Character.isDigit(input.charAt(index))) {
            index++;
        }
        boolean isDecimal = false;
        if (index < input.length() && input.charAt(index) == '.') {
            isDecimal = true;
            index++;
            while (index < input.length() && Character.isDigit(input.charAt(index))) {
                index++;
            }
        }
        if (index < input.length()) {
            char ch = input.charAt(index);
            if (ch == 'e' || ch == 'E') {
                isDecimal = true;
                index++;
                if (index < input.length() && (input.charAt(index) == '+' || input.charAt(index) == '-')) {
                    index++;
                }
                while (index < input.length() && Character.isDigit(input.charAt(index))) {
                    index++;
                }
            }
        }
        String number = input.substring(start, index);
        if (isDecimal) {
            return Double.parseDouble(number);
        }
        return Long.parseLong(number);
    }

    private void skipWhitespace() {
        while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
            index++;
        }
    }

    private boolean peek(char ch) {
        return index < input.length() && input.charAt(index) == ch;
    }

    private void expect(char ch) {
        if (index >= input.length() || input.charAt(index) != ch) {
            throw new IllegalArgumentException("Expected '" + ch + "'");
        }
        index++;
    }
}

package com.amanoform.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Configuration parser for Amanoform {@code .af} files.
 *
 * <p>Amanoform uses a simplified HCL-inspired configuration format.
 * This is intentionally not HCL — we believe in doing things by hand,
 * and that includes writing our own parser. In Java. With regex.
 * What could go wrong.</p>
 *
 * <p>The Python implementation was 166 lines. This Java implementation
 * achieves the same functionality in approximately twice that, thanks
 * to explicit type declarations, checked exceptions, and the natural
 * verbosity that makes Java the language enterprise architects dream
 * about.</p>
 *
 * @author Carlos Loya
 * @version 0.1.0
 */
public class AmanoformConfigurationParser {

    private static final Pattern BLOCK_PATTERN = Pattern.compile(
            "(\\w+)\\s+\"([^\"]+)\"(?:\\s+\"([^\"]+)\")?\\s*\\{");

    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile(
            "(\\w+)\\s*=\\s*(.+)");

    /** Prevent instantiation. */
    private AmanoformConfigurationParser() {}

    /**
     * Load and parse an Amanoform configuration file.
     *
     * @param path path to the {@code .af} configuration file
     * @return parsed configuration as a nested map
     * @throws RuntimeException if the file cannot be read or parsed
     */
    public static Map<String, Object> loadConfig(String path) {
        Path configPath = Path.of(path);
        if (!Files.exists(configPath)) {
            throw new RuntimeException(
                    "Configuration file \"" + path + "\" not found.\n"
                    + "Have you created your Amanoform configuration? "
                    + "See documentation for examples.");
        }

        try {
            String text = Files.readString(configPath);
            return parse(text);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to read configuration file \"" + path + "\": " + e.getMessage(), e);
        }
    }

    /**
     * Parse the Amanoform configuration format.
     *
     * <p>Supports blocks like:</p>
     * <pre>
     *   provider "aws" { ... }
     *   resource "af_ec2_instance" "name" { ... }
     * </pre>
     *
     * @param text the raw configuration text
     * @return parsed configuration as a nested map
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String text) {
        Map<String, Object> result = new HashMap<>();
        text = stripComments(text);
        int pos = 0;

        while (pos < text.length()) {
            // Skip whitespace
            if (Character.isWhitespace(text.charAt(pos))) {
                pos++;
                continue;
            }

            // Try to match a block
            Matcher blockMatcher = BLOCK_PATTERN.matcher(text.substring(pos));
            if (blockMatcher.find() && blockMatcher.start() == 0) {
                String keyword = blockMatcher.group(1);
                String label1 = blockMatcher.group(2);
                String label2 = blockMatcher.group(3);
                pos += blockMatcher.end();

                BlockExtractionResult blockResult = extractBlock(text, pos);
                pos = blockResult.endPosition();

                Map<String, Object> attrs = parseAttributes(blockResult.body());

                if (!result.containsKey(keyword)) {
                    result.put(keyword, new HashMap<String, Object>());
                }

                Map<String, Object> keywordMap = (Map<String, Object>) result.get(keyword);

                if (label2 != null) {
                    if (!keywordMap.containsKey(label1)) {
                        keywordMap.put(label1, new HashMap<String, Object>());
                    }
                    Map<String, Object> label1Map = (Map<String, Object>) keywordMap.get(label1);
                    label1Map.put(label2, attrs);
                } else {
                    keywordMap.put(label1, attrs);
                }

                continue;
            }

            // If we can't parse anything, skip a character
            pos++;
        }

        return result;
    }

    /**
     * Remove single-line comments ({@code #} and {@code //}).
     *
     * @param text the raw text to strip comments from
     * @return the text with comments removed
     */
    private static String stripComments(String text) {
        StringBuilder result = new StringBuilder();
        String[] lines = text.split("\n", -1);

        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            String line = lines[lineIdx];
            boolean inString = false;
            StringBuilder lineResult = new StringBuilder();

            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);

                if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                    inString = !inString;
                    lineResult.append(c);
                } else if (!inString && c == '#') {
                    break;
                } else if (!inString && c == '/' && i + 1 < line.length()
                        && line.charAt(i + 1) == '/') {
                    break;
                } else {
                    lineResult.append(c);
                }
            }

            result.append(lineResult);
            if (lineIdx < lines.length - 1) {
                result.append("\n");
            }
        }

        return result.toString();
    }

    /**
     * Extract content between matched braces, starting after the opening brace.
     *
     * @param text the full text
     * @param pos the position right after the opening brace
     * @return the extracted block body and the position after the closing brace
     */
    private static BlockExtractionResult extractBlock(String text, int pos) {
        int depth = 1;
        int start = pos;

        while (pos < text.length() && depth > 0) {
            if (text.charAt(pos) == '{') {
                depth++;
            } else if (text.charAt(pos) == '}') {
                depth--;
            }
            pos++;
        }

        return new BlockExtractionResult(text.substring(start, pos - 1), pos);
    }

    /**
     * Parse key = value pairs from a block body.
     *
     * @param body the block body text
     * @return a map of attribute key-value pairs
     */
    private static Map<String, Object> parseAttributes(String body) {
        Map<String, Object> attrs = new HashMap<>();

        for (String line : body.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            Matcher matcher = ATTRIBUTE_PATTERN.matcher(line);
            if (matcher.matches()) {
                String key = matcher.group(1);
                Object value = parseValue(matcher.group(2).trim());
                attrs.put(key, value);
            }
        }

        return attrs;
    }

    /**
     * Parse a configuration value into its appropriate Java type.
     *
     * <p>Supports strings, booleans, integers, and floating-point numbers.
     * In Python, this returned a union type {@code str | int | float | bool}.
     * In Java, it returns {@code Object}, because generics were added in 2004
     * and we've been working around them ever since.</p>
     *
     * @param value the raw value string
     * @return the parsed value as the appropriate type
     */
    private static Object parseValue(String value) {
        // String
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }

        // Boolean
        if ("true".equals(value)) {
            return Boolean.TRUE;
        }
        if ("false".equals(value)) {
            return Boolean.FALSE;
        }

        // Integer
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            // Not an integer, try the next type
        }

        // Float
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            // Not a float either
        }

        // Give up and return as string
        return value;
    }

    /**
     * Internal record for block extraction results.
     *
     * <p>In Python, this was a {@code tuple[str, int]}. In Java, it's a record.
     * At least records exist now. Before Java 16, this would have been a class
     * with a constructor, two private fields, two getter methods, equals(),
     * hashCode(), and toString().</p>
     */
    private record BlockExtractionResult(String body, int endPosition) {}
}

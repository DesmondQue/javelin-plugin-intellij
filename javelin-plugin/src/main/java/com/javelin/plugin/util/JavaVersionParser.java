package com.javelin.plugin.util;

public final class JavaVersionParser {

    private JavaVersionParser() {
    }

    public static int parseJavaMajor(String text) {
        if (text == null || text.isBlank()) {
            return -1;
        }
        for (String token : text.replace('"', ' ').split("\\s+")) {
            if (token.matches("\\d+(\\.\\d+)?(\\.\\d+)?([_+\\-].*)?")) {
                String[] parts = token.split("\\.");
                if (parts.length > 0) {
                    try {
                        int v = Integer.parseInt(parts[0]);
                        if (v == 1 && parts.length > 1) {
                            return Integer.parseInt(parts[1]);
                        }
                        return v;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return -1;
    }
}

package com.javelin.plugin.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaVersionParserTest {

    @ParameterizedTest(name = "input=\"{0}\" → major={1}")
    @CsvSource({
        // Modern Java (11+) — version string starts with the major
        "'openjdk version \"21.0.2\" 2024-01-16', 21",
        "'java version \"17.0.5\" 2022-10-18 LTS', 17",
        "'openjdk version \"11.0.21\" 2023-10-17', 11",

        // Java 8 format (1.x.y) — major is the second component
        "'java version \"1.8.0_392\"', 8",
        "'openjdk version \"1.8.0_382\"', 8",

        // Bare version tokens
        "'21.0.2', 21",
        "'17', 17",
        "'1.8.0', 8",

        // JBR output with extra metadata
        "'openjdk 21.0.5 2024-10-15 OpenJDK Runtime', 21",

        // Version buried in noise
        "'some text 11.0.3 more text', 11",
    })
    void parsesJavaVersionStrings(String input, int expectedMajor) {
        assertEquals(expectedMajor, JavaVersionParser.parseJavaMajor(input));
    }

    @Test
    void returnsNegativeOneForNull() {
        assertEquals(-1, JavaVersionParser.parseJavaMajor(null));
    }

    @Test
    void returnsNegativeOneForBlank() {
        assertEquals(-1, JavaVersionParser.parseJavaMajor(""));
        assertEquals(-1, JavaVersionParser.parseJavaMajor("   "));
    }

    @Test
    void returnsNegativeOneForNoVersionToken() {
        assertEquals(-1, JavaVersionParser.parseJavaMajor("no version info here"));
    }

    @Test
    void handlesQuotedVersionString() {
        assertEquals(21, JavaVersionParser.parseJavaMajor("openjdk version \"21.0.1\""));
    }

    @Test
    void parsesJava9() {
        assertEquals(9, JavaVersionParser.parseJavaMajor("java version \"9.0.4\""));
    }

    @Test
    void parsesJava1dot7AsJava7() {
        assertEquals(7, JavaVersionParser.parseJavaMajor("java version \"1.7.0_80\""));
    }
}

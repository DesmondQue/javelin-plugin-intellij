package com.javelin.plugin.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class JavelinServiceExitCodeTest {

    @ParameterizedTest
    @CsvSource({
        "2, No failing tests",
        "3, target classes",
        "4, test classes",
        "5, source directory",
        "6, unexpected error",
        "7, timed out"
    })
    void describeExitCodeContainsKeyPhrase(int code, String phrase) {
        String message = JavelinService.describeExitCode(code);
        assertNotNull(message);
        assertTrue(message.toLowerCase().contains(phrase.toLowerCase()),
                "Expected '" + phrase + "' in message for code " + code + ": " + message);
    }

    @Test
    void describeExitCodeForUnknownCodeContainsCode() {
        String message = JavelinService.describeExitCode(99);
        assertNotNull(message);
        assertTrue(message.contains("99"), "Expected code 99 in: " + message);
    }

    @Test
    void describeExitCodeReturnsNonBlankForAllKnownCodes() {
        for (int code = 2; code <= 7; code++) {
            String message = JavelinService.describeExitCode(code);
            assertNotNull(message);
            assertFalse(message.isBlank(), "Message for code " + code + " should not be blank");
        }
    }

    @Test
    void eachKnownCodeHasDistinctMessage() {
        java.util.Set<String> messages = new java.util.HashSet<>();
        for (int code = 2; code <= 7; code++) {
            messages.add(JavelinService.describeExitCode(code));
        }
        assertEquals(6, messages.size(), "Each exit code should produce a distinct message");
    }
}

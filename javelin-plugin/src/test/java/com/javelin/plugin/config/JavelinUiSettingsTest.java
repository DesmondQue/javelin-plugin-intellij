package com.javelin.plugin.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavelinUiSettingsTest {

    @Test
    void granularityKeyIsNonBlank() {
        assertNotNull(JavelinUiSettings.KEY_GRANULARITY);
        assertFalse(JavelinUiSettings.KEY_GRANULARITY.isBlank());
    }

    @Test
    void rankingStrategyKeyIsNonBlank() {
        assertNotNull(JavelinUiSettings.KEY_RANKING_STRATEGY);
        assertFalse(JavelinUiSettings.KEY_RANKING_STRATEGY.isBlank());
    }

    @Test
    void granularityAndRankingKeysAreDistinct() {
        assertNotEquals(JavelinUiSettings.KEY_GRANULARITY, JavelinUiSettings.KEY_RANKING_STRATEGY);
    }

    @Test
    void allKeysFollowNamingConvention() {
        List<String> allKeys = List.of(
                JavelinUiSettings.KEY_GUTTER_ENABLED,
                JavelinUiSettings.KEY_HIGHLIGHT_ENABLED,
                JavelinUiSettings.KEY_STRIPE_ENABLED,
                JavelinUiSettings.KEY_ALGORITHM,
                JavelinUiSettings.KEY_MAX_THREADS,
                JavelinUiSettings.KEY_VISIBLE_BANDS,
                JavelinUiSettings.KEY_JVM_HOME,
                JavelinUiSettings.KEY_GRANULARITY,
                JavelinUiSettings.KEY_RANKING_STRATEGY,
                JavelinUiSettings.KEY_TIMEOUT_MINUTES
        );
        for (String key : allKeys) {
            assertNotNull(key, "Key should not be null");
            assertTrue(key.startsWith("javelin.ui."), "Key should start with 'javelin.ui.': " + key);
        }
    }

    @Test
    void allKeysAreDistinct() {
        List<String> allKeys = List.of(
                JavelinUiSettings.KEY_GUTTER_ENABLED,
                JavelinUiSettings.KEY_HIGHLIGHT_ENABLED,
                JavelinUiSettings.KEY_STRIPE_ENABLED,
                JavelinUiSettings.KEY_ALGORITHM,
                JavelinUiSettings.KEY_MAX_THREADS,
                JavelinUiSettings.KEY_VISIBLE_BANDS,
                JavelinUiSettings.KEY_JVM_HOME,
                JavelinUiSettings.KEY_GRANULARITY,
                JavelinUiSettings.KEY_RANKING_STRATEGY,
                JavelinUiSettings.KEY_TIMEOUT_MINUTES
        );
        long distinctCount = allKeys.stream().distinct().count();
        assertEquals(allKeys.size(), distinctCount, "All setting keys must be unique");
    }

    @Test
    void defaultAlgorithmIsOchiai() {
        assertEquals("ochiai", JavelinUiSettings.DEFAULT_ALGORITHM);
    }

    @Test
    void defaultGranularityIsStatement() {
        assertEquals("statement", JavelinUiSettings.DEFAULT_GRANULARITY);
    }

    @Test
    void defaultRankingStrategyIsDense() {
        assertEquals("dense", JavelinUiSettings.DEFAULT_RANKING_STRATEGY);
    }

    @Test
    void defaultVisibleBandsContainsAllBands() {
        assertEquals("RED,ORANGE,YELLOW,GREEN", JavelinUiSettings.DEFAULT_VISIBLE_BANDS);
    }

    @Test
    void defaultJvmHomeIsEmpty() {
        assertEquals("", JavelinUiSettings.DEFAULT_JVM_HOME);
    }

    @Test
    void defaultTimeoutMinutesIsZero() {
        assertEquals(0, JavelinUiSettings.DEFAULT_TIMEOUT_MINUTES);
    }

    @Test
    void timeoutKeyFollowsNamingConvention() {
        assertNotNull(JavelinUiSettings.KEY_TIMEOUT_MINUTES);
        assertTrue(JavelinUiSettings.KEY_TIMEOUT_MINUTES.startsWith("javelin.ui."));
    }

    @Test
    void timeoutKeyIsDistinctFromOtherKeys() {
        List<String> otherKeys = List.of(
                JavelinUiSettings.KEY_GUTTER_ENABLED,
                JavelinUiSettings.KEY_HIGHLIGHT_ENABLED,
                JavelinUiSettings.KEY_STRIPE_ENABLED,
                JavelinUiSettings.KEY_ALGORITHM,
                JavelinUiSettings.KEY_MAX_THREADS,
                JavelinUiSettings.KEY_VISIBLE_BANDS,
                JavelinUiSettings.KEY_JVM_HOME,
                JavelinUiSettings.KEY_GRANULARITY,
                JavelinUiSettings.KEY_RANKING_STRATEGY
        );
        for (String key : otherKeys) {
            assertNotEquals(JavelinUiSettings.KEY_TIMEOUT_MINUTES, key);
        }
    }

    @Test
    void timeoutDefaultKeyTransformsCorrectly() {
        String defaultKey = JavelinUiSettings.defaultKeyFor(JavelinUiSettings.KEY_TIMEOUT_MINUTES);
        assertTrue(defaultKey.startsWith("javelin.defaults."));
        assertFalse(defaultKey.contains("javelin.ui."));
    }

    @Test
    void defaultBooleanSettingsAreTrue() {
        assertTrue(JavelinUiSettings.DEFAULT_GUTTER_ENABLED);
        assertTrue(JavelinUiSettings.DEFAULT_HIGHLIGHT_ENABLED);
        assertTrue(JavelinUiSettings.DEFAULT_STRIPE_ENABLED);
    }

    @Test
    void defaultKeyForTransformsPrefix() {
        assertEquals("javelin.defaults.algorithm",
                JavelinUiSettings.defaultKeyFor("javelin.ui.algorithm"));
    }

    @Test
    void defaultKeyForTransformsAllKeys() {
        List<String> uiKeys = List.of(
                JavelinUiSettings.KEY_GUTTER_ENABLED,
                JavelinUiSettings.KEY_HIGHLIGHT_ENABLED,
                JavelinUiSettings.KEY_STRIPE_ENABLED,
                JavelinUiSettings.KEY_ALGORITHM,
                JavelinUiSettings.KEY_MAX_THREADS,
                JavelinUiSettings.KEY_VISIBLE_BANDS,
                JavelinUiSettings.KEY_JVM_HOME,
                JavelinUiSettings.KEY_GRANULARITY,
                JavelinUiSettings.KEY_RANKING_STRATEGY,
                JavelinUiSettings.KEY_TIMEOUT_MINUTES
        );
        for (String uiKey : uiKeys) {
            String defaultKey = JavelinUiSettings.defaultKeyFor(uiKey);
            assertTrue(defaultKey.startsWith("javelin.defaults."),
                    "Default key should start with 'javelin.defaults.': " + defaultKey);
            assertFalse(defaultKey.contains("javelin.ui."),
                    "Default key should not contain 'javelin.ui.': " + defaultKey);
        }
    }

    @Test
    void defaultKeysAreDistinctFromUiKeys() {
        List<String> uiKeys = List.of(
                JavelinUiSettings.KEY_GUTTER_ENABLED,
                JavelinUiSettings.KEY_HIGHLIGHT_ENABLED,
                JavelinUiSettings.KEY_STRIPE_ENABLED,
                JavelinUiSettings.KEY_ALGORITHM,
                JavelinUiSettings.KEY_MAX_THREADS,
                JavelinUiSettings.KEY_VISIBLE_BANDS,
                JavelinUiSettings.KEY_JVM_HOME,
                JavelinUiSettings.KEY_GRANULARITY,
                JavelinUiSettings.KEY_RANKING_STRATEGY,
                JavelinUiSettings.KEY_TIMEOUT_MINUTES
        );
        for (String uiKey : uiKeys) {
            String defaultKey = JavelinUiSettings.defaultKeyFor(uiKey);
            assertNotEquals(uiKey, defaultKey,
                    "Default key must differ from UI key: " + uiKey);
        }
    }

    @Test
    void defaultKeysAreDistinctFromEachOther() {
        List<String> uiKeys = List.of(
                JavelinUiSettings.KEY_GUTTER_ENABLED,
                JavelinUiSettings.KEY_HIGHLIGHT_ENABLED,
                JavelinUiSettings.KEY_STRIPE_ENABLED,
                JavelinUiSettings.KEY_ALGORITHM,
                JavelinUiSettings.KEY_MAX_THREADS,
                JavelinUiSettings.KEY_VISIBLE_BANDS,
                JavelinUiSettings.KEY_JVM_HOME,
                JavelinUiSettings.KEY_GRANULARITY,
                JavelinUiSettings.KEY_RANKING_STRATEGY,
                JavelinUiSettings.KEY_TIMEOUT_MINUTES
        );
        List<String> defaultKeys = uiKeys.stream()
                .map(JavelinUiSettings::defaultKeyFor)
                .toList();
        long distinctCount = defaultKeys.stream().distinct().count();
        assertEquals(defaultKeys.size(), distinctCount, "All default keys must be unique");
    }

    @Test
    void defaultKeyPreservesSuffix() {
        String uiKey = JavelinUiSettings.KEY_ALGORITHM;
        String defaultKey = JavelinUiSettings.defaultKeyFor(uiKey);
        String uiSuffix = uiKey.substring("javelin.ui.".length());
        String defaultSuffix = defaultKey.substring("javelin.defaults.".length());
        assertEquals(uiSuffix, defaultSuffix, "Suffix must be preserved across keyspaces");
    }

    @Test
    void sentinelKeyIsNonBlank() {
        assertNotNull(JavelinUiSettings.KEY_DEFAULTS_INITIALIZED);
        assertFalse(JavelinUiSettings.KEY_DEFAULTS_INITIALIZED.isBlank());
        assertTrue(JavelinUiSettings.KEY_DEFAULTS_INITIALIZED.startsWith("javelin.defaults."));
    }
}

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
                JavelinUiSettings.KEY_RANKING_STRATEGY
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
                JavelinUiSettings.KEY_RANKING_STRATEGY
        );
        long distinctCount = allKeys.stream().distinct().count();
        assertEquals(allKeys.size(), distinctCount, "All setting keys must be unique");
    }
}

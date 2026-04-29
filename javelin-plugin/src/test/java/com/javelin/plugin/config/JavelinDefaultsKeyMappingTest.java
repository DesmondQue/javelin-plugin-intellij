package com.javelin.plugin.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavelinDefaultsKeyMappingTest {

    private static final List<String> ALL_UI_KEYS = List.of(
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

    @Test
    void allUiKeysProduceValidDefaultKeys() {
        for (String uiKey : ALL_UI_KEYS) {
            String defaultKey = JavelinUiSettings.defaultKeyFor(uiKey);
            assertNotNull(defaultKey, "Default key for " + uiKey + " should not be null");
            assertFalse(defaultKey.isBlank(), "Default key for " + uiKey + " should not be blank");
        }
    }

    @Test
    void defaultKeysDoNotOverlapWithUiKeys() {
        List<String> defaultKeys = ALL_UI_KEYS.stream()
                .map(JavelinUiSettings::defaultKeyFor)
                .toList();
        for (String defaultKey : defaultKeys) {
            assertFalse(ALL_UI_KEYS.contains(defaultKey),
                    "Default key must not collide with a UI key: " + defaultKey);
        }
    }

    @Test
    void defaultKeyMappingIsConsistent() {
        for (String uiKey : ALL_UI_KEYS) {
            String first = JavelinUiSettings.defaultKeyFor(uiKey);
            String second = JavelinUiSettings.defaultKeyFor(uiKey);
            assertEquals(first, second,
                    "defaultKeyFor must return the same result for the same input: " + uiKey);
        }
    }

    @Test
    void allNineSettingsAreMapped() {
        assertEquals(9, ALL_UI_KEYS.size(), "Expected exactly 9 UI setting keys");
        long distinctDefaultKeys = ALL_UI_KEYS.stream()
                .map(JavelinUiSettings::defaultKeyFor)
                .distinct()
                .count();
        assertEquals(9, distinctDefaultKeys, "Expected exactly 9 distinct default keys");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javelin.ui.gutter.enabled",
            "javelin.ui.highlight.enabled",
            "javelin.ui.stripe.enabled",
            "javelin.ui.algorithm",
            "javelin.ui.maxThreads",
            "javelin.ui.visibleBands",
            "javelin.ui.jvmHome",
            "javelin.ui.granularity",
            "javelin.ui.rankingStrategy"
    })
    void eachKeyTransformsCorrectly(String uiKey) {
        String defaultKey = JavelinUiSettings.defaultKeyFor(uiKey);
        String expectedPrefix = "javelin.defaults.";
        String suffix = uiKey.substring("javelin.ui.".length());
        assertEquals(expectedPrefix + suffix, defaultKey);
    }

    @Test
    void sentinelKeyIsInDefaultsNamespace() {
        assertTrue(JavelinUiSettings.KEY_DEFAULTS_INITIALIZED.startsWith("javelin.defaults."),
                "Sentinel key must be in the defaults namespace");
    }

    @Test
    void sentinelKeyIsNotASettingKey() {
        List<String> defaultKeys = ALL_UI_KEYS.stream()
                .map(JavelinUiSettings::defaultKeyFor)
                .toList();
        assertFalse(defaultKeys.contains(JavelinUiSettings.KEY_DEFAULTS_INITIALIZED),
                "Sentinel key must not collide with any setting's default key");
    }
}

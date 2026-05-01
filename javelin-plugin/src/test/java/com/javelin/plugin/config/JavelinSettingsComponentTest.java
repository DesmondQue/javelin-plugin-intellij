package com.javelin.plugin.config;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.javelin.plugin.ui.JavelinHighlightProvider.SuspicionBand;

import static org.junit.jupiter.api.Assertions.*;

class JavelinSettingsComponentTest {

    private JavelinSettingsComponent component;

    @BeforeEach
    void setUp() {
        component = new JavelinSettingsComponent();
    }

    @Test
    void panelIsNotNull() {
        assertNotNull(component.getPanel());
    }

    @Test
    void resetToDefaultsSetsAlgorithm() {
        component.setAlgorithm("ochiai-ms");
        component.resetToDefaults();
        assertEquals(JavelinUiSettings.DEFAULT_ALGORITHM, component.getAlgorithm());
    }

    @Test
    void resetToDefaultsSetsGranularity() {
        component.setGranularity("method");
        component.resetToDefaults();
        assertEquals(JavelinUiSettings.DEFAULT_GRANULARITY, component.getGranularity());
    }

    @Test
    void resetToDefaultsSetsRankingStrategy() {
        component.setRankingStrategy("average");
        component.resetToDefaults();
        assertEquals(JavelinUiSettings.DEFAULT_RANKING_STRATEGY, component.getRankingStrategy());
    }

    @Test
    void resetToDefaultsSetsJvmHome() {
        component.setJvmHome("/some/path");
        component.resetToDefaults();
        assertEquals(JavelinUiSettings.DEFAULT_JVM_HOME, component.getJvmHome());
    }

    @Test
    void resetToDefaultsEnablesHighlighting() {
        component.setHighlightEnabled(false);
        component.resetToDefaults();
        assertTrue(component.isHighlightEnabled());
    }

    @Test
    void resetToDefaultsEnablesGutter() {
        component.setGutterEnabled(false);
        component.resetToDefaults();
        assertTrue(component.isGutterEnabled());
    }

    @Test
    void resetToDefaultsEnablesStripe() {
        component.setStripeEnabled(false);
        component.resetToDefaults();
        assertTrue(component.isStripeEnabled());
    }

    @Test
    void resetToDefaultsEnablesAllBands() {
        component.setVisibleBands(EnumSet.of(SuspicionBand.RED));
        component.resetToDefaults();
        assertEquals(EnumSet.allOf(SuspicionBand.class), component.getVisibleBands());
    }

    @Test
    void getVisibleBandsFallsBackToAllWhenNoneSelected() {
        component.setVisibleBands(EnumSet.noneOf(SuspicionBand.class));
        Set<SuspicionBand> bands = component.getVisibleBands();
        assertEquals(EnumSet.allOf(SuspicionBand.class), bands);
    }

    @Test
    void setAndGetAlgorithm() {
        component.setAlgorithm("ochiai-ms");
        assertEquals("ochiai-ms", component.getAlgorithm());
    }

    @Test
    void setAndGetGranularity() {
        component.setGranularity("method");
        assertEquals("method", component.getGranularity());
    }

    @Test
    void setAndGetRankingStrategy() {
        component.setRankingStrategy("average");
        assertEquals("average", component.getRankingStrategy());
    }

    @Test
    void setAndGetHighlightEnabled() {
        component.setHighlightEnabled(false);
        assertFalse(component.isHighlightEnabled());
        component.setHighlightEnabled(true);
        assertTrue(component.isHighlightEnabled());
    }

    @Test
    void setAndGetGutterEnabled() {
        component.setGutterEnabled(false);
        assertFalse(component.isGutterEnabled());
    }

    @Test
    void setAndGetStripeEnabled() {
        component.setStripeEnabled(false);
        assertFalse(component.isStripeEnabled());
    }

    @Test
    void setAndGetVisibleBands() {
        EnumSet<SuspicionBand> subset = EnumSet.of(SuspicionBand.RED, SuspicionBand.ORANGE);
        component.setVisibleBands(subset);
        assertEquals(subset, component.getVisibleBands());
    }

    @Test
    void threadsClampsToValidRange() {
        component.setThreads(0);
        assertTrue(component.getThreads() >= 1);
    }

    @Test
    void setAndGetTimeoutMinutes() {
        component.setTimeoutMinutes(5);
        assertEquals(5, component.getTimeoutMinutes());
    }

    @Test
    void timeoutMinutesDefaultsToZero() {
        component.resetToDefaults();
        assertEquals(0, component.getTimeoutMinutes());
    }

    @Test
    void timeoutMinutesClampsNegativeToZero() {
        component.setTimeoutMinutes(-5);
        assertEquals(0, component.getTimeoutMinutes());
    }

    @Test
    void timeoutMinutesClampsAboveMax() {
        component.setTimeoutMinutes(200);
        assertEquals(120, component.getTimeoutMinutes());
    }

    @Test
    void resetToDefaultsSetsTimeout() {
        component.setTimeoutMinutes(10);
        component.resetToDefaults();
        assertEquals(JavelinUiSettings.DEFAULT_TIMEOUT_MINUTES, component.getTimeoutMinutes());
    }
}

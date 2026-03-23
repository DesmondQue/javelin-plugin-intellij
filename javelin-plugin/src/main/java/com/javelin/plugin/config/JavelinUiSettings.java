package com.javelin.plugin.config;

import java.util.EnumSet;
import java.util.Set;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.javelin.plugin.ui.JavelinHighlightProvider.SuspicionBand;

public final class JavelinUiSettings {

    public static final String KEY_GUTTER_ENABLED = "javelin.ui.gutter.enabled";
    public static final String KEY_HIGHLIGHT_ENABLED = "javelin.ui.highlight.enabled";
    public static final String KEY_STRIPE_ENABLED = "javelin.ui.stripe.enabled";
    public static final String KEY_ALGORITHM = "javelin.ui.algorithm";
    public static final String KEY_MAX_THREADS = "javelin.ui.maxThreads";
    public static final String KEY_VISIBLE_BANDS = "javelin.ui.visibleBands";

    private JavelinUiSettings() {
    }

    public static boolean isGutterEnabled(Project project) {
        return getProperties(project).getBoolean(KEY_GUTTER_ENABLED, true);
    }

    public static void setGutterEnabled(Project project, boolean enabled) {
        getProperties(project).setValue(KEY_GUTTER_ENABLED, enabled, true);
    }

    public static boolean isHighlightEnabled(Project project) {
        return getProperties(project).getBoolean(KEY_HIGHLIGHT_ENABLED, true);
    }

    public static void setHighlightEnabled(Project project, boolean enabled) {
        getProperties(project).setValue(KEY_HIGHLIGHT_ENABLED, enabled, true);
    }

    public static boolean isStripeEnabled(Project project) {
        return getProperties(project).getBoolean(KEY_STRIPE_ENABLED, true);
    }

    public static void setStripeEnabled(Project project, boolean enabled) {
        getProperties(project).setValue(KEY_STRIPE_ENABLED, enabled, true);
    }

    public static String getAlgorithm(Project project) {
        String algorithm = getProperties(project).getValue(KEY_ALGORITHM, "ochiai");
        if ("ochiai".equals(algorithm) || "ochiai-ms".equals(algorithm)) {
            return algorithm;
        }
        return "ochiai";
    }

    public static void setAlgorithm(Project project, String algorithm) {
        if ("ochiai".equals(algorithm) || "ochiai-ms".equals(algorithm)) {
            getProperties(project).setValue(KEY_ALGORITHM, algorithm, "ochiai");
        }
    }

    public static int getMaxThreads(Project project) {
        int max = Runtime.getRuntime().availableProcessors();
        int stored = getProperties(project).getInt(KEY_MAX_THREADS, max);
        return clampThreads(stored);
    }

    public static void setMaxThreads(Project project, int threads) {
        getProperties(project).setValue(KEY_MAX_THREADS, clampThreads(threads), Runtime.getRuntime().availableProcessors());
    }

    public static Set<SuspicionBand> getVisibleBands(Project project) {
        String raw = getProperties(project).getValue(KEY_VISIBLE_BANDS, "RED,ORANGE,YELLOW,GREEN");
        EnumSet<SuspicionBand> bands = EnumSet.noneOf(SuspicionBand.class);
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                bands.add(SuspicionBand.valueOf(trimmed));
            } catch (IllegalArgumentException ignored) {
                // Ignore unknown values to keep settings forward-compatible.
            }
        }
        if (bands.isEmpty()) {
            bands = EnumSet.allOf(SuspicionBand.class);
        }
        return bands;
    }

    public static void setVisibleBands(Project project, Set<SuspicionBand> visibleBands) {
        if (visibleBands == null || visibleBands.isEmpty()) {
            getProperties(project).setValue(KEY_VISIBLE_BANDS, "RED,ORANGE,YELLOW,GREEN", "RED,ORANGE,YELLOW,GREEN");
            return;
        }
        String serialized = String.join(",", visibleBands.stream().map(Enum::name).toList());
        getProperties(project).setValue(KEY_VISIBLE_BANDS, serialized, "RED,ORANGE,YELLOW,GREEN");
    }

    private static int clampThreads(int threads) {
        int max = Math.max(1, Runtime.getRuntime().availableProcessors());
        return Math.max(1, Math.min(threads, max));
    }

    private static PropertiesComponent getProperties(Project project) {
        return PropertiesComponent.getInstance(project);
    }
}
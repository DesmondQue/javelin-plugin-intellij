package com.javelin.plugin.config;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.javelin.plugin.ui.JavelinHighlightProvider.SuspicionBand;

public final class JavelinUiSettings {

    private static final Set<String> RESTORED_PROJECTS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static final String KEY_GUTTER_ENABLED = "javelin.ui.gutter.enabled";
    public static final String KEY_HIGHLIGHT_ENABLED = "javelin.ui.highlight.enabled";
    public static final String KEY_STRIPE_ENABLED = "javelin.ui.stripe.enabled";
    public static final String KEY_ALGORITHM = "javelin.ui.algorithm";
    public static final String KEY_MAX_THREADS = "javelin.ui.maxThreads";
    public static final String KEY_VISIBLE_BANDS = "javelin.ui.visibleBands";
    public static final String KEY_GRANULARITY = "javelin.ui.granularity";
    public static final String KEY_RANKING_STRATEGY = "javelin.ui.rankingStrategy";
    public static final String KEY_TIMEOUT_MINUTES = "javelin.ui.timeoutMinutes";
    public static final String KEY_BUILD_FIRST = "javelin.ui.buildFirst";

    public static final String DEFAULT_ALGORITHM = "ochiai";
    public static final String DEFAULT_GRANULARITY = "statement";
    public static final String DEFAULT_RANKING_STRATEGY = "dense";
    public static final String DEFAULT_VISIBLE_BANDS = "RED,ORANGE,YELLOW,GREEN";
    public static final int DEFAULT_TIMEOUT_MINUTES = 0;
    public static final boolean DEFAULT_GUTTER_ENABLED = true;
    public static final boolean DEFAULT_HIGHLIGHT_ENABLED = true;
    public static final boolean DEFAULT_STRIPE_ENABLED = true;
    public static final boolean DEFAULT_BUILD_FIRST = true;

    static final String KEY_DEFAULTS_INITIALIZED = "javelin.defaults.initialized";

    private JavelinUiSettings() {
    }

    public static String defaultKeyFor(String uiKey) {
        return uiKey.replace("javelin.ui.", "javelin.defaults.");
    }

    // --- Defaults getters (read from javelin.defaults.* keyspace) ---

    public static String getDefaultAlgorithm(Project project) {
        String value = getProperties(project).getValue(defaultKeyFor(KEY_ALGORITHM), DEFAULT_ALGORITHM);
        return "ochiai".equals(value) || "ochiai-ms".equals(value) ? value : DEFAULT_ALGORITHM;
    }

    public static void setDefaultAlgorithm(Project project, String algorithm) {
        if ("ochiai".equals(algorithm) || "ochiai-ms".equals(algorithm)) {
            getProperties(project).setValue(defaultKeyFor(KEY_ALGORITHM), algorithm, DEFAULT_ALGORITHM);
        }
    }

    public static String getDefaultGranularity(Project project) {
        String value = getProperties(project).getValue(defaultKeyFor(KEY_GRANULARITY), DEFAULT_GRANULARITY);
        return "method".equals(value) || "statement".equals(value) ? value : DEFAULT_GRANULARITY;
    }

    public static void setDefaultGranularity(Project project, String granularity) {
        if ("method".equals(granularity) || "statement".equals(granularity)) {
            getProperties(project).setValue(defaultKeyFor(KEY_GRANULARITY), granularity, DEFAULT_GRANULARITY);
        }
    }

    public static String getDefaultRankingStrategy(Project project) {
        String value = getProperties(project).getValue(defaultKeyFor(KEY_RANKING_STRATEGY), DEFAULT_RANKING_STRATEGY);
        return "average".equals(value) || "dense".equals(value) ? value : DEFAULT_RANKING_STRATEGY;
    }

    public static void setDefaultRankingStrategy(Project project, String rankingStrategy) {
        if ("average".equals(rankingStrategy) || "dense".equals(rankingStrategy)) {
            getProperties(project).setValue(defaultKeyFor(KEY_RANKING_STRATEGY), rankingStrategy, DEFAULT_RANKING_STRATEGY);
        }
    }

    public static int getDefaultMaxThreads(Project project) {
        int max = Runtime.getRuntime().availableProcessors();
        int stored = getProperties(project).getInt(defaultKeyFor(KEY_MAX_THREADS), max);
        return clampThreads(stored);
    }

    public static void setDefaultMaxThreads(Project project, int threads) {
        getProperties(project).setValue(defaultKeyFor(KEY_MAX_THREADS), clampThreads(threads), Runtime.getRuntime().availableProcessors());
    }

    public static int getDefaultTimeoutMinutes(Project project) {
        return Math.max(0, getProperties(project).getInt(defaultKeyFor(KEY_TIMEOUT_MINUTES), DEFAULT_TIMEOUT_MINUTES));
    }

    public static void setDefaultTimeoutMinutes(Project project, int minutes) {
        getProperties(project).setValue(defaultKeyFor(KEY_TIMEOUT_MINUTES), Math.max(0, minutes), DEFAULT_TIMEOUT_MINUTES);
    }

    public static boolean isDefaultBuildFirst(Project project) {
        return getProperties(project).getBoolean(defaultKeyFor(KEY_BUILD_FIRST), DEFAULT_BUILD_FIRST);
    }

    public static void setDefaultBuildFirst(Project project, boolean enabled) {
        getProperties(project).setValue(defaultKeyFor(KEY_BUILD_FIRST), enabled, DEFAULT_BUILD_FIRST);
    }

    public static boolean isDefaultHighlightEnabled(Project project) {
        return getProperties(project).getBoolean(defaultKeyFor(KEY_HIGHLIGHT_ENABLED), DEFAULT_HIGHLIGHT_ENABLED);
    }

    public static void setDefaultHighlightEnabled(Project project, boolean enabled) {
        getProperties(project).setValue(defaultKeyFor(KEY_HIGHLIGHT_ENABLED), enabled, DEFAULT_HIGHLIGHT_ENABLED);
    }

    public static boolean isDefaultGutterEnabled(Project project) {
        return getProperties(project).getBoolean(defaultKeyFor(KEY_GUTTER_ENABLED), DEFAULT_GUTTER_ENABLED);
    }

    public static void setDefaultGutterEnabled(Project project, boolean enabled) {
        getProperties(project).setValue(defaultKeyFor(KEY_GUTTER_ENABLED), enabled, DEFAULT_GUTTER_ENABLED);
    }

    public static boolean isDefaultStripeEnabled(Project project) {
        return getProperties(project).getBoolean(defaultKeyFor(KEY_STRIPE_ENABLED), DEFAULT_STRIPE_ENABLED);
    }

    public static void setDefaultStripeEnabled(Project project, boolean enabled) {
        getProperties(project).setValue(defaultKeyFor(KEY_STRIPE_ENABLED), enabled, DEFAULT_STRIPE_ENABLED);
    }

    public static Set<SuspicionBand> getDefaultVisibleBands(Project project) {
        String raw = getProperties(project).getValue(defaultKeyFor(KEY_VISIBLE_BANDS), DEFAULT_VISIBLE_BANDS);
        EnumSet<SuspicionBand> bands = EnumSet.noneOf(SuspicionBand.class);
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) continue;
            try {
                bands.add(SuspicionBand.valueOf(trimmed));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (bands.isEmpty()) {
            bands = EnumSet.allOf(SuspicionBand.class);
        }
        return bands;
    }

    public static void setDefaultVisibleBands(Project project, Set<SuspicionBand> visibleBands) {
        if (visibleBands == null || visibleBands.isEmpty()) {
            getProperties(project).setValue(defaultKeyFor(KEY_VISIBLE_BANDS), DEFAULT_VISIBLE_BANDS, DEFAULT_VISIBLE_BANDS);
            return;
        }
        String serialized = String.join(",", visibleBands.stream().map(Enum::name).toList());
        getProperties(project).setValue(defaultKeyFor(KEY_VISIBLE_BANDS), serialized, DEFAULT_VISIBLE_BANDS);
    }

    // --- Migration and restore ---

    public static boolean isDefaultsInitialized(Project project) {
        return getProperties(project).getBoolean(KEY_DEFAULTS_INITIALIZED, false);
    }

    public static void initializeDefaultsFromCurrent(Project project) {
        setDefaultAlgorithm(project, getAlgorithm(project));
        setDefaultGranularity(project, getGranularity(project));
        setDefaultRankingStrategy(project, getRankingStrategy(project));
        setDefaultMaxThreads(project, getMaxThreads(project));
        setDefaultTimeoutMinutes(project, getTimeoutMinutes(project));
        setDefaultBuildFirst(project, isBuildFirst(project));
        setDefaultHighlightEnabled(project, isHighlightEnabled(project));
        setDefaultGutterEnabled(project, isGutterEnabled(project));
        setDefaultStripeEnabled(project, isStripeEnabled(project));
        setDefaultVisibleBands(project, getVisibleBands(project));
        getProperties(project).setValue(KEY_DEFAULTS_INITIALIZED, true, false);
    }

    public static void restoreFromDefaults(Project project) {
        setAlgorithm(project, getDefaultAlgorithm(project));
        setGranularity(project, getDefaultGranularity(project));
        setRankingStrategy(project, getDefaultRankingStrategy(project));
        setMaxThreads(project, getDefaultMaxThreads(project));
        setTimeoutMinutes(project, getDefaultTimeoutMinutes(project));
        setBuildFirst(project, isDefaultBuildFirst(project));
        setHighlightEnabled(project, isDefaultHighlightEnabled(project));
        setGutterEnabled(project, isDefaultGutterEnabled(project));
        setStripeEnabled(project, isDefaultStripeEnabled(project));
        setVisibleBands(project, getDefaultVisibleBands(project));
    }

    public static void ensureDefaultsRestored(Project project) {
        String projectPath = project.getBasePath();
        if (projectPath == null || !RESTORED_PROJECTS.add(projectPath)) {
            return;
        }
        if (!isDefaultsInitialized(project)) {
            initializeDefaultsFromCurrent(project);
        }
        restoreFromDefaults(project);
    }

    public static boolean isGutterEnabled(Project project) {
        return getProperties(project).getBoolean(KEY_GUTTER_ENABLED, DEFAULT_GUTTER_ENABLED);
    }

    public static void setGutterEnabled(Project project, boolean enabled) {
        getProperties(project).setValue(KEY_GUTTER_ENABLED, enabled, DEFAULT_GUTTER_ENABLED);
    }

    public static boolean isHighlightEnabled(Project project) {
        return getProperties(project).getBoolean(KEY_HIGHLIGHT_ENABLED, DEFAULT_HIGHLIGHT_ENABLED);
    }

    public static void setHighlightEnabled(Project project, boolean enabled) {
        getProperties(project).setValue(KEY_HIGHLIGHT_ENABLED, enabled, DEFAULT_HIGHLIGHT_ENABLED);
    }

    public static boolean isStripeEnabled(Project project) {
        return getProperties(project).getBoolean(KEY_STRIPE_ENABLED, DEFAULT_STRIPE_ENABLED);
    }

    public static void setStripeEnabled(Project project, boolean enabled) {
        getProperties(project).setValue(KEY_STRIPE_ENABLED, enabled, DEFAULT_STRIPE_ENABLED);
    }

    public static String getAlgorithm(Project project) {
        String algorithm = getProperties(project).getValue(KEY_ALGORITHM, DEFAULT_ALGORITHM);
        if ("ochiai".equals(algorithm) || "ochiai-ms".equals(algorithm)) {
            return algorithm;
        }
        return DEFAULT_ALGORITHM;
    }

    public static void setAlgorithm(Project project, String algorithm) {
        if ("ochiai".equals(algorithm) || "ochiai-ms".equals(algorithm)) {
            getProperties(project).setValue(KEY_ALGORITHM, algorithm, DEFAULT_ALGORITHM);
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
        String raw = getProperties(project).getValue(KEY_VISIBLE_BANDS, DEFAULT_VISIBLE_BANDS);
        EnumSet<SuspicionBand> bands = EnumSet.noneOf(SuspicionBand.class);
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                bands.add(SuspicionBand.valueOf(trimmed));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (bands.isEmpty()) {
            bands = EnumSet.allOf(SuspicionBand.class);
        }
        return bands;
    }

    public static void setVisibleBands(Project project, Set<SuspicionBand> visibleBands) {
        if (visibleBands == null || visibleBands.isEmpty()) {
            getProperties(project).setValue(KEY_VISIBLE_BANDS, DEFAULT_VISIBLE_BANDS, DEFAULT_VISIBLE_BANDS);
            return;
        }
        String serialized = String.join(",", visibleBands.stream().map(Enum::name).toList());
        getProperties(project).setValue(KEY_VISIBLE_BANDS, serialized, DEFAULT_VISIBLE_BANDS);
    }

    public static String getGranularity(Project project) {
        String value = getProperties(project).getValue(KEY_GRANULARITY, DEFAULT_GRANULARITY);
        return "method".equals(value) || "statement".equals(value) ? value : DEFAULT_GRANULARITY;
    }

    public static void setGranularity(Project project, String granularity) {
        if ("method".equals(granularity) || "statement".equals(granularity)) {
            getProperties(project).setValue(KEY_GRANULARITY, granularity, DEFAULT_GRANULARITY);
        }
    }

    public static String getRankingStrategy(Project project) {
        String value = getProperties(project).getValue(KEY_RANKING_STRATEGY, DEFAULT_RANKING_STRATEGY);
        return "average".equals(value) || "dense".equals(value) ? value : DEFAULT_RANKING_STRATEGY;
    }

    public static void setRankingStrategy(Project project, String rankingStrategy) {
        if ("average".equals(rankingStrategy) || "dense".equals(rankingStrategy)) {
            getProperties(project).setValue(KEY_RANKING_STRATEGY, rankingStrategy, DEFAULT_RANKING_STRATEGY);
        }
    }

    public static int getTimeoutMinutes(Project project) {
        return Math.max(0, getProperties(project).getInt(KEY_TIMEOUT_MINUTES, DEFAULT_TIMEOUT_MINUTES));
    }

    public static void setTimeoutMinutes(Project project, int minutes) {
        getProperties(project).setValue(KEY_TIMEOUT_MINUTES, Math.max(0, minutes), DEFAULT_TIMEOUT_MINUTES);
    }

    public static boolean isBuildFirst(Project project) {
        return getProperties(project).getBoolean(KEY_BUILD_FIRST, DEFAULT_BUILD_FIRST);
    }

    public static void setBuildFirst(Project project, boolean enabled) {
        getProperties(project).setValue(KEY_BUILD_FIRST, enabled, DEFAULT_BUILD_FIRST);
    }

    private static int clampThreads(int threads) {
        int max = Math.max(1, Runtime.getRuntime().availableProcessors());
        return Math.max(1, Math.min(threads, max));
    }

    private static PropertiesComponent getProperties(Project project) {
        return PropertiesComponent.getInstance(project);
    }
}

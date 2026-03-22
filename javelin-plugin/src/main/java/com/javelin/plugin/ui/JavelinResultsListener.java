package com.javelin.plugin.ui;

import com.intellij.util.messages.Topic;
import com.javelin.plugin.model.FaultLocalizationResult;

import java.util.List;

public interface JavelinResultsListener {
    Topic<JavelinResultsListener> TOPIC = Topic.create("javelin-results", JavelinResultsListener.class);

    void resultsUpdated(List<FaultLocalizationResult> results);
}

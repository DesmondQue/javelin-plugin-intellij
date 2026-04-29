package com.javelin.plugin.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JavelinSettingsConfigurableTest {

    @Test
    void displayNameIsJavelin() {
        JavelinSettingsConfigurable configurable = new JavelinSettingsConfigurable(null);
        assertEquals("Javelin", configurable.getDisplayName());
    }

    @Test
    void displayNameIsNonBlank() {
        JavelinSettingsConfigurable configurable = new JavelinSettingsConfigurable(null);
        assertNotNull(configurable.getDisplayName());
        assertFalse(configurable.getDisplayName().isBlank());
    }

    @Test
    void notModifiedWhenNoComponentCreated() {
        JavelinSettingsConfigurable configurable = new JavelinSettingsConfigurable(null);
        assertFalse(configurable.isModified());
    }

    @Test
    void disposeNullsComponent() {
        JavelinSettingsConfigurable configurable = new JavelinSettingsConfigurable(null);
        configurable.disposeUIResources();
        assertFalse(configurable.isModified());
    }
}

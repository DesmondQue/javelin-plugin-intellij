package com.javelin.plugin.config;

import com.intellij.openapi.startup.StartupActivity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JavelinDefaultsRestorerTest {

    @Test
    void restorerIsInstantiable() {
        JavelinDefaultsRestorer restorer = new JavelinDefaultsRestorer();
        assertNotNull(restorer);
    }

    @Test
    void restorerImplementsStartupActivity() {
        JavelinDefaultsRestorer restorer = new JavelinDefaultsRestorer();
        assertInstanceOf(StartupActivity.Background.class, restorer);
    }
}

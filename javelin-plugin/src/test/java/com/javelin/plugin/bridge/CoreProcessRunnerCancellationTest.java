package com.javelin.plugin.bridge;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.NlsContexts;

import static org.junit.jupiter.api.Assertions.*;

class CoreProcessRunnerCancellationTest {

    private static ProgressIndicator cancelAfter(long delayMs) {
        StubProgressIndicator indicator = new StubProgressIndicator();
        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                indicator.markCanceled();
            }
        }, delayMs);
        return indicator;
    }

    private static ProgressIndicator neverCancel() {
        return new StubProgressIndicator();
    }

    private static final class StubProgressIndicator implements ProgressIndicator {
        private final AtomicBoolean canceled = new AtomicBoolean(false);

        void markCanceled() { canceled.set(true); }

        @Override public boolean isCanceled() { return canceled.get(); }
        @Override public void cancel() { canceled.set(true); }
        @Override public void start() {}
        @Override public void stop() {}
        @Override public boolean isRunning() { return true; }
        @Override public void setText(@NlsContexts.ProgressText String text) {}
        @Override public String getText() { return ""; }
        @Override public void setText2(@NlsContexts.ProgressDetails String text) {}
        @Override public String getText2() { return ""; }
        @Override public double getFraction() { return 0; }
        @Override public void setFraction(double fraction) {}
        @Override public void pushState() {}
        @Override public void popState() {}
        @Override public boolean isModal() { return false; }
        @Override public @org.jetbrains.annotations.NotNull com.intellij.openapi.application.ModalityState getModalityState() {
            return com.intellij.openapi.application.ModalityState.nonModal();
        }
        @Override public void setModalityProgress(ProgressIndicator modalityProgress) {}
        @Override public boolean isIndeterminate() { return true; }
        @Override public void setIndeterminate(boolean indeterminate) {}
        @Override public void checkCanceled() { if (canceled.get()) throw new ProcessCanceledException(); }
        @Override public boolean isPopupWasShown() { return false; }
        @Override public boolean isShowing() { return false; }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void cancelStopsLongRunningProcess() {
        CoreProcessRunner runner = new CoreProcessRunner();

        long start = System.currentTimeMillis();
        assertThrows(ProcessCanceledException.class, () -> {
            runner.runRaw(
                    buildPingCommand(),
                    cancelAfter(1500),
                    0L
            );
        });
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 10_000, "Should cancel well before the 60s process completes, took " + elapsed + "ms");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void timeoutStopsLongRunningProcess() {
        CoreProcessRunner runner = new CoreProcessRunner();

        long start = System.currentTimeMillis();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            runner.runRaw(
                    buildPingCommand(),
                    null,
                    2000L
            );
        });
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 10_000, "Should timeout well before the 60s process completes, took " + elapsed + "ms");
        assertTrue(ex.getMessage().contains("timed out"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void zeroTimeoutMeansNoLimit() {
        CoreProcessRunner runner = new CoreProcessRunner();

        CoreProcessResult result = runner.runRaw(
                buildQuickCommand(),
                neverCancel(),
                0L
        );
        assertEquals(0, result.exitCode());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void cancelTakesPriorityOverTimeout() {
        CoreProcessRunner runner = new CoreProcessRunner();

        long start = System.currentTimeMillis();
        assertThrows(ProcessCanceledException.class, () -> {
            runner.runRaw(
                    buildPingCommand(),
                    cancelAfter(1500),
                    30_000L
            );
        });
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 5_000, "Cancel at 1.5s should win over 30s timeout, took " + elapsed + "ms");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void processCompletesNormallyWithIndicator() {
        CoreProcessRunner runner = new CoreProcessRunner();

        CoreProcessResult result = runner.runRaw(
                buildQuickCommand(),
                neverCancel(),
                60_000L
        );
        assertEquals(0, result.exitCode());
        assertFalse(result.stdout().isBlank());
    }

    private static String[] buildPingCommand() {
        return new String[]{"ping", "-n", "60", "127.0.0.1"};
    }

    private static String[] buildQuickCommand() {
        return new String[]{"cmd", "/c", "echo hello"};
    }
}

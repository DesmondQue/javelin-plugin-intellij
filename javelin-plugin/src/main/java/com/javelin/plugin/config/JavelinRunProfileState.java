package com.javelin.plugin.config;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.NopProcessHandler;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.javelin.plugin.model.FaultLocalizationResult;
import com.javelin.plugin.service.JavelinService;

public final class JavelinRunProfileState implements RunProfileState {

    private final Project project;
    private final JavelinRunConfiguration configuration;

    public JavelinRunProfileState(Project project, JavelinRunConfiguration configuration) {
        this.project = project;
        this.configuration = configuration;
    }

    @Override
    public ExecutionResult execute(Executor executor, ProgramRunner<?> runner) throws ExecutionException {
        ConsoleView console = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
        NopProcessHandler processHandler = new NopProcessHandler();
        console.attachToProcess(processHandler);
        processHandler.startNotify();

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                console.print("Building project..." + System.lineSeparator(), ConsoleViewContentType.NORMAL_OUTPUT);
                // Trigger build and wait for it on the pooled thread via a latch
                java.util.concurrent.CountDownLatch buildLatch = new java.util.concurrent.CountDownLatch(1);
                boolean[] buildOk = {false};
                ApplicationManager.getApplication().invokeLater(() ->
                    CompilerManager.getInstance(project).make((aborted, errors, warnings, ctx) -> {
                        buildOk[0] = !aborted && errors == 0;
                        buildLatch.countDown();
                    })
                );
                buildLatch.await();
                if (!buildOk[0]) {
                    console.print("ERROR: Build failed. Fix compilation errors before running Javelin." + System.lineSeparator(), ConsoleViewContentType.ERROR_OUTPUT);
                    return;
                }
                console.print("Running Javelin analysis..." + System.lineSeparator(), ConsoleViewContentType.NORMAL_OUTPUT);
                JavelinService service = project.getService(JavelinService.class);
                List<FaultLocalizationResult> results = service.runAnalysis(new JavelinService.RunRequest(
                        Path.of(configuration.getTargetPath()),
                        Path.of(configuration.getTestPath()),
                        configuration.getAlgorithm(),
                        resolveModuleClasspath(),
                        configuration.getThreads(),
                        configuration.getOutputPath().isBlank() ? null : Path.of(configuration.getOutputPath())
                ));
                console.print("Completed. Results: " + results.size() + System.lineSeparator(), ConsoleViewContentType.SYSTEM_OUTPUT);
            } catch (Exception ex) {
                String message = ex.getMessage() == null ? "Run failed." : ex.getMessage();
                console.print("ERROR: " + message + System.lineSeparator(), ConsoleViewContentType.ERROR_OUTPUT);
            } finally {
                processHandler.destroyProcess();
            }
        });

        return new DefaultExecutionResult(console, processHandler);
    }

    private String resolveModuleClasspath() {
        List<String> entries = new ArrayList<>();
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            entries.addAll(OrderEnumerator.orderEntries(module)
                    .recursively()
                    .withoutSdk()
                    .classes()
                    .getPathsList()
                    .getPathList());
        }
        entries.removeIf(String::isBlank);
        return String.join(File.pathSeparator, entries);
    }
}

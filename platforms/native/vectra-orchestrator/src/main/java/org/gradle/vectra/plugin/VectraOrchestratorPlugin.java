package org.gradle.vectra.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.vectra.plugin.tasks.AbstractVectraTask;
import org.gradle.vectra.plugin.tasks.VectraInitTask;
import org.gradle.vectra.plugin.tasks.VectraReportTask;
import org.gradle.vectra.plugin.tasks.VectraStepTask;

public abstract class VectraOrchestratorPlugin implements Plugin<Project> {

    public static final String EXTENSION_NAME = "vectraOrchestrator";
    public static final String SERVICE_NAME = "vectraRuntimeService";

    @Override
    public void apply(Project project) {
        VectraOrchestratorExtension extension = project.getExtensions().create(
            EXTENSION_NAME,
            VectraOrchestratorExtension.class
        );

        Provider<VectraRuntimeService> runtimeService = project.getGradle().getSharedServices().registerIfAbsent(
            SERVICE_NAME,
            VectraRuntimeService.class,
            spec -> spec.getMaxParallelUsages().set(1)
        );

        project.getTasks().register("vectraInit", VectraInitTask.class, task -> configureTask(task, extension, runtimeService));
        project.getTasks().register("vectraStep", VectraStepTask.class, task -> configureTask(task, extension, runtimeService));
        project.getTasks().register("vectraReport", VectraReportTask.class, task -> configureTask(task, extension, runtimeService));
    }

    private static void configureTask(
        AbstractVectraTask task,
        VectraOrchestratorExtension extension,
        Provider<VectraRuntimeService> runtimeService
    ) {
        task.getEngineEnabled().convention(extension.getEnabled());
        task.getDeterministicMode().convention(extension.getDeterministic());
        task.getBackend().convention(extension.getBackend());
        task.configureRuntime(runtimeService);
    }
}

package org.gradle.vectra.plugin.tasks;

import org.gradle.api.tasks.TaskAction;
import org.gradle.vectra.plugin.VectraBackend;

public abstract class VectraReportTask extends AbstractVectraTask {

    @TaskAction
    public void report() {
        VectraBackend backend = resolveBackend();
        boolean noop = !getEngineEnabled().get() || getRuntimeService().get().isNoopMode();
        getLogger().lifecycle(
            "vectraReport: backend={} deterministic={} noop={} initialized={} steps={}",
            backend.name().toLowerCase(),
            getDeterministicMode().get(),
            noop,
            getRuntimeService().get().isInitialized(),
            getRuntimeService().get().getSteps()
        );
    }
}

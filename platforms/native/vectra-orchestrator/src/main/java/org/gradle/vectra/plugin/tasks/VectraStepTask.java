package org.gradle.vectra.plugin.tasks;

import org.gradle.api.tasks.TaskAction;
import org.gradle.vectra.plugin.VectraBackend;

public abstract class VectraStepTask extends AbstractVectraTask {

    @TaskAction
    public void step() {
        VectraBackend backend = resolveBackend();
        if (!getEngineEnabled().get() || getRuntimeService().get().isNoopMode()) {
            getLogger().lifecycle("vectraStep: noop mode active (backend={}, deterministic={}).", getBackend().get(), getDeterministicMode().get());
            return;
        }

        if (!getRuntimeService().get().isInitialized()) {
            getLogger().warn("vectraStep: runtime was not initialized via vectraInit; continuing with lazy init semantics.");
            getRuntimeService().get().markInitialized();
        }

        int step = getRuntimeService().get().incrementStep();
        getLogger().lifecycle("vectraStep: backend={} deterministic={} step={}", backend.name().toLowerCase(), getDeterministicMode().get(), step);
    }
}

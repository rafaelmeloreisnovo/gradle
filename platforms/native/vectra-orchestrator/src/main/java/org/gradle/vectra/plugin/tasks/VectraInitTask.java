package org.gradle.vectra.plugin.tasks;

import org.gradle.api.tasks.TaskAction;
import org.gradle.vectra.plugin.VectraBackend;

public abstract class VectraInitTask extends AbstractVectraTask {

    @TaskAction
    public void initialize() {
        VectraBackend backend = resolveBackend();
        if (!getEngineEnabled().get() || getRuntimeService().get().isNoopMode()) {
            getLogger().lifecycle("vectraInit: noop mode active (backend={}, deterministic={}).", getBackend().get(), getDeterministicMode().get());
            return;
        }

        getRuntimeService().get().markInitialized();
        getLogger().lifecycle("vectraInit: initialized backend={} deterministic={}", backend.name().toLowerCase(), getDeterministicMode().get());
    }
}

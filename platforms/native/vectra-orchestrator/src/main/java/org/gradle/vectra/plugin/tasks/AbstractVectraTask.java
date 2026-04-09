package org.gradle.vectra.plugin.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.vectra.plugin.VectraBackend;
import org.gradle.vectra.plugin.VectraRuntimeService;

public abstract class AbstractVectraTask extends DefaultTask {

    @Input
    public abstract Property<Boolean> getEngineEnabled();

    @Input
    public abstract Property<Boolean> getDeterministicMode();

    @Input
    public abstract Property<String> getBackend();

    @Internal
    public abstract Property<VectraRuntimeService> getRuntimeService();

    public void configureRuntime(Provider<VectraRuntimeService> serviceProvider) {
        getRuntimeService().set(serviceProvider);
        usesService(serviceProvider);
    }

    protected VectraBackend resolveBackend() {
        return getRuntimeService().get().resolveBackend(getBackend().get(), getEngineEnabled().get());
    }
}

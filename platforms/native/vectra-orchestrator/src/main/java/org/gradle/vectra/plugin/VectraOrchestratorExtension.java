package org.gradle.vectra.plugin;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class VectraOrchestratorExtension {
    @Inject
    public VectraOrchestratorExtension(ObjectFactory objects) {
        getEnabled().convention(true);
        getDeterministic().convention(true);
        getBackend().convention(VectraBackend.JAVA.name().toLowerCase());
    }

    public abstract Property<Boolean> getEnabled();

    public abstract Property<Boolean> getDeterministic();

    public abstract Property<String> getBackend();
}

package org.gradle.vectra.plugin;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class VectraRuntimeService implements BuildService<BuildServiceParameters.None>, AutoCloseable {
    private static final Logger LOGGER = Logging.getLogger(VectraRuntimeService.class);

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean noopMode = new AtomicBoolean(false);
    private final AtomicInteger steps = new AtomicInteger(0);

    public VectraBackend resolveBackend(String configuredBackend, boolean engineEnabled) {
        if (!engineEnabled) {
            noopMode.set(true);
            return VectraBackend.JAVA;
        }

        VectraBackend backend = VectraBackend.parse(configuredBackend);
        if (backend == VectraBackend.JAVA) {
            noopMode.set(false);
            return backend;
        }

        if (!isNativeBackendAvailable(backend)) {
            noopMode.set(true);
            LOGGER.warn("Vectra backend '{}' is unavailable. Falling back to noop mode.", backend.name().toLowerCase(Locale.ROOT));
            return backend;
        }

        noopMode.set(false);
        return backend;
    }

    public void markInitialized() {
        initialized.set(true);
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public int incrementStep() {
        return steps.incrementAndGet();
    }

    public int getSteps() {
        return steps.get();
    }

    public boolean isNoopMode() {
        return noopMode.get();
    }

    private boolean isNativeBackendAvailable(VectraBackend backend) {
        String override = System.getProperty("org.gradle.vectra.native.available");
        if (override != null) {
            return Boolean.parseBoolean(override);
        }
        return false;
    }

    @Override
    public void close() {
        LOGGER.lifecycle(
            "Vectra runtime closed (initialized: {}, steps: {}, noop: {}).",
            initialized.get(),
            steps.get(),
            noopMode.get()
        );
    }
}

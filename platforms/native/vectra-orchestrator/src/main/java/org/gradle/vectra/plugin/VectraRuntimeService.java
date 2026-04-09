package org.gradle.vectra.plugin;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.vectra.runtime.VectraNativeLoadException;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class VectraRuntimeService implements BuildService<BuildServiceParameters.None>, AutoCloseable {
    private static final Logger LOGGER = Logging.getLogger(VectraRuntimeService.class);

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean noopMode = new AtomicBoolean(false);
    private final AtomicInteger steps = new AtomicInteger(0);

    public VectraBackend resolveBackend(
        String configuredBackend,
        boolean engineEnabled,
        String configuredAssemblerTool,
        String configuredCCompilerTool,
        String toolchainDirectory
    ) {
        if (!engineEnabled) {
            noopMode.set(true);
            return VectraBackend.JAVA;
        }

        VectraBackend requestedBackend;
        try {
            requestedBackend = VectraBackend.parse(configuredBackend);
        } catch (IllegalArgumentException ex) {
            throw new VectraNativeLoadException("Invalid Vectra backend configuration: " + configuredBackend, ex);
        }

        VectraBackend selectedBackend = selectBestAvailableBackend(requestedBackend, configuredAssemblerTool, configuredCCompilerTool, toolchainDirectory);
        noopMode.set(false);
        return selectedBackend;
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

    private VectraBackend selectBestAvailableBackend(
        VectraBackend requestedBackend,
        String configuredAssemblerTool,
        String configuredCCompilerTool,
        String toolchainDirectory
    ) {
        switch (requestedBackend) {
            case ASM:
                if (isNativeBackendAvailable(VectraBackend.ASM, configuredAssemblerTool, configuredCCompilerTool, toolchainDirectory)) {
                    return VectraBackend.ASM;
                }
                LOGGER.warn("Vectra backend '{}' is unavailable. Falling back to c backend.", requestedBackend.name().toLowerCase(Locale.ROOT));
                if (isNativeBackendAvailable(VectraBackend.C, configuredAssemblerTool, configuredCCompilerTool, toolchainDirectory)) {
                    return VectraBackend.C;
                }
                LOGGER.warn("Vectra backend 'c' is unavailable. Falling back to java backend.");
                return VectraBackend.JAVA;
            case C:
                if (isNativeBackendAvailable(VectraBackend.C, configuredAssemblerTool, configuredCCompilerTool, toolchainDirectory)) {
                    return VectraBackend.C;
                }
                LOGGER.warn("Vectra backend 'c' is unavailable. Falling back to java backend.");
                return VectraBackend.JAVA;
            case JAVA:
                return VectraBackend.JAVA;
            default:
                throw new VectraNativeLoadException("Unsupported backend state encountered: " + requestedBackend);
        }
    }

    private boolean isNativeBackendAvailable(
        VectraBackend backend,
        String configuredAssemblerTool,
        String configuredCCompilerTool,
        String toolchainDirectory
    ) {
        if (backend == VectraBackend.ASM && configuredAssemblerTool != null && !configuredAssemblerTool.isBlank()) {
            return true;
        }
        if (backend == VectraBackend.C && configuredCCompilerTool != null && !configuredCCompilerTool.isBlank()) {
            return true;
        }

        if (toolchainDirectory != null && !toolchainDirectory.isBlank()) {
            File binDir = new File(toolchainDirectory);
            if (backend == VectraBackend.ASM && hasTool(binDir, "as", "clang", "ml64")) {
                return true;
            }
            if (backend == VectraBackend.C && hasTool(binDir, "cc", "clang", "gcc", "cl")) {
                return true;
            }
        }

        String override = System.getProperty("org.gradle.vectra.native.available");
        if (override != null) {
            return Boolean.parseBoolean(override);
        }
        return false;
    }

    private boolean hasTool(File dir, String... commandNames) {
        for (String commandName : commandNames) {
            File unix = new File(dir, commandName);
            File windows = new File(dir, commandName + ".exe");
            if (unix.isFile() || windows.isFile()) {
                return true;
            }
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

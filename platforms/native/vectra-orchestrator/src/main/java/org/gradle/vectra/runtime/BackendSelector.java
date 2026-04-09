package org.gradle.vectra.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Maps capabilities to backend selection policies.
 * Priority order: native assembly > native C > pure Java.
 */
public class BackendSelector {


    public SelectionDecision selectWithFallback(CapabilityReport capabilityReport, BackendLoader backendLoader, Consumer<String> warnLogger) {
        SelectionDecision baseDecision = select(capabilityReport);
        if (baseDecision.getBackend() == Backend.JAVA_PURE) {
            return baseDecision;
        }

        if (baseDecision.getBackend() == Backend.NATIVE_ASM) {
            try {
                backendLoader.load(Backend.NATIVE_ASM);
                return baseDecision;
            } catch (VectraNativeLoadException ex) {
                warnLogger.accept("Failed to load ASM backend: " + ex.getMessage() + ". Falling back to C backend.");
                if (supportsNativeC(capabilityReport)) {
                    try {
                        backendLoader.load(Backend.NATIVE_C);
                        return new SelectionDecision(Backend.NATIVE_C, List.of("Native C backend selected after ASM load failure."));
                    } catch (VectraNativeLoadException cEx) {
                        warnLogger.accept("Failed to load C backend: " + cEx.getMessage() + ". Falling back to Java backend.");
                        return new SelectionDecision(Backend.JAVA_PURE, List.of("Pure Java backend selected after native load failures."));
                    }
                }
                return new SelectionDecision(Backend.JAVA_PURE, List.of("Pure Java backend selected after ASM load failure."));
            }
        }

        if (baseDecision.getBackend() == Backend.NATIVE_C) {
            try {
                backendLoader.load(Backend.NATIVE_C);
                return baseDecision;
            } catch (VectraNativeLoadException ex) {
                warnLogger.accept("Failed to load C backend: " + ex.getMessage() + ". Falling back to Java backend.");
                return new SelectionDecision(Backend.JAVA_PURE, List.of("Pure Java backend selected after C load failure."));
            }
        }

        return baseDecision;
    }

    public SelectionDecision select(CapabilityReport capabilityReport) {
        List<String> reasons = new ArrayList<>();

        if (supportsNativeAssembly(capabilityReport)) {
            reasons.add("Native assembly backend selected: supported platform, architecture, SIMD and assembler toolchain.");
            return new SelectionDecision(Backend.NATIVE_ASM, reasons);
        }

        if (capabilityReport.getArchitecture() == CapabilityReport.Architecture.AARCH64) {
            reasons.add("Native assembly backend skipped: no aarch64 assembly implementation is currently available.");
        } else {
            reasons.add("Native assembly backend skipped due to missing policy requirements.");
        }

        if (supportsNativeC(capabilityReport)) {
            reasons.add("Native C backend selected: C toolchain available on supported platform/architecture.");
            return new SelectionDecision(Backend.NATIVE_C, reasons);
        }

        reasons.add("Native C backend skipped due to missing policy requirements.");
        reasons.add("Falling back to pure Java backend.");
        return new SelectionDecision(Backend.JAVA_PURE, reasons);
    }

    private boolean supportsNativeAssembly(CapabilityReport capabilityReport) {
        return isSupportedPlatform(capabilityReport)
            && hasAssemblyImplementation(capabilityReport)
            && !capabilityReport.getSimdInstructions().isEmpty()
            && capabilityReport.getToolchainAvailability().isAssemblerAvailable();
    }


    private boolean hasAssemblyImplementation(CapabilityReport capabilityReport) {
        return capabilityReport.getArchitecture() == CapabilityReport.Architecture.X86_64;
    }

    private boolean supportsNativeC(CapabilityReport capabilityReport) {
        return isSupportedPlatform(capabilityReport)
            && isSupportedArchitecture(capabilityReport)
            && capabilityReport.getToolchainAvailability().isCCompilerAvailable();
    }

    private boolean isSupportedPlatform(CapabilityReport capabilityReport) {
        CapabilityReport.OperatingSystem os = capabilityReport.getOperatingSystem();
        return os == CapabilityReport.OperatingSystem.LINUX
            || os == CapabilityReport.OperatingSystem.MACOS
            || os == CapabilityReport.OperatingSystem.WINDOWS;
    }

    private boolean isSupportedArchitecture(CapabilityReport capabilityReport) {
        CapabilityReport.Architecture architecture = capabilityReport.getArchitecture();
        return architecture == CapabilityReport.Architecture.X86_64
            || architecture == CapabilityReport.Architecture.AARCH64;
    }

    @FunctionalInterface
    public interface BackendLoader {
        void load(Backend backend);
    }

    public enum Backend {
        NATIVE_ASM,
        NATIVE_C,
        JAVA_PURE
    }

    public static class SelectionDecision {

        private final Backend backend;
        private final List<String> reasons;

        public SelectionDecision(Backend backend, List<String> reasons) {
            this.backend = backend;
            this.reasons = List.copyOf(reasons);
        }

        public Backend getBackend() {
            return backend;
        }

        public List<String> getReasons() {
            return reasons;
        }
    }
}

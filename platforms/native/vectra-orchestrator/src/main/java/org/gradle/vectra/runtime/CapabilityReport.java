package org.gradle.vectra.runtime;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable capability report describing host characteristics used in backend selection.
 */
public class CapabilityReport {

    private final OperatingSystem operatingSystem;
    private final Architecture architecture;
    private final Set<SimdInstruction> simdInstructions;
    private final ToolchainAvailability toolchainAvailability;

    public CapabilityReport(
        OperatingSystem operatingSystem,
        Architecture architecture,
        Set<SimdInstruction> simdInstructions,
        ToolchainAvailability toolchainAvailability
    ) {
        this.operatingSystem = operatingSystem;
        this.architecture = architecture;
        this.simdInstructions = simdInstructions.isEmpty()
            ? Collections.emptySet()
            : Collections.unmodifiableSet(EnumSet.copyOf(simdInstructions));
        this.toolchainAvailability = toolchainAvailability;
    }

    public OperatingSystem getOperatingSystem() {
        return operatingSystem;
    }

    public Architecture getArchitecture() {
        return architecture;
    }

    public Set<SimdInstruction> getSimdInstructions() {
        return simdInstructions;
    }

    public ToolchainAvailability getToolchainAvailability() {
        return toolchainAvailability;
    }

    public enum OperatingSystem {
        LINUX,
        MACOS,
        WINDOWS,
        OTHER
    }

    public enum Architecture {
        X86_64,
        AARCH64,
        OTHER
    }

    public enum SimdInstruction {
        SSE2,
        AVX2,
        NEON
    }

    public static class ToolchainAvailability {

        private final boolean assemblerAvailable;
        private final boolean cCompilerAvailable;

        public ToolchainAvailability(boolean assemblerAvailable, boolean cCompilerAvailable) {
            this.assemblerAvailable = assemblerAvailable;
            this.cCompilerAvailable = cCompilerAvailable;
        }

        public boolean isAssemblerAvailable() {
            return assemblerAvailable;
        }

        public boolean isCCompilerAvailable() {
            return cCompilerAvailable;
        }
    }
}

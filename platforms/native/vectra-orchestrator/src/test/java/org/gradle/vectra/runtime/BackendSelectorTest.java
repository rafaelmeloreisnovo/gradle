package org.gradle.vectra.runtime;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackendSelectorTest {

    private final BackendSelector backendSelector = new BackendSelector();

    @Test
    void selectsAssemblyBackendFirstOnLinuxX8664() {
        CapabilityReport capabilityReport = new CapabilityReport(
            CapabilityReport.OperatingSystem.LINUX,
            CapabilityReport.Architecture.X86_64,
            EnumSet.of(CapabilityReport.SimdInstruction.SSE2, CapabilityReport.SimdInstruction.AVX2),
            new CapabilityReport.ToolchainAvailability(true, true)
        );

        BackendSelector.SelectionDecision decision = backendSelector.select(capabilityReport);

        assertEquals(BackendSelector.Backend.NATIVE_ASM, decision.getBackend());
    }

    @Test
    void fallsBackToNativeCWhenAssemblerIsMissingOnMacOsAarch64() {
        CapabilityReport capabilityReport = new CapabilityReport(
            CapabilityReport.OperatingSystem.MACOS,
            CapabilityReport.Architecture.AARCH64,
            EnumSet.of(CapabilityReport.SimdInstruction.NEON),
            new CapabilityReport.ToolchainAvailability(false, true)
        );

        BackendSelector.SelectionDecision decision = backendSelector.select(capabilityReport);

        assertEquals(BackendSelector.Backend.NATIVE_C, decision.getBackend());
    }

    @Test
    void selectsPureJavaWhenNoNativeToolchainsAreAvailableOnWindows() {
        CapabilityReport capabilityReport = new CapabilityReport(
            CapabilityReport.OperatingSystem.WINDOWS,
            CapabilityReport.Architecture.X86_64,
            EnumSet.of(CapabilityReport.SimdInstruction.SSE2),
            new CapabilityReport.ToolchainAvailability(false, false)
        );

        BackendSelector.SelectionDecision decision = backendSelector.select(capabilityReport);

        assertEquals(BackendSelector.Backend.JAVA_PURE, decision.getBackend());
    }

    @Test
    void selectsPureJavaForUnsupportedArchitecture() {
        CapabilityReport capabilityReport = new CapabilityReport(
            CapabilityReport.OperatingSystem.LINUX,
            CapabilityReport.Architecture.OTHER,
            EnumSet.noneOf(CapabilityReport.SimdInstruction.class),
            new CapabilityReport.ToolchainAvailability(true, true)
        );

        BackendSelector.SelectionDecision decision = backendSelector.select(capabilityReport);

        assertEquals(BackendSelector.Backend.JAVA_PURE, decision.getBackend());
    }
}

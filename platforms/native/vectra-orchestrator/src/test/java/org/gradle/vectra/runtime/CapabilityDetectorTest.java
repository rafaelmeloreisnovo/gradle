package org.gradle.vectra.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityDetectorTest {

    @Test
    void detectsLinuxX8664AndCompilersFromMocks() {
        CapabilityDetector detector = new CapabilityDetector(
            new FakeSystemInspector("Linux", "x86_64", "avx2"),
            command -> command.equals("as") || command.equals("clang")
        );

        CapabilityReport report = detector.detect();

        assertEquals(CapabilityReport.OperatingSystem.LINUX, report.getOperatingSystem());
        assertEquals(CapabilityReport.Architecture.X86_64, report.getArchitecture());
        assertTrue(report.getSimdInstructions().contains(CapabilityReport.SimdInstruction.SSE2));
        assertTrue(report.getSimdInstructions().contains(CapabilityReport.SimdInstruction.AVX2));
        assertTrue(report.getToolchainAvailability().isAssemblerAvailable());
        assertTrue(report.getToolchainAvailability().isCCompilerAvailable());
    }

    @Test
    void detectsMacOsArm64AndNeonFromArchitectureBaseline() {
        CapabilityDetector detector = new CapabilityDetector(
            new FakeSystemInspector("Mac OS X", "arm64", ""),
            command -> command.equals("cc")
        );

        CapabilityReport report = detector.detect();

        assertEquals(CapabilityReport.OperatingSystem.MACOS, report.getOperatingSystem());
        assertEquals(CapabilityReport.Architecture.AARCH64, report.getArchitecture());
        assertTrue(report.getSimdInstructions().contains(CapabilityReport.SimdInstruction.NEON));
        assertFalse(report.getToolchainAvailability().isAssemblerAvailable());
        assertTrue(report.getToolchainAvailability().isCCompilerAvailable());
    }

    @Test
    void includesSimdFromProbeWhenAvailable() {
        CapabilityDetector detector = new CapabilityDetector(
            new FakeSystemInspector("Linux", "x86_64", ""),
            command -> false,
            (architecture, os) -> java.util.EnumSet.of(CapabilityReport.SimdInstruction.AVX2)
        );

        CapabilityReport report = detector.detect();

        assertTrue(report.getSimdInstructions().contains(CapabilityReport.SimdInstruction.SSE2));
        assertTrue(report.getSimdInstructions().contains(CapabilityReport.SimdInstruction.AVX2));
    }

    @Test
    void detectsWindowsAndToolchainFallbackCandidates() {
        CapabilityDetector detector = new CapabilityDetector(
            new FakeSystemInspector("Windows 11", "amd64", "sse2"),
            command -> command.equals("ml64")
        );

        CapabilityReport report = detector.detect();

        assertEquals(CapabilityReport.OperatingSystem.WINDOWS, report.getOperatingSystem());
        assertEquals(CapabilityReport.Architecture.X86_64, report.getArchitecture());
        assertTrue(report.getToolchainAvailability().isAssemblerAvailable());
        assertFalse(report.getToolchainAvailability().isCCompilerAvailable());
    }

    private static final class FakeSystemInspector implements CapabilityDetector.SystemInspector {

        private final String osName;
        private final String osArch;
        private final String simdHint;

        private FakeSystemInspector(String osName, String osArch, String simdHint) {
            this.osName = osName;
            this.osArch = osArch;
            this.simdHint = simdHint;
        }

        @Override
        public String osName() {
            return osName;
        }

        @Override
        public String osArch() {
            return osArch;
        }

        @Override
        public String simdHint() {
            return simdHint;
        }
    }
}

package org.gradle.vectra.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityDecisionReportWriterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesCapabilitiesReportJson() throws Exception {
        CapabilityReport report = new CapabilityReport(
            CapabilityReport.OperatingSystem.LINUX,
            CapabilityReport.Architecture.X86_64,
            EnumSet.of(CapabilityReport.SimdInstruction.SSE2),
            new CapabilityReport.ToolchainAvailability(true, true)
        );
        BackendSelector.SelectionDecision decision = new BackendSelector.SelectionDecision(
            BackendSelector.Backend.NATIVE_ASM,
            java.util.List.of("selected")
        );

        CapabilityDecisionReportWriter writer = new CapabilityDecisionReportWriter();
        Path output = writer.write(report, decision, temporaryDirectory.resolve("build/reports/vectra/capabilities.json"));

        String json = Files.readString(output);
        assertTrue(Files.exists(output));
        assertTrue(json.contains("\"selectedBackend\": \"NATIVE_ASM\""));
        assertTrue(json.contains("\"operatingSystem\": \"LINUX\""));
    }
}

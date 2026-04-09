package org.gradle.vectra.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Persists backend selection inputs and outputs into a per-build JSON report.
 */
public class CapabilityDecisionReportWriter {

    private static final Path DEFAULT_REPORT_PATH = Path.of("build", "reports", "vectra", "capabilities.json");

    public Path write(CapabilityReport capabilityReport, BackendSelector.SelectionDecision decision) {
        return write(capabilityReport, decision, DEFAULT_REPORT_PATH);
    }

    Path write(CapabilityReport capabilityReport, BackendSelector.SelectionDecision decision, Path reportPath) {
        try {
            Path parent = reportPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(reportPath, toJson(capabilityReport, decision), StandardCharsets.UTF_8);
            return reportPath;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not write Vectra capabilities report to " + reportPath, ex);
        }
    }

    private String toJson(CapabilityReport capabilityReport, BackendSelector.SelectionDecision decision) {
        String simdJson = capabilityReport.getSimdInstructions().stream()
            .map(simd -> "\"" + simd.name() + "\"")
            .collect(Collectors.joining(", "));

        String reasonsJson = decision.getReasons().stream()
            .map(reason -> "\"" + escape(reason) + "\"")
            .collect(Collectors.joining(", "));

        return "{\n"
            + "  \"generatedAt\": \"" + Instant.now() + "\",\n"
            + "  \"operatingSystem\": \"" + capabilityReport.getOperatingSystem().name() + "\",\n"
            + "  \"architecture\": \"" + capabilityReport.getArchitecture().name() + "\",\n"
            + "  \"simdInstructions\": [" + simdJson + "],\n"
            + "  \"toolchain\": {\n"
            + "    \"assemblerAvailable\": " + capabilityReport.getToolchainAvailability().isAssemblerAvailable() + ",\n"
            + "    \"cCompilerAvailable\": " + capabilityReport.getToolchainAvailability().isCCompilerAvailable() + "\n"
            + "  },\n"
            + "  \"selectedBackend\": \"" + decision.getBackend().name() + "\",\n"
            + "  \"reasons\": [" + reasonsJson + "]\n"
            + "}\n";
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

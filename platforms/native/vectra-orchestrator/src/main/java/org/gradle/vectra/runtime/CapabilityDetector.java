package org.gradle.vectra.runtime;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Detects host capabilities used to choose the Vectra execution backend.
 */
public class CapabilityDetector {

    private static final String SIMD_HINT_PROPERTY = "vectra.simd";

    private final SystemInspector systemInspector;
    private final ToolchainProbe toolchainProbe;

    public CapabilityDetector() {
        this(new JvmSystemInspector(), new ProcessToolchainProbe());
    }

    CapabilityDetector(SystemInspector systemInspector, ToolchainProbe toolchainProbe) {
        this.systemInspector = systemInspector;
        this.toolchainProbe = toolchainProbe;
    }

    public CapabilityReport detect() {
        CapabilityReport.OperatingSystem operatingSystem = detectOperatingSystem(systemInspector.osName());
        CapabilityReport.Architecture architecture = detectArchitecture(systemInspector.osArch());
        Set<CapabilityReport.SimdInstruction> simdInstructions = detectSimdInstructions(architecture, systemInspector.simdHint());
        CapabilityReport.ToolchainAvailability toolchainAvailability = detectToolchainAvailability(operatingSystem);

        return new CapabilityReport(operatingSystem, architecture, simdInstructions, toolchainAvailability);
    }

    private CapabilityReport.OperatingSystem detectOperatingSystem(String osName) {
        String normalized = osName.toLowerCase(Locale.ROOT);
        if (normalized.contains("win")) {
            return CapabilityReport.OperatingSystem.WINDOWS;
        }
        if (normalized.contains("mac") || normalized.contains("darwin")) {
            return CapabilityReport.OperatingSystem.MACOS;
        }
        if (normalized.contains("nix") || normalized.contains("nux") || normalized.contains("linux")) {
            return CapabilityReport.OperatingSystem.LINUX;
        }
        return CapabilityReport.OperatingSystem.OTHER;
    }

    private CapabilityReport.Architecture detectArchitecture(String osArch) {
        String normalized = osArch.toLowerCase(Locale.ROOT);
        if (normalized.equals("x86_64") || normalized.equals("amd64")) {
            return CapabilityReport.Architecture.X86_64;
        }
        if (normalized.equals("aarch64") || normalized.equals("arm64")) {
            return CapabilityReport.Architecture.AARCH64;
        }
        return CapabilityReport.Architecture.OTHER;
    }

    private Set<CapabilityReport.SimdInstruction> detectSimdInstructions(CapabilityReport.Architecture architecture, String simdHint) {
        EnumSet<CapabilityReport.SimdInstruction> simdInstructions = EnumSet.noneOf(CapabilityReport.SimdInstruction.class);

        if (architecture == CapabilityReport.Architecture.X86_64) {
            simdInstructions.add(CapabilityReport.SimdInstruction.SSE2);
        } else if (architecture == CapabilityReport.Architecture.AARCH64) {
            simdInstructions.add(CapabilityReport.SimdInstruction.NEON);
        }

        if (simdHint != null && !simdHint.isBlank()) {
            Arrays.stream(simdHint.split(","))
                .map(candidate -> candidate.trim().toLowerCase(Locale.ROOT))
                .forEach(candidate -> {
                    if (candidate.equals("sse2")) {
                        simdInstructions.add(CapabilityReport.SimdInstruction.SSE2);
                    } else if (candidate.equals("avx2")) {
                        simdInstructions.add(CapabilityReport.SimdInstruction.AVX2);
                    } else if (candidate.equals("neon")) {
                        simdInstructions.add(CapabilityReport.SimdInstruction.NEON);
                    }
                });
        }

        return simdInstructions;
    }

    private CapabilityReport.ToolchainAvailability detectToolchainAvailability(CapabilityReport.OperatingSystem operatingSystem) {
        boolean assemblerAvailable;
        boolean cCompilerAvailable;

        if (operatingSystem == CapabilityReport.OperatingSystem.WINDOWS) {
            assemblerAvailable = anyToolAvailable("ml64", "clang");
            cCompilerAvailable = anyToolAvailable("cl", "clang", "gcc");
        } else {
            assemblerAvailable = anyToolAvailable("as", "clang");
            cCompilerAvailable = anyToolAvailable("cc", "clang", "gcc");
        }

        return new CapabilityReport.ToolchainAvailability(assemblerAvailable, cCompilerAvailable);
    }

    private boolean anyToolAvailable(String... commands) {
        for (String command : commands) {
            if (toolchainProbe.isAvailable(command)) {
                return true;
            }
        }
        return false;
    }

    interface SystemInspector {
        String osName();

        String osArch();

        String simdHint();
    }

    interface ToolchainProbe {
        boolean isAvailable(String command);
    }

    private static class JvmSystemInspector implements SystemInspector {

        @Override
        public String osName() {
            return System.getProperty("os.name", "unknown");
        }

        @Override
        public String osArch() {
            return System.getProperty("os.arch", "unknown");
        }

        @Override
        public String simdHint() {
            return System.getProperty(SIMD_HINT_PROPERTY, "");
        }
    }

    private static class ProcessToolchainProbe implements ToolchainProbe {

        @Override
        public boolean isAvailable(String command) {
            ProcessBuilder processBuilder = new ProcessBuilder(command, "--version");
            processBuilder.redirectErrorStream(true);
            try {
                Process process = processBuilder.start();
                if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    return false;
                }
                return true;
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                return false;
            }
        }
    }
}

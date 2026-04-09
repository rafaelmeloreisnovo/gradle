package org.gradle.vectra.perf;

import com.sun.management.ThreadMXBean;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class VectraPerfRunner {
    private static final int MICRO_ITERATIONS = 800;
    private static final int MACRO_ITERATIONS = 16_000;

    public static void main(String[] args) throws Exception {
        Map<String, String> cli = parseArgs(args);
        Path nativeC = Path.of(required(cli, "native-c"));
        Path nativeAsm = Path.of(required(cli, "native-asm"));
        Path report = Path.of(required(cli, "report"));
        Path summary = Path.of(required(cli, "summary"));
        String platform = required(cli, "platform");

        PerfResult javaPure = runJavaPure();
        PerfResult nativeCResult = runNative("c", nativeC);
        PerfResult nativeAsmResult = runNative("asm", nativeAsm);

        String json = toJson(platform, javaPure, nativeCResult, nativeAsmResult);
        Files.createDirectories(report.getParent());
        Files.writeString(report, json, StandardCharsets.UTF_8);

        String md = toSummaryMarkdown(platform, javaPure, nativeCResult, nativeAsmResult);
        Files.createDirectories(summary.getParent());
        Files.writeString(summary, md, StandardCharsets.UTF_8);
    }

    private static PerfResult runJavaPure() {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long tid = Thread.currentThread().threadId();

        JavaPureEngine engine = new JavaPureEngine();
        byte[] input = new byte[32];
        byte[] stepOut = new byte[32];
        byte[] collapseOut = new byte[32];

        long[] init = new long[MICRO_ITERATIONS];
        long[] step = new long[MICRO_ITERATIONS];
        long[] collapse = new long[MICRO_ITERATIONS];
        long[] inject = new long[MICRO_ITERATIONS];

        for (int i = 0; i < MICRO_ITERATIONS; i++) {
            long t0 = System.nanoTime();
            engine.init();
            init[i] = System.nanoTime() - t0;

            t0 = System.nanoTime();
            engine.step(input, stepOut);
            step[i] = System.nanoTime() - t0;

            t0 = System.nanoTime();
            engine.collapse(collapseOut);
            collapse[i] = System.nanoTime() - t0;

            t0 = System.nanoTime();
            engine.inject(input);
            inject[i] = System.nanoTime() - t0;
        }

        long allocStart = bean.getThreadAllocatedBytes(tid);
        long macroStart = System.nanoTime();
        for (int i = 0; i < MACRO_ITERATIONS; i++) {
            engine.step(input, stepOut);
        }
        long macroNanos = System.nanoTime() - macroStart;
        long allocEnd = bean.getThreadAllocatedBytes(tid);

        return new PerfResult(
            "java-puro",
            p95(init),
            p95(step),
            p95(collapse),
            p95(inject),
            (double) (allocEnd - allocStart) / MACRO_ITERATIONS,
            macroNanos / 1_000_000L,
            Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        );
    }

    private static PerfResult runNative(String impl, Path executable) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(executable.toAbsolutePath().toString()).redirectErrorStream(true).start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        if (code != 0) {
            throw new IllegalStateException("Native benchmark failed for " + impl + ": " + out);
        }

        Map<String, Double> values = new HashMap<>();
        for (String line : out.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.contains("=")) {
                continue;
            }
            String[] parts = trimmed.split("=", 2);
            values.put(parts[0], Double.parseDouble(parts[1]));
        }

        return new PerfResult(
            impl,
            values.getOrDefault("p95_init_ns", 0.0).longValue(),
            values.getOrDefault("p95_step_ns", 0.0).longValue(),
            values.getOrDefault("p95_collapse_ns", 0.0).longValue(),
            values.getOrDefault("p95_inject_ns", 0.0).longValue(),
            values.getOrDefault("alloc_per_step", 0.0),
            values.getOrDefault("macro_total_ms", 0.0).longValue(),
            values.getOrDefault("rss_bytes", 0.0).longValue()
        );
    }

    private static String toJson(String platform, PerfResult... results) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"schema_version\": 1,\n");
        sb.append("  \"generated_at\": \"").append(Instant.now()).append("\",\n");
        sb.append("  \"platform\": \"").append(platform).append("\",\n");
        sb.append("  \"slo\": {\n");
        sb.append("    \"hot_path_alloc_per_step\": 0,\n");
        sb.append("    \"memory_stability_max_delta_bytes\": 1048576\n");
        sb.append("  },\n");
        sb.append("  \"results\": [\n");
        for (int i = 0; i < results.length; i++) {
            PerfResult r = results[i];
            sb.append("    {\n");
            sb.append("      \"impl\": \"").append(r.impl).append("\",\n");
            sb.append("      \"p95_init_ns\": ").append(r.p95InitNs).append(",\n");
            sb.append("      \"p95_step_ns\": ").append(r.p95StepNs).append(",\n");
            sb.append("      \"p95_collapse_ns\": ").append(r.p95CollapseNs).append(",\n");
            sb.append("      \"p95_inject_ns\": ").append(r.p95InjectNs).append(",\n");
            sb.append("      \"alloc_per_step\": ").append(String.format(Locale.ROOT, "%.4f", r.allocPerStep)).append(",\n");
            sb.append("      \"macro_total_ms\": ").append(r.macroTotalMs).append(",\n");
            sb.append("      \"memory_bytes\": ").append(r.memoryBytes).append("\n");
            sb.append("    }");
            if (i < results.length - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append("  ],\n");
        for (PerfResult r : results) {
            sb.append("  \"").append(metricKey(r.impl, "p95_step_ns")).append("\": ").append(r.p95StepNs).append(",\n");
            sb.append("  \"").append(metricKey(r.impl, "macro_total_ms")).append("\": ").append(r.macroTotalMs).append(",\n");
            sb.append("  \"").append(metricKey(r.impl, "alloc_per_step")).append("\": ").append(String.format(Locale.ROOT, "%.4f", r.allocPerStep)).append(",\n");
        }
        sb.append("  \"status\": \"ok\"\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String toSummaryMarkdown(String platform, PerfResult... results) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Vectra Performance Summary\n\n");
        sb.append("- schema: `v1`\n");
        sb.append("- platform: `").append(platform).append("`\n");
        sb.append("- generated_at: `").append(Instant.now()).append("`\n\n");

        sb.append("## SLOs técnicos\n\n");
        sb.append("- Alocações no hot path (`step/collapse`): **0 por passo**.\n");
        sb.append("- Latência p95 por etapa (`init/step/collapse/inject`) rastreada por implementação.\n");
        sb.append("- Memória estável: delta máximo alvo <= **1 MiB** durante macro benchmark.\n\n");

        sb.append("## Matriz comparativa (`java puro` vs `c` vs `asm`)\n\n");
        sb.append("| Implementação | p95 init (ns) | p95 step (ns) | p95 collapse (ns) | p95 inject (ns) | alloc/step | macro total (ms) | memória (bytes) |\n");
        sb.append("|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (PerfResult r : results) {
            sb.append("| ").append(r.impl).append(" | ")
                .append(r.p95InitNs).append(" | ")
                .append(r.p95StepNs).append(" | ")
                .append(r.p95CollapseNs).append(" | ")
                .append(r.p95InjectNs).append(" | ")
                .append(String.format(Locale.ROOT, "%.4f", r.allocPerStep)).append(" | ")
                .append(r.macroTotalMs).append(" | ")
                .append(r.memoryBytes).append(" |\n");
        }

        return sb.toString();
    }

    private static String metricKey(String impl, String suffix) {
        return impl.replace('-', '_') + "_" + suffix;
    }

    private static long p95(long[] values) {
        long[] copy = Arrays.copyOf(values, values.length);
        Arrays.sort(copy);
        return copy[(int) Math.ceil(copy.length * 0.95) - 1];
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> parsed = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                continue;
            }
            String[] parts = arg.substring(2).split("=", 2);
            parsed.put(parts[0], parts[1]);
        }
        return parsed;
    }

    private static String required(Map<String, String> args, String key) {
        String value = args.get(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Missing --" + key);
        }
        return value;
    }

    private record PerfResult(
        String impl,
        long p95InitNs,
        long p95StepNs,
        long p95CollapseNs,
        long p95InjectNs,
        double allocPerStep,
        long macroTotalMs,
        long memoryBytes
    ) {
    }

    private static final class JavaPureEngine {
        private final int[] lane = new int[32];
        private final int[] pulse = new int[32];
        private int cycle;

        void init() {
            Arrays.fill(lane, 0);
            Arrays.fill(pulse, 0);
            cycle = 0;
        }

        void step(byte[] input, byte[] output) {
            for (int i = 0; i < lane.length; i++) {
                int sample = input[i] & 0xFF;
                lane[i] = Integer.rotateLeft(lane[i] ^ (sample + cycle + i), 5) * 0x9E3779B9;
                pulse[i] ^= sample * 0x45D9F3B;
                output[i] = (byte) (lane[i] ^ pulse[i]);
            }
            cycle = (cycle + 1) % 56;
        }

        void collapse(byte[] output) {
            for (int i = 0; i < output.length; i++) {
                output[i] = (byte) (lane[i] ^ (pulse[i] >>> 8));
            }
        }

        void inject(byte[] patch) {
            for (int i = 0; i < patch.length; i++) {
                lane[i % lane.length] ^= (patch[i] & 0xFF) << ((i & 3) * 8);
            }
        }
    }
}

# vectra-orchestrator

## Scope
The `vectra-orchestrator` module provides a dedicated public surface for orchestrating native Vectra flows inside Gradle builds, with APIs in an isolated package namespace (`org.gradle.vectra.*`) to avoid collisions with core packages.

## Usage boundaries
- This module is strictly for Vectra integration and extension scenarios.
- It does not replace or alter existing Gradle core public APIs.
- It does not redefine default behavior for dependency resolution, execution, cache, toolchains, or the Gradle lifecycle.

## Generated artifacts
With the current setup, the module produces:
- Main `vectra-orchestrator` module JAR;
- Sources JAR (`withSourcesJar()`).

The `src/main/c` and `src/main/asm` directories are already provisioned for future native component evolution, without changing the standard pipeline at this stage.

## Gradle compatibility
The integration is compatible with default Gradle behavior: the module adds isolated, opt-in capabilities without changing defaults for existing builds.

## Native core (layout and lifecycle)
The native implementation is partitioned into:
- `src/main/c/include/vectra_state.h`: fixed binary layout (512 bytes), 64-byte alignment, and little-endian convention.
- `src/main/c/vectra_bridge.c`: C ABI bridge for JNI/Panama with a preallocated static pool and explicit lifecycle (`init`, `step`, `collapse`, `inject`, `release`).
- `src/main/c/vectra_core.c`: deterministic math core with 56-cycle geometric dynamics in Q16.16 (derivative, antiderivative, recursive term, inversion, and 5 adaptive weights).
- `src/main/asm/vectra_pulse.S`: vector mixing routine used by the core.

## Allocation and runtime policy
Dynamic allocation is not allowed in the critical path (`step`/`collapse`).
In the critical path we also avoid floating-point/memory libc helpers to reduce overhead and preserve predictability.
See `CONTRIBUTING.md` for the mandatory review checklist.

## Technical SLOs and benchmark
- SLOs defined for the module:
  - hot-path allocations (`step/collapse`) = **0 per step**;
  - p95 latency per stage (`init`, `step`, `collapse`, `inject`);
  - stable memory usage (target delta <= 1 MiB in macro benchmark).
- Micro and macro benchmarks are in `src/performanceTest`.
- To run benchmark and regression gate:

```bash
./gradlew :platforms:native:vectra-orchestrator:vectraPerfBenchmark
./gradlew :platforms:native:vectra-orchestrator:vectraPerfGate
```

Generated reports:
- `build/reports/vectra/perf-report-v1-<platform>.json` (versioned);
- `build/reports/vectra/perf-summary.md` (comparative matrix `pure java` vs `c` vs `asm`).

## Backend selection and capability report
The `org.gradle.vectra.runtime` package detects host capabilities (OS, architecture, SIMD, and toolchain), applies the selection policy with priority `native asm > native c > pure java`, and persists the per-build decision to:

- `build/reports/vectra/capabilities.json`

## Platform limitations
- **Linux x86_64 / aarch64:** `asm` and `c` paths are eligible when the corresponding toolchain is available.
- **macOS x86_64 / aarch64:** `asm` and `c` paths are eligible when the corresponding toolchain is available.
- **Windows x86_64:** `asm` path depends on `ml64`/`clang`; `c` path depends on `cl`/`clang`/`gcc`.
- **Windows aarch64:** currently only `pure java` fallback is guaranteed until dedicated toolchain coverage and native artifacts are available.
- **Architectures other than x86_64/aarch64:** mandatory fallback to `pure java`.
- **JVM SIMD detection:** does not execute native instruction probing; uses architecture baseline and an optional hint via `-Dvectra.simd=...`.

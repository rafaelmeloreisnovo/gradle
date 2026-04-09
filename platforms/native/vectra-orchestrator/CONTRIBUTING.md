# Vectra Native Path Guidelines

## Critical-path allocation policy
`step` and `collapse` are hard real-time style operations and **must not** perform dynamic allocation.

Forbidden in `src/main/c/vectra_core.c`, `src/main/c/vectra_bridge.c`, and any native file called from `step/collapse`:
- `malloc`, `calloc`, `realloc`, `free`
- `posix_memalign`, `aligned_alloc`
- C++ `new`/`delete` (if C++ wrappers are introduced)

## Native runtime policy
To reduce overhead and friction in the critical path, avoid depending on libc routines inside `step/collapse` and helpers called by them (for example `memset`, `memcpy`, `sin`, `log`, `sqrt`).
Use local deterministic implementations (Q16.16, fixed tables, and explicit loops) whenever possible.

## Review checks (mandatory)
Run the checks below during review:

```bash
rg -n "malloc|calloc|realloc|free|aligned_alloc|posix_memalign|new |delete " platforms/native/vectra-orchestrator/src/main/c
rg -n "memset|memcpy|sin\(|cos\(|log\(|sqrt\(" platforms/native/vectra-orchestrator/src/main/c/vectra_core.c
rg -n "vectra_bridge_step|vectra_bridge_collapse|vectra_core_step|vectra_core_collapse" platforms/native/vectra-orchestrator/src/main/c
```

If an allocation or forbidden runtime helper appears in the critical path, the review must block merge.

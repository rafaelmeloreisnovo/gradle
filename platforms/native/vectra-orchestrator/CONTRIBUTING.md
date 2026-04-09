# Vectra Native Path Guidelines

## Critical-path allocation policy
`step` and `collapse` are hard real-time style operations and **must not** perform dynamic allocation.

Forbidden in `src/main/c/vectra_core.c`, `src/main/c/vectra_bridge.c`, and any native file called from `step/collapse`:
- `malloc`, `calloc`, `realloc`, `free`
- `posix_memalign`, `aligned_alloc`
- C++ `new`/`delete` (if C++ wrappers are introduced)

## Native runtime policy
Para reduzir overhead e fricção no caminho crítico, evitar dependência de rotinas da libc dentro de `step/collapse` e dos helpers chamados por elas (ex.: `memset`, `memcpy`, `sin`, `log`, `sqrt`).
Use implementações determinísticas locais (Q16.16, tabelas fixas e laços explícitos) sempre que possível.

## Review checks (mandatory)
Run the checks below during review:

```bash
rg -n "malloc|calloc|realloc|free|aligned_alloc|posix_memalign|new |delete " platforms/native/vectra-orchestrator/src/main/c
rg -n "memset|memcpy|sin\(|cos\(|log\(|sqrt\(" platforms/native/vectra-orchestrator/src/main/c/vectra_core.c
rg -n "vectra_bridge_step|vectra_bridge_collapse|vectra_core_step|vectra_core_collapse" platforms/native/vectra-orchestrator/src/main/c
```

If an allocation or forbidden runtime helper appears in the critical path, the review must block merge.

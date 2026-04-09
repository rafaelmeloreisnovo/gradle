# vectra-orchestrator

## Escopo
O módulo `vectra-orchestrator` fornece uma superfície pública dedicada para orquestração de fluxos nativos Vectra dentro do build do Gradle, com APIs em pacote isolado (`org.gradle.vectra.*`) para evitar colisão com pacotes core.

## Limites de uso
- Este módulo é estritamente de integração e extensão para cenários Vectra.
- Não substitui nem altera APIs públicas existentes do Gradle core.
- Não redefine comportamento padrão de resolução, execução, cache, toolchains ou ciclo de vida do Gradle.

## Artefatos gerados
Com a configuração atual, o módulo gera:
- JAR principal do módulo `vectra-orchestrator`;
- JAR de sources (`withSourcesJar()`).

Os diretórios `src/main/c` e `src/main/asm` já estão provisionados para evolução futura de componentes nativos, sem alterar o pipeline padrão neste estágio.

## Compatibilidade com Gradle
A integração é compatível com o comportamento padrão do Gradle: o módulo adiciona capacidades isoladas e opt-in, sem mudar defaults de builds existentes.

## Núcleo nativo (layout e ciclo de vida)
A implementação nativa foi particionada em:
- `src/main/c/include/vectra_state.h`: layout binário fixo (512 bytes), alinhamento 64 bytes e convenção little-endian.
- `src/main/c/vectra_bridge.c`: bridge C ABI para JNI/Panama com pool estático pré-alocado e ciclo de vida explícito (`init`, `step`, `collapse`, `inject`, `release`).
- `src/main/c/vectra_core.c`: núcleo matemático determinístico com dinâmica geométrica de 56 ciclos em Q16.16 (derivada, antiderivada, termo recursivo, inversão e 5 pesos adaptativos).
- `src/main/asm/vectra_pulse.S`: rotina de mistura vetorial usada pelo core.

## Política de alocação e runtime
Não é permitido uso de alocação dinâmica no caminho crítico (`step`/`collapse`).
No caminho crítico também evitamos helpers de libc de ponto flutuante/memória para reduzir overhead e preservar previsibilidade.
Consulte `CONTRIBUTING.md` para checklist obrigatório de review.

## Seleção de backend e relatório de capacidades
O pacote `org.gradle.vectra.runtime` detecta capacidades do host (SO, arquitetura, SIMD e toolchain), aplica a política de seleção com prioridade `asm nativo > c nativo > java puro` e persiste a decisão por build em:

- `build/reports/vectra/capabilities.json`

## Limitações por plataforma
- **Linux x86_64 / aarch64:** caminhos `asm` e `c` são elegíveis quando toolchain correspondente está disponível.
- **macOS x86_64 / aarch64:** caminhos `asm` e `c` são elegíveis quando toolchain correspondente está disponível.
- **Windows x86_64:** caminho `asm` depende de `ml64`/`clang`; caminho `c` depende de `cl`/`clang`/`gcc`.
- **Windows aarch64:** atualmente só há fallback garantido para `java puro` até haver cobertura de toolchain e artefatos nativos dedicados.
- **Arquiteturas fora de x86_64/aarch64:** fallback obrigatório para `java puro`.
- **Detecção SIMD em JVM:** não executa probing nativo de instruções; usa baseline por arquitetura e hint opcional via propriedade `-Dvectra.simd=...`.

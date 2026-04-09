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
- `src/main/c/vectra_core.c`: núcleo matemático puro sobre `VectraState`.
- `src/main/asm/vectra_pulse.S`: rotina de mistura vetorial usada pelo core.

## Política de alocação
Não é permitido uso de alocação dinâmica no caminho crítico (`step`/`collapse`).
Consulte `CONTRIBUTING.md` para checklist obrigatório de review.

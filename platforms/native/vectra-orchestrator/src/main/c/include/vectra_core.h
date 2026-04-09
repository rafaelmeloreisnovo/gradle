#ifndef GRADLE_VECTRA_CORE_H
#define GRADLE_VECTRA_CORE_H

#include <stddef.h>
#include <stdint.h>

#include "vectra_state.h"

#define VECTRA_STEP_OUTPUT_BYTES VECTRA_LANES
#define VECTRA_COLLAPSE_OUTPUT_BYTES 32u

void vectra_state_init(VectraState* state);
int vectra_core_step(VectraState* state, const uint8_t* input, size_t input_len, uint8_t* output, size_t output_len);
int vectra_core_collapse(const VectraState* state, uint8_t* output, size_t output_len);
int vectra_core_inject(VectraState* state, const uint8_t* patch, size_t patch_len);

/* Implemented in src/main/asm/vectra_pulse.S */
void vectra_pulse_mix(uint32_t* lane, const uint32_t* aux, const uint32_t* pulse, size_t len);

#endif

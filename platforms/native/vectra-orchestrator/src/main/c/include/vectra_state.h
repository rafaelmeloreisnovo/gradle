#ifndef GRADLE_VECTRA_STATE_H
#define GRADLE_VECTRA_STATE_H

#include <stdint.h>

/*
 * Binary layout contract
 * ----------------------
 * - Endianness: little-endian for all serialized words.
 * - Alignment : 64-byte aligned state struct.
 * - Fixed size: VectraState is exactly 512 bytes.
 * - Numeric format in the core: signed Q16.16 fixed-point.
 */

#define VECTRA_DIM 3u
#define VECTRA_LANES 32u
#define VECTRA_WEIGHTS 5u
#define VECTRA_PHASE_CYCLES 56u
#define VECTRA_VERSION 2u
#define VECTRA_STATE_SIZE_BYTES 512u
#define VECTRA_SCRATCH_SIZE_BYTES 512u

typedef struct __attribute__((aligned(64))) VectraState {
    uint32_t version;
    uint32_t flags;
    uint64_t tick;

    uint32_t cycle;
    uint32_t reserved0;

    int32_t x[VECTRA_DIM];
    int32_t prev[VECTRA_DIM];
    int32_t prev2[VECTRA_DIM];

    int32_t phase_q16;
    int32_t delta_q16;
    int32_t coherence_q16;
    int32_t entropy_q16;

    int32_t weights_q16[VECTRA_WEIGHTS];

    uint32_t lane[VECTRA_LANES];
    uint32_t aux[VECTRA_LANES];
    uint32_t pulse[VECTRA_LANES];

    uint8_t reserved[32];
} VectraState;

_Static_assert(sizeof(VectraState) == VECTRA_STATE_SIZE_BYTES, "VectraState must remain 512 bytes");
_Static_assert(_Alignof(VectraState) == 64, "VectraState alignment must remain 64 bytes");

#endif

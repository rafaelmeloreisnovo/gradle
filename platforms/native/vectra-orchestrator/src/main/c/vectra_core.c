#include "include/vectra_core.h"

#include <string.h>

static uint32_t rotate_left_5(uint32_t value) {
    return (value << 5u) | (value >> 27u);
}

void vectra_state_init(VectraState* state) {
    memset(state, 0, sizeof(*state));
    state->version = VECTRA_VERSION;
}

int vectra_core_step(VectraState* state, const uint8_t* input, size_t input_len, uint8_t* output, size_t output_len) {
    if (state == NULL || output == NULL || output_len < VECTRA_LANES) {
        return -1;
    }

    for (size_t i = 0; i < VECTRA_LANES; ++i) {
        const uint8_t sample = input != NULL && i < input_len ? input[i] : 0u;
        state->aux[i] = rotate_left_5(state->lane[i] ^ (uint32_t) sample ^ (uint32_t) state->tick);
        state->pulse[i] ^= (uint32_t) sample * 0x9E3779B9u;
    }

    vectra_pulse_mix(state->lane, state->aux, state->pulse, VECTRA_LANES);

    for (size_t i = 0; i < VECTRA_LANES; ++i) {
        output[i] = (uint8_t) (state->lane[i] & 0xFFu);
    }

    state->tick += 1u;
    return 0;
}

int vectra_core_collapse(const VectraState* state, uint8_t* output, size_t output_len) {
    if (state == NULL || output == NULL || output_len < VECTRA_LANES) {
        return -1;
    }

    for (size_t i = 0; i < VECTRA_LANES; ++i) {
        output[i] = (uint8_t) ((state->lane[i] ^ state->aux[i] ^ state->pulse[i]) & 0xFFu);
    }

    return 0;
}

int vectra_core_inject(VectraState* state, const uint8_t* patch, size_t patch_len) {
    if (state == NULL || patch == NULL) {
        return -1;
    }

    for (size_t i = 0; i < patch_len; ++i) {
        const size_t lane_index = i % VECTRA_LANES;
        state->lane[lane_index] ^= (uint32_t) patch[i] << ((i % 4u) * 8u);
    }

    return 0;
}

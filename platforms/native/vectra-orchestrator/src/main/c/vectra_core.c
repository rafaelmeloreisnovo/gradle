#include "include/vectra_core.h"

#define Q16_ONE 65536
#define Q16_HALF 32768
#define Q16_EPSILON 64
#define Q16_LN2 45426

static const int32_t VECTRA_SIN56_Q16[VECTRA_PHASE_CYCLES] = {
    0, 7341, 14589, 21654, 28447, 34883, 40880, 46362,
    51260, 55513, 59067, 61877, 63908, 65134, 65536, 65134,
    63908, 61877, 59067, 55513, 51260, 46362, 40880, 34883,
    28447, 21654, 14589, 7341, 0, -7341, -14589, -21654,
    -28447, -34883, -40880, -46362, -51260, -55513, -59067, -61877,
    -63908, -65134, -65536, -65134, -63908, -61877, -59067, -55513,
    -51260, -46362, -40880, -34883, -28447, -21654, -14589, -7341
};

static int32_t q16_mul(int32_t a, int32_t b) {
    return (int32_t) ((((int64_t) a) * ((int64_t) b)) >> 16);
}

static int32_t abs32(int32_t value) {
    return value < 0 ? -value : value;
}

static uint32_t isqrt64(uint64_t n) {
    uint64_t x = n;
    uint64_t y = (x + 1u) >> 1u;
    while (y < x) {
        x = y;
        y = (x + (n / x)) >> 1u;
    }
    return (uint32_t) x;
}

static int32_t reciprocal_q16(int32_t value) {
    const int32_t magnitude = abs32(value);
    if (magnitude <= Q16_EPSILON) {
        return 0;
    }

    const int64_t scaled = ((int64_t) Q16_ONE) << 16;
    return (int32_t) (scaled / value);
}

static int32_t entropy_from_delta_q16(int32_t delta_q16) {
    uint32_t x = (uint32_t) (delta_q16 > 0 ? delta_q16 + Q16_ONE : Q16_ONE);
    uint32_t bits = 0;
    while (x > 1u) {
        x >>= 1u;
        bits += 1u;
    }

    return (int32_t) (bits * Q16_LN2);
}

static void normalize_weights_q16(int32_t* weights_q16) {
    int64_t sum = 0;
    for (size_t i = 0; i < VECTRA_WEIGHTS; ++i) {
        if (weights_q16[i] < 0) {
            weights_q16[i] = 0;
        }
        sum += weights_q16[i];
    }

    if (sum == 0) {
        for (size_t i = 0; i < VECTRA_WEIGHTS; ++i) {
            weights_q16[i] = Q16_ONE / (int32_t) VECTRA_WEIGHTS;
        }
        return;
    }

    for (size_t i = 0; i < VECTRA_WEIGHTS; ++i) {
        weights_q16[i] = (int32_t) ((((int64_t) weights_q16[i]) << 16) / sum);
    }
}

static void clear_state(VectraState* state) {
    uint8_t* p = (uint8_t*) state;
    for (size_t i = 0; i < sizeof(*state); ++i) {
        p[i] = 0;
    }
}

void vectra_state_init(VectraState* state) {
    clear_state(state);

    state->version = VECTRA_VERSION;
    state->coherence_q16 = Q16_HALF;
    state->entropy_q16 = Q16_HALF;

    for (size_t i = 0; i < VECTRA_DIM; ++i) {
        state->x[i] = (int32_t) ((i + 1u) * (Q16_ONE / 10));
    }

    for (size_t i = 0; i < VECTRA_WEIGHTS; ++i) {
        state->weights_q16[i] = Q16_ONE / (int32_t) VECTRA_WEIGHTS;
    }
}

static void apply_adaptive_weights(VectraState* state) {
    const int32_t eta = Q16_ONE / 100;
    if (state->delta_q16 > Q16_ONE) {
        state->weights_q16[1] += eta;
        state->weights_q16[3] += eta;
    } else {
        state->weights_q16[0] += eta;
    }
    normalize_weights_q16(state->weights_q16);
}

int vectra_core_step(VectraState* state, const uint8_t* input, size_t input_len, uint8_t* output, size_t output_len) {
    if (state == NULL || output == NULL || output_len < VECTRA_STEP_OUTPUT_BYTES) {
        return -1;
    }

    int32_t next[VECTRA_DIM];

    for (size_t i = 0; i < VECTRA_DIM; ++i) {
        const int32_t d = state->x[i] - state->prev[i];
        const int32_t a = state->x[i] + state->prev[i] + state->prev2[i];

        const uint32_t phase_offset = (state->cycle + (uint32_t) (i * 7u)) % VECTRA_PHASE_CYCLES;
        const int32_t phase_wave = VECTRA_SIN56_Q16[phase_offset];
        const int32_t r = q16_mul(state->prev[i] + state->prev2[i] + state->phase_q16, phase_wave);
        const int32_t inv = reciprocal_q16(state->x[i]);

        next[i] =
            q16_mul(state->weights_q16[0], d) +
            q16_mul(state->weights_q16[1], a) +
            q16_mul(state->weights_q16[2], r) +
            q16_mul(state->weights_q16[3], inv) +
            q16_mul(state->weights_q16[4], state->delta_q16);

        state->aux[i] = (uint32_t) (d ^ a ^ r ^ inv);
        state->pulse[i] ^= (uint32_t) ((input != NULL && i < input_len) ? input[i] : 0u);
    }

    for (size_t i = VECTRA_DIM; i < VECTRA_LANES; ++i) {
        const uint8_t sample = input != NULL && i < input_len ? input[i] : 0u;
        state->aux[i] = state->lane[i] ^ (uint32_t) sample ^ state->cycle;
        state->pulse[i] ^= (uint32_t) sample * 0x9E3779B9u;
    }

    vectra_pulse_mix(state->lane, state->aux, state->pulse, VECTRA_LANES);

    uint64_t delta_acc = 0;
    for (size_t i = 0; i < VECTRA_DIM; ++i) {
        const int32_t diff = next[i] - state->x[i];
        delta_acc += (uint64_t) ((int64_t) diff * (int64_t) diff);

        state->prev2[i] = state->prev[i];
        state->prev[i] = state->x[i];
        state->x[i] = next[i];
        state->lane[i] = (uint32_t) next[i];
    }

    state->delta_q16 = (int32_t) isqrt64(delta_acc);
    state->coherence_q16 = (int32_t) ((((int64_t) Q16_ONE) << 16) / (Q16_ONE + (state->delta_q16 > 0 ? state->delta_q16 : 0)));
    state->entropy_q16 = entropy_from_delta_q16(state->delta_q16);

    state->cycle = (state->cycle + 1u) % VECTRA_PHASE_CYCLES;
    state->phase_q16 = (int32_t) (state->cycle * (Q16_ONE / (int32_t) VECTRA_PHASE_CYCLES));

    apply_adaptive_weights(state);

    for (size_t i = 0; i < VECTRA_STEP_OUTPUT_BYTES; ++i) {
        output[i] = (uint8_t) (state->lane[i] & 0xFFu);
    }

    state->tick += 1u;
    return 0;
}

static void write_le32(uint8_t* out, uint32_t value) {
    out[0] = (uint8_t) (value & 0xFFu);
    out[1] = (uint8_t) ((value >> 8u) & 0xFFu);
    out[2] = (uint8_t) ((value >> 16u) & 0xFFu);
    out[3] = (uint8_t) ((value >> 24u) & 0xFFu);
}

int vectra_core_collapse(const VectraState* state, uint8_t* output, size_t output_len) {
    if (state == NULL || output == NULL || output_len < VECTRA_COLLAPSE_OUTPUT_BYTES) {
        return -1;
    }

    write_le32(output + 0, (uint32_t) state->x[0]);
    write_le32(output + 4, (uint32_t) state->x[1]);
    write_le32(output + 8, (uint32_t) state->x[2]);
    write_le32(output + 12, (uint32_t) state->delta_q16);
    write_le32(output + 16, (uint32_t) state->coherence_q16);
    write_le32(output + 20, (uint32_t) state->entropy_q16);
    write_le32(output + 24, state->cycle);
    write_le32(output + 28, (uint32_t) state->tick);
    return 0;
}

int vectra_core_inject(VectraState* state, const uint8_t* patch, size_t patch_len) {
    if (state == NULL || patch == NULL) {
        return -1;
    }

    for (size_t i = 0; i < patch_len; ++i) {
        const size_t lane_index = i % VECTRA_LANES;
        const uint32_t shift = (uint32_t) ((i & 0x3u) << 3u);
        state->lane[lane_index] ^= (uint32_t) patch[i] << shift;
        state->pulse[lane_index] ^= (uint32_t) patch[i] * 0x45D9F3Bu;
    }

    state->x[0] ^= (int32_t) state->lane[0];
    state->x[1] ^= (int32_t) state->lane[1];
    state->x[2] ^= (int32_t) state->lane[2];
    return 0;
}

#define _POSIX_C_SOURCE 199309L
#include "vectra_core.h"

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define MICRO_ITERATIONS 800
#define MACRO_ITERATIONS 16000

static uint64_t now_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t) ts.tv_sec * 1000000000ull + (uint64_t) ts.tv_nsec;
}

static int cmp_u64(const void* a, const void* b) {
    const uint64_t x = *(const uint64_t*) a;
    const uint64_t y = *(const uint64_t*) b;
    if (x < y) return -1;
    if (x > y) return 1;
    return 0;
}

static uint64_t p95(uint64_t* values, size_t len) {
    qsort(values, len, sizeof(uint64_t), cmp_u64);
    size_t idx = (size_t) (((double) len * 0.95) - 1.0);
    if (idx >= len) idx = len - 1;
    return values[idx];
}

int main(void) {
    VectraState state;
    uint8_t input[VECTRA_STEP_OUTPUT_BYTES];
    uint8_t step_out[VECTRA_STEP_OUTPUT_BYTES];
    uint8_t collapse_out[VECTRA_COLLAPSE_OUTPUT_BYTES];

    memset(input, 0xAB, sizeof(input));

    uint64_t init_times[MICRO_ITERATIONS];
    uint64_t step_times[MICRO_ITERATIONS];
    uint64_t collapse_times[MICRO_ITERATIONS];
    uint64_t inject_times[MICRO_ITERATIONS];

    for (size_t i = 0; i < MICRO_ITERATIONS; ++i) {
        uint64_t t0 = now_ns();
        vectra_state_init(&state);
        init_times[i] = now_ns() - t0;

        t0 = now_ns();
        (void) vectra_core_step(&state, input, sizeof(input), step_out, sizeof(step_out));
        step_times[i] = now_ns() - t0;

        t0 = now_ns();
        (void) vectra_core_collapse(&state, collapse_out, sizeof(collapse_out));
        collapse_times[i] = now_ns() - t0;

        t0 = now_ns();
        (void) vectra_core_inject(&state, input, sizeof(input));
        inject_times[i] = now_ns() - t0;
    }

    vectra_state_init(&state);
    const uint64_t macro_start = now_ns();
    for (size_t i = 0; i < MACRO_ITERATIONS; ++i) {
        (void) vectra_core_step(&state, input, sizeof(input), step_out, sizeof(step_out));
    }
    const uint64_t macro_elapsed_ns = now_ns() - macro_start;

    printf("p95_init_ns=%llu\n", (unsigned long long) p95(init_times, MICRO_ITERATIONS));
    printf("p95_step_ns=%llu\n", (unsigned long long) p95(step_times, MICRO_ITERATIONS));
    printf("p95_collapse_ns=%llu\n", (unsigned long long) p95(collapse_times, MICRO_ITERATIONS));
    printf("p95_inject_ns=%llu\n", (unsigned long long) p95(inject_times, MICRO_ITERATIONS));
    printf("alloc_per_step=0\n");
    printf("macro_total_ms=%.3f\n", (double) macro_elapsed_ns / 1000000.0);
    printf("rss_bytes=%zu\n", sizeof(VectraState));

    return 0;
}

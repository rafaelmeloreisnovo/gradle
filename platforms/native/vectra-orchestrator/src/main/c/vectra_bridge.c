#include "include/vectra_core.h"

#include <stddef.h>
#include <stdint.h>
#include <string.h>

#define VECTRA_BRIDGE_MAX_CONTEXTS 8u

typedef struct __attribute__((aligned(64))) VectraBridgeContext {
    uint32_t in_use;
    uint32_t reserved;
    VectraState state;
    uint8_t scratch[VECTRA_SCRATCH_SIZE_BYTES];
} VectraBridgeContext;

static VectraBridgeContext VECTRA_CONTEXT_POOL[VECTRA_BRIDGE_MAX_CONTEXTS];

static VectraBridgeContext* context_from_handle(int64_t handle) {
    if (handle <= 0) {
        return NULL;
    }

    const uint64_t index = (uint64_t) (handle - 1);
    if (index >= VECTRA_BRIDGE_MAX_CONTEXTS) {
        return NULL;
    }

    VectraBridgeContext* context = &VECTRA_CONTEXT_POOL[index];
    return context->in_use == 1u ? context : NULL;
}

/*
 * C ABI chosen to be callable from JNI glue or Panama downcalls.
 * Handle lifecycle is explicit: init -> step/collapse/inject -> release.
 */
int64_t vectra_bridge_init(void) {
    for (uint64_t i = 0; i < VECTRA_BRIDGE_MAX_CONTEXTS; ++i) {
        VectraBridgeContext* context = &VECTRA_CONTEXT_POOL[i];
        if (context->in_use == 0u) {
            memset(context, 0, sizeof(*context));
            context->in_use = 1u;
            vectra_state_init(&context->state);
            return (int64_t) (i + 1u);
        }
    }

    return -1;
}

int vectra_bridge_step(int64_t handle, const uint8_t* input, size_t input_len, uint8_t* output, size_t output_len) {
    VectraBridgeContext* context = context_from_handle(handle);
    if (context == NULL) {
        return -1;
    }

    return vectra_core_step(&context->state, input, input_len, output, output_len);
}

int vectra_bridge_collapse(int64_t handle, uint8_t* output, size_t output_len) {
    VectraBridgeContext* context = context_from_handle(handle);
    if (context == NULL) {
        return -1;
    }

    return vectra_core_collapse(&context->state, output, output_len);
}

int vectra_bridge_inject(int64_t handle, const uint8_t* patch, size_t patch_len) {
    VectraBridgeContext* context = context_from_handle(handle);
    if (context == NULL) {
        return -1;
    }

    return vectra_core_inject(&context->state, patch, patch_len);
}

int vectra_bridge_release(int64_t handle) {
    VectraBridgeContext* context = context_from_handle(handle);
    if (context == NULL) {
        return -1;
    }

    memset(context, 0, sizeof(*context));
    return 0;
}

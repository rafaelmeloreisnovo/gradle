#include <stddef.h>
#include <stdint.h>

void vectra_pulse_mix(uint32_t* lane, const uint32_t* aux, const uint32_t* pulse, size_t len) {
    for (size_t i = 0; i < len; ++i) {
        const uint32_t a = aux[i];
        const uint32_t p = pulse[i];
        lane[i] = (lane[i] ^ a) + (p << ((i & 7u) + 1u));
        lane[i] = (lane[i] << 3u) | (lane[i] >> 29u);
    }
}

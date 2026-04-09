/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.vectra.runtime;

import org.gradle.vectra.engine.VectraEngine;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pure Java deterministic fallback implementation for the Vectra algorithm.
 */
public class JavaPureVectraEngine implements VectraEngine {

    private static final int Q16_ONE = 65536;
    private static final int Q16_HALF = 32768;
    private static final int VECTRA_DIM = 3;
    private static final int VECTRA_LANES = 32;
    private static final int VECTRA_WEIGHTS = 5;
    private static final int VECTRA_PHASE_CYCLES = 56;
    private static final int STEP_OUTPUT_BYTES = VECTRA_LANES;
    private static final int COLLAPSE_OUTPUT_BYTES = 32;

    private static final int[] VECTRA_SIN56_Q16 = {
        0, 7341, 14589, 21654, 28447, 34883, 40880, 46362,
        51260, 55513, 59067, 61877, 63908, 65134, 65536, 65134,
        63908, 61877, 59067, 55513, 51260, 46362, 40880, 34883,
        28447, 21654, 14589, 7341, 0, -7341, -14589, -21654,
        -28447, -34883, -40880, -46362, -51260, -55513, -59067, -61877,
        -63908, -65134, -65536, -65134, -63908, -61877, -59067, -55513,
        -51260, -46362, -40880, -34883, -28447, -21654, -14589, -7341
    };

    private final AtomicLong handleSequence = new AtomicLong(1);
    private final Map<Long, State> states = new ConcurrentHashMap<>();

    @Override
    public long init(ByteBuffer stateBuffer, ByteBuffer scratchBuffer) {
        if (stateBuffer == null || scratchBuffer == null) {
            return -1L;
        }

        long handle = handleSequence.getAndIncrement();
        states.put(handle, State.initial());
        return handle;
    }

    @Override
    public int step(long engineHandle, ByteBuffer inputBuffer, ByteBuffer outputBuffer) {
        State state = states.get(engineHandle);
        if (state == null || outputBuffer == null || outputBuffer.remaining() < STEP_OUTPUT_BYTES) {
            return -1;
        }

        synchronized (state) {
            int[] next = new int[VECTRA_DIM];
            long deltaAcc = 0L;

            for (int i = 0; i < VECTRA_DIM; i++) {
                int d = state.x[i] - state.prev[i];
                int a = state.x[i] + state.prev[i] + state.prev2[i];
                int phaseOffset = (int) ((state.cycle + (i * 7)) % VECTRA_PHASE_CYCLES);
                int phaseWave = VECTRA_SIN56_Q16[phaseOffset];
                int r = q16Mul(state.prev[i] + state.prev2[i] + state.phaseQ16, phaseWave);
                int inv = reciprocalQ16(state.x[i]);

                next[i] = q16Mul(state.weightsQ16[0], d)
                    + q16Mul(state.weightsQ16[1], a)
                    + q16Mul(state.weightsQ16[2], r)
                    + q16Mul(state.weightsQ16[3], inv)
                    + q16Mul(state.weightsQ16[4], state.deltaQ16);

                state.aux[i] = d ^ a ^ r ^ inv;
                int inputSample = readInputByte(inputBuffer, i);
                state.pulse[i] ^= inputSample;
            }

            for (int i = VECTRA_DIM; i < VECTRA_LANES; i++) {
                int sample = readInputByte(inputBuffer, i);
                state.aux[i] = state.lane[i] ^ sample ^ state.cycle;
                state.pulse[i] ^= sample * 0x9E3779B9;
            }

            pulseMix(state.lane, state.aux, state.pulse, VECTRA_LANES);

            for (int i = 0; i < VECTRA_DIM; i++) {
                int diff = next[i] - state.x[i];
                deltaAcc += (long) diff * diff;

                state.prev2[i] = state.prev[i];
                state.prev[i] = state.x[i];
                state.x[i] = next[i];
                state.lane[i] = next[i];
            }

            state.deltaQ16 = (int) Math.sqrt(deltaAcc);
            state.coherenceQ16 = (int) ((((long) Q16_ONE) << 16) / (Q16_ONE + Math.max(state.deltaQ16, 0)));
            state.entropyQ16 = entropyFromDeltaQ16(state.deltaQ16);
            state.cycle = (state.cycle + 1) % VECTRA_PHASE_CYCLES;
            state.phaseQ16 = state.cycle * (Q16_ONE / VECTRA_PHASE_CYCLES);
            applyAdaptiveWeights(state);
            state.tick++;

            for (int i = 0; i < STEP_OUTPUT_BYTES; i++) {
                outputBuffer.put((byte) (state.lane[i] & 0xFF));
            }
        }

        return 0;
    }

    @Override
    public int collapse(long engineHandle, ByteBuffer outputBuffer) {
        State state = states.get(engineHandle);
        if (state == null || outputBuffer == null || outputBuffer.remaining() < COLLAPSE_OUTPUT_BYTES) {
            return -1;
        }

        synchronized (state) {
            ByteBuffer out = outputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN);
            out.putInt(state.x[0]);
            out.putInt(state.x[1]);
            out.putInt(state.x[2]);
            out.putInt(state.deltaQ16);
            out.putInt(state.coherenceQ16);
            out.putInt(state.entropyQ16);
            out.putInt(state.cycle);
            out.putInt((int) state.tick);
        }

        return 0;
    }

    @Override
    public int inject(long engineHandle, ByteBuffer patchBuffer) {
        State state = states.get(engineHandle);
        if (state == null || patchBuffer == null) {
            return -1;
        }

        synchronized (state) {
            ByteBuffer patch = patchBuffer.slice();
            for (int i = 0; i < patch.remaining(); i++) {
                int v = patch.get(i) & 0xFF;
                int laneIndex = i % VECTRA_LANES;
                int shift = (i & 0x3) << 3;
                state.lane[laneIndex] ^= (v << shift);
                state.pulse[laneIndex] ^= v * 0x45D9F3B;
            }

            state.x[0] ^= state.lane[0];
            state.x[1] ^= state.lane[1];
            state.x[2] ^= state.lane[2];
        }

        return 0;
    }

    private static int readInputByte(ByteBuffer inputBuffer, int index) {
        if (inputBuffer == null || index >= inputBuffer.remaining()) {
            return 0;
        }
        return inputBuffer.get(inputBuffer.position() + index) & 0xFF;
    }

    private static void pulseMix(int[] lane, int[] aux, int[] pulse, int len) {
        for (int i = 0; i < len; i++) {
            lane[i] = Integer.rotateLeft((lane[i] ^ aux[i]) + pulse[i], 7);
        }
    }

    private static int q16Mul(int a, int b) {
        return (int) ((((long) a) * b) >> 16);
    }

    private static int reciprocalQ16(int value) {
        int magnitude = Math.abs(value);
        if (magnitude <= 64) {
            return 0;
        }
        long scaled = ((long) Q16_ONE) << 16;
        return (int) (scaled / value);
    }

    private static int entropyFromDeltaQ16(int deltaQ16) {
        int x = deltaQ16 > 0 ? deltaQ16 + Q16_ONE : Q16_ONE;
        int bits = 0;
        while (x > 1) {
            x >>= 1;
            bits++;
        }
        return bits * 45426;
    }

    private static void applyAdaptiveWeights(State state) {
        int eta = Q16_ONE / 100;
        if (state.deltaQ16 > Q16_ONE) {
            state.weightsQ16[1] += eta;
            state.weightsQ16[3] += eta;
        } else {
            state.weightsQ16[0] += eta;
        }
        normalizeWeights(state.weightsQ16);
    }

    private static void normalizeWeights(int[] weightsQ16) {
        long sum = 0;
        for (int i = 0; i < VECTRA_WEIGHTS; i++) {
            if (weightsQ16[i] < 0) {
                weightsQ16[i] = 0;
            }
            sum += weightsQ16[i];
        }

        if (sum == 0) {
            for (int i = 0; i < VECTRA_WEIGHTS; i++) {
                weightsQ16[i] = Q16_ONE / VECTRA_WEIGHTS;
            }
            return;
        }

        for (int i = 0; i < VECTRA_WEIGHTS; i++) {
            weightsQ16[i] = (int) (((long) weightsQ16[i] << 16) / sum);
        }
    }

    private static final class State {
        private final int[] x = new int[VECTRA_DIM];
        private final int[] prev = new int[VECTRA_DIM];
        private final int[] prev2 = new int[VECTRA_DIM];
        private final int[] weightsQ16 = new int[VECTRA_WEIGHTS];
        private final int[] lane = new int[VECTRA_LANES];
        private final int[] aux = new int[VECTRA_LANES];
        private final int[] pulse = new int[VECTRA_LANES];

        private int cycle;
        private long tick;
        private int phaseQ16;
        private int deltaQ16;
        private int coherenceQ16;
        private int entropyQ16;

        private static State initial() {
            State state = new State();
            state.coherenceQ16 = Q16_HALF;
            state.entropyQ16 = Q16_HALF;
            for (int i = 0; i < VECTRA_DIM; i++) {
                state.x[i] = (i + 1) * (Q16_ONE / 10);
            }
            for (int i = 0; i < VECTRA_WEIGHTS; i++) {
                state.weightsQ16[i] = Q16_ONE / VECTRA_WEIGHTS;
            }
            return state;
        }
    }
}

package org.gradle.vectra.support

import groovy.transform.CompileStatic

import java.io.ByteArrayOutputStream

@CompileStatic
final class VectraModel {
    static final int Q16_ONE = 65536
    static final int Q16_HALF = 32768
    static final int Q16_EPSILON = 64
    static final int Q16_LN2 = 45426
    static final int VECTRA_DIM = 3
    static final int VECTRA_LANES = 32
    static final int VECTRA_WEIGHTS = 5
    static final int VECTRA_PHASE_CYCLES = 56
    static final int GOLDEN_RATIO_MIX = (int) 0x9E3779B9
    static final int PATCH_MIX = (int) 0x045D9F3B

    static final int[] SIN56_Q16 = [
        0, 7341, 14589, 21654, 28447, 34883, 40880, 46362,
        51260, 55513, 59067, 61877, 63908, 65134, 65536, 65134,
        63908, 61877, 59067, 55513, 51260, 46362, 40880, 34883,
        28447, 21654, 14589, 7341, 0, -7341, -14589, -21654,
        -28447, -34883, -40880, -46362, -51260, -55513, -59067, -61877,
        -63908, -65134, -65536, -65134, -63908, -61877, -59067, -55513,
        -51260, -46362, -40880, -34883, -28447, -21654, -14589, -7341
    ] as int[]

    static VectraState newState() {
        VectraState state = new VectraState()
        state.coherenceQ16 = Q16_HALF
        state.entropyQ16 = Q16_HALF
        for (int i = 0; i < VECTRA_DIM; i++) {
            state.x[i] = (i + 1) * (Q16_ONE.intdiv(10))
        }
        for (int i = 0; i < VECTRA_WEIGHTS; i++) {
            state.weightsQ16[i] = Q16_ONE.intdiv(VECTRA_WEIGHTS)
        }
        return state
    }

    static int q16Mul(int a, int b) {
        return (int) (((long) a * (long) b) >> 16)
    }

    static int reciprocalQ16(int value) {
        int magnitude = Math.abs(value)
        if (magnitude <= Q16_EPSILON) {
            return 0
        }
        long scaled = ((long) Q16_ONE) << 16
        return (int) (scaled / value)
    }

    static int entropyFromDeltaQ16(int deltaQ16) {
        long x = (long) (deltaQ16 > 0 ? deltaQ16 + Q16_ONE : Q16_ONE)
        int bits = 0
        while (x > 1L) {
            x >>>= 1
            bits++
        }
        return bits * Q16_LN2
    }

    static int isqrt64(long n) {
        long x = n
        long y = (x + 1L) >> 1
        while (y < x) {
            x = y
            y = (x + (n / x)) >> 1
        }
        return (int) x
    }

    static void normalizeWeightsQ16(int[] weightsQ16) {
        long sum = 0L
        for (int i = 0; i < VECTRA_WEIGHTS; i++) {
            if (weightsQ16[i] < 0) {
                weightsQ16[i] = 0
            }
            sum += weightsQ16[i]
        }
        if (sum == 0L) {
            for (int i = 0; i < VECTRA_WEIGHTS; i++) {
                weightsQ16[i] = Q16_ONE.intdiv(VECTRA_WEIGHTS)
            }
            return
        }
        for (int i = 0; i < VECTRA_WEIGHTS; i++) {
            weightsQ16[i] = (int) (((long) weightsQ16[i] << 16) / sum)
        }
    }

    static void applyAdaptiveWeights(VectraState state) {
        int eta = Q16_ONE.intdiv(100)
        if (state.deltaQ16 > Q16_ONE) {
            state.weightsQ16[1] += eta
            state.weightsQ16[3] += eta
        } else {
            state.weightsQ16[0] += eta
        }
        normalizeWeightsQ16(state.weightsQ16)
    }

    static void pulseMix(int[] lane, int[] aux, int[] pulse) {
        for (int i = 0; i < VECTRA_LANES; i++) {
            int mixed = Integer.rotateLeft((lane[i] ^ aux[i]) + pulse[i], 7)
            lane[i] = mixed
        }
    }

    static byte[] step(VectraState state, byte[] inputBytes) {
        int[] next = new int[VECTRA_DIM]
        for (int i = 0; i < VECTRA_DIM; i++) {
            int d = state.x[i] - state.prev[i]
            int a = state.x[i] + state.prev[i] + state.prev2[i]
            int phaseOffset = (state.cycle + (i * 7)) % VECTRA_PHASE_CYCLES
            int phaseWave = SIN56_Q16[phaseOffset]
            int r = q16Mul(state.prev[i] + state.prev2[i] + state.phaseQ16, phaseWave)
            int inv = reciprocalQ16(state.x[i])

            next[i] = q16Mul(state.weightsQ16[0], d) +
                q16Mul(state.weightsQ16[1], a) +
                q16Mul(state.weightsQ16[2], r) +
                q16Mul(state.weightsQ16[3], inv) +
                q16Mul(state.weightsQ16[4], state.deltaQ16)

            state.aux[i] = d ^ a ^ r ^ inv
            int sample = i < inputBytes.length ? Byte.toUnsignedInt(inputBytes[i]) : 0
            state.pulse[i] ^= sample
        }

        for (int i = VECTRA_DIM; i < VECTRA_LANES; i++) {
            int sample = i < inputBytes.length ? Byte.toUnsignedInt(inputBytes[i]) : 0
            state.aux[i] = state.lane[i] ^ sample ^ state.cycle
            state.pulse[i] ^= sample * GOLDEN_RATIO_MIX
        }

        pulseMix(state.lane, state.aux, state.pulse)

        long deltaAcc = 0L
        for (int i = 0; i < VECTRA_DIM; i++) {
            int diff = next[i] - state.x[i]
            deltaAcc += (long) diff * (long) diff
            state.prev2[i] = state.prev[i]
            state.prev[i] = state.x[i]
            state.x[i] = next[i]
            state.lane[i] = next[i]
        }

        state.deltaQ16 = isqrt64(deltaAcc)
        state.coherenceQ16 = (int) ((((long) Q16_ONE) << 16) / (Q16_ONE + Math.max(state.deltaQ16, 0)))
        state.entropyQ16 = entropyFromDeltaQ16(state.deltaQ16)
        state.cycle = (state.cycle + 1) % VECTRA_PHASE_CYCLES
        state.phaseQ16 = state.cycle * (Q16_ONE.intdiv(VECTRA_PHASE_CYCLES))
        applyAdaptiveWeights(state)

        byte[] output = new byte[VECTRA_LANES]
        for (int i = 0; i < VECTRA_LANES; i++) {
            output[i] = (byte) (state.lane[i] & 0xFF)
        }
        state.tick++
        return output
    }

    static byte[] collapse(VectraState state) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(32)
        writeLeInt(out, state.x[0])
        writeLeInt(out, state.x[1])
        writeLeInt(out, state.x[2])
        writeLeInt(out, state.deltaQ16)
        writeLeInt(out, state.coherenceQ16)
        writeLeInt(out, state.entropyQ16)
        writeLeInt(out, state.cycle)
        writeLeInt(out, (int) state.tick)
        return out.toByteArray()
    }

    static void inject(VectraState state, byte[] patch) {
        for (int i = 0; i < patch.length; i++) {
            int laneIndex = i % VECTRA_LANES
            int shift = (i & 0x3) << 3
            int v = Byte.toUnsignedInt(patch[i])
            state.lane[laneIndex] ^= (v << shift)
            state.pulse[laneIndex] ^= v * PATCH_MIX
        }
        state.x[0] ^= state.lane[0]
        state.x[1] ^= state.lane[1]
        state.x[2] ^= state.lane[2]
    }

    static byte[] serializeToroidalState(VectraState state) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4 + 8 + VECTRA_LANES * 12)
        writeLeInt(out, state.cycle)
        writeLeLong(out, state.tick)
        for (int i = 0; i < VECTRA_LANES; i++) {
            writeLeInt(out, state.lane[i])
            writeLeInt(out, state.aux[i])
            writeLeInt(out, state.pulse[i])
        }
        return out.toByteArray()
    }

    static VectraState deserializeToroidalState(byte[] bytes) {
        if (bytes.length != (4 + 8 + VECTRA_LANES * 12)) {
            throw new IllegalArgumentException("Unexpected toroidal payload length: ${bytes.length}")
        }
        VectraState state = newState()
        int cursor = 0
        state.cycle = readLeInt(bytes, cursor); cursor += 4
        state.tick = readLeLong(bytes, cursor); cursor += 8
        for (int i = 0; i < VECTRA_LANES; i++) {
            state.lane[i] = readLeInt(bytes, cursor); cursor += 4
            state.aux[i] = readLeInt(bytes, cursor); cursor += 4
            state.pulse[i] = readLeInt(bytes, cursor); cursor += 4
        }
        return state
    }

    static double q16ToDouble(int q16) {
        return q16 / 65536d
    }

    static double entropyFromDeltaDouble(double delta) {
        return Math.log(1.0d + Math.max(delta, 0.0d))
    }

    private static void writeLeInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF)
        out.write((value >>> 8) & 0xFF)
        out.write((value >>> 16) & 0xFF)
        out.write((value >>> 24) & 0xFF)
    }

    private static void writeLeLong(ByteArrayOutputStream out, long value) {
        for (int i = 0; i < 8; i++) {
            out.write((int) ((value >>> (8 * i)) & 0xFF))
        }
    }

    private static int readLeInt(byte[] bytes, int offset) {
        return (Byte.toUnsignedInt(bytes[offset])) |
            (Byte.toUnsignedInt(bytes[offset + 1]) << 8) |
            (Byte.toUnsignedInt(bytes[offset + 2]) << 16) |
            (Byte.toUnsignedInt(bytes[offset + 3]) << 24)
    }

    private static long readLeLong(byte[] bytes, int offset) {
        long result = 0
        for (int i = 0; i < 8; i++) {
            result |= ((long) Byte.toUnsignedInt(bytes[offset + i])) << (8 * i)
        }
        return result
    }

    @CompileStatic
    static final class VectraState {
        int cycle
        long tick
        int phaseQ16
        int deltaQ16
        int coherenceQ16
        int entropyQ16
        int[] x = new int[VECTRA_DIM]
        int[] prev = new int[VECTRA_DIM]
        int[] prev2 = new int[VECTRA_DIM]
        int[] weightsQ16 = new int[VECTRA_WEIGHTS]
        int[] lane = new int[VECTRA_LANES]
        int[] aux = new int[VECTRA_LANES]
        int[] pulse = new int[VECTRA_LANES]

        VectraState copy() {
            VectraState copy = new VectraState()
            copy.cycle = cycle
            copy.tick = tick
            copy.phaseQ16 = phaseQ16
            copy.deltaQ16 = deltaQ16
            copy.coherenceQ16 = coherenceQ16
            copy.entropyQ16 = entropyQ16
            System.arraycopy(x, 0, copy.x, 0, VECTRA_DIM)
            System.arraycopy(prev, 0, copy.prev, 0, VECTRA_DIM)
            System.arraycopy(prev2, 0, copy.prev2, 0, VECTRA_DIM)
            System.arraycopy(weightsQ16, 0, copy.weightsQ16, 0, VECTRA_WEIGHTS)
            System.arraycopy(lane, 0, copy.lane, 0, VECTRA_LANES)
            System.arraycopy(aux, 0, copy.aux, 0, VECTRA_LANES)
            System.arraycopy(pulse, 0, copy.pulse, 0, VECTRA_LANES)
            return copy
        }
    }
}

package org.gradle.vectra.engine;

import java.nio.ByteBuffer;

/**
 * Minimal Vectra engine contract used by the native bridge.
 */
public interface VectraEngine {
    /**
     * Initializes engine state with caller-owned preallocated buffers.
     */
    long init(ByteBuffer stateBuffer, ByteBuffer scratchBuffer);

    /**
     * Applies one deterministic transition step.
     */
    int step(long engineHandle, ByteBuffer inputBuffer, ByteBuffer outputBuffer);

    /**
     * Collapses the current state into the provided output buffer.
     */
    int collapse(long engineHandle, ByteBuffer outputBuffer);

    /**
     * Injects deltas directly into the active state.
     */
    int inject(long engineHandle, ByteBuffer patchBuffer);
}

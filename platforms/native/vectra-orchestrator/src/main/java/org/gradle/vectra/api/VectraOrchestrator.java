package org.gradle.vectra.api;

/**
 * Public entry point for Vectra orchestration integration.
 */
public interface VectraOrchestrator {
    /**
     * Returns a stable identifier for this orchestrator implementation.
     */
    String id();
}

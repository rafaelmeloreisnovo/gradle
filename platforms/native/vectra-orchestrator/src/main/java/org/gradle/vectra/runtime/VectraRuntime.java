package org.gradle.vectra.runtime;

import org.gradle.vectra.engine.VectraEngine;

/**
 * Entry point for capability detection, backend selection and decision report persistence.
 */
public class VectraRuntime {

    private final VectraEngine javaPureEngine = new JavaPureVectraEngine();

    private final CapabilityDetector capabilityDetector;
    private final BackendSelector backendSelector;
    private final CapabilityDecisionReportWriter reportWriter;

    public VectraRuntime() {
        this(new CapabilityDetector(), new BackendSelector(), new CapabilityDecisionReportWriter());
    }

    VectraRuntime(CapabilityDetector capabilityDetector, BackendSelector backendSelector, CapabilityDecisionReportWriter reportWriter) {
        this.capabilityDetector = capabilityDetector;
        this.backendSelector = backendSelector;
        this.reportWriter = reportWriter;
    }

    public VectraEngine resolveEngine(BackendSelector.SelectionDecision decision) {
        if (decision.getBackend() == BackendSelector.Backend.JAVA_PURE) {
            return javaPureEngine;
        }
        // Native engine wiring will be provided by JNI/Panama bridge integration.
        return javaPureEngine;
    }

    public BackendSelector.SelectionDecision selectBackendForCurrentBuild() {
        CapabilityReport capabilityReport = capabilityDetector.detect();
        BackendSelector.SelectionDecision decision = backendSelector.select(capabilityReport);
        reportWriter.write(capabilityReport, decision);
        return decision;
    }
}

package org.gradle.vectra.runtime;

/**
 * Entry point for capability detection, backend selection and decision report persistence.
 */
public class VectraRuntime {

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

    public BackendSelector.SelectionDecision selectBackendForCurrentBuild() {
        CapabilityReport capabilityReport = capabilityDetector.detect();
        BackendSelector.SelectionDecision decision = backendSelector.select(capabilityReport);
        reportWriter.write(capabilityReport, decision);
        return decision;
    }
}

package com.policyguard.service.gate;

/**
 * The outcome of a {@link ConfidenceGate} evaluation, combining the binary
 * {@link GateDecision} with a human-readable reason for audit logging.
 */
public record GateOutcome(GateDecision decision, String reason) {}

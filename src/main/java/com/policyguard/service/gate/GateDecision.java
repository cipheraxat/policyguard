package com.policyguard.service.gate;

/** Decision returned by the {@link ConfidenceGate}. */
public enum GateDecision {
    /** Confidence is acceptable and all citations are verifiable — pass the answer through. */
    ANSWER,
    /** Confidence is too low or a citation is unverifiable — refuse to answer. */
    REFUSE
}

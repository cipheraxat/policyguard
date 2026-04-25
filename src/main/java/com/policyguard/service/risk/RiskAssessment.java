package com.policyguard.service.risk;

/**
 * The result of classifying a prompt through the {@link RiskClassifier}.
 *
 * @param riskLevel      {@code "HIGH"} if any risk pattern matched; {@code "LOW"} otherwise.
 * @param category       the matched risk category name (e.g. {@code "regulatory_interpretation"}),
 *                       or {@code null} when {@code riskLevel} is {@code "LOW"}.
 * @param requiresReview {@code true} when the prompt must be routed to a human reviewer.
 */
public record RiskAssessment(String riskLevel, String category, boolean requiresReview) {}

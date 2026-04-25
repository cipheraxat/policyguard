package com.policyguard.service.query;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.policyguard.config.PolicyguardProperties;
import com.policyguard.domain.Answer;
import com.policyguard.domain.Query;
import com.policyguard.repository.AnswerRepository;
import com.policyguard.repository.QueryRepository;
import com.policyguard.service.audit.AuditLogService;
import com.policyguard.service.citation.Citation;
import com.policyguard.service.citation.CitationGenerator;
import com.policyguard.service.citation.CitationResult;
import com.policyguard.service.gate.ConfidenceGate;
import com.policyguard.service.gate.GateDecision;
import com.policyguard.service.gate.GateOutcome;
import com.policyguard.service.pii.PiiRedactionGateway;
import com.policyguard.service.pii.RedactionResult;
import com.policyguard.service.retrieval.HybridRetriever;
import com.policyguard.service.retrieval.RetrievalHit;
import com.policyguard.service.review.ReviewQueueService;
import com.policyguard.domain.ReviewQueueItem;
import com.policyguard.service.risk.RiskAssessment;
import com.policyguard.service.risk.RiskClassifier;

@Service
public class QueryService {

    private final PiiRedactionGateway piiRedactionGateway;
    private final RiskClassifier riskClassifier;
    private final HybridRetriever hybridRetriever;
    private final CitationGenerator citationGenerator;
    private final ConfidenceGate confidenceGate;
    private final ReviewQueueService reviewQueueService;
    private final AuditLogService auditLogService;
    private final QueryRepository queryRepository;
    private final AnswerRepository answerRepository;
    private final PolicyguardProperties properties;

    public QueryService(PiiRedactionGateway piiRedactionGateway,
                        RiskClassifier riskClassifier,
                        HybridRetriever hybridRetriever,
                        CitationGenerator citationGenerator,
                        ConfidenceGate confidenceGate,
                        ReviewQueueService reviewQueueService,
                        AuditLogService auditLogService,
                        QueryRepository queryRepository,
                        AnswerRepository answerRepository,
                        PolicyguardProperties properties) {
        this.piiRedactionGateway = piiRedactionGateway;
        this.riskClassifier = riskClassifier;
        this.hybridRetriever = hybridRetriever;
        this.citationGenerator = citationGenerator;
        this.confidenceGate = confidenceGate;
        this.reviewQueueService = reviewQueueService;
        this.auditLogService = auditLogService;
        this.queryRepository = queryRepository;
        this.answerRepository = answerRepository;
        this.properties = properties;
    }

    /**
     * Full pipeline: redact → risk classify → retrieve → generate → gate.
     * Wrapped in a single transaction; audit calls run in REQUIRES_NEW so they
     * survive any outer rollback.
     */
    @Transactional
    public QueryOutcome handle(String question, String userId, Map<String, Object> filters) {
        String queryId = "qry-" + UUID.randomUUID().toString().substring(0, 12);
        int originalLength = question == null ? 0 : question.length();

        // Step 1: PII redaction
        RedactionResult redactionResult = piiRedactionGateway.redact(question);
        String redactedText = redactionResult.redactedText();

        // Step 2: Persist Query (status=pending so FK constraints resolve)
        Query query = new Query();
        query.setQueryId(queryId);
        query.setOriginalPrompt(redactedText);
        query.setRedacted(redactionResult.wasRedacted());
        query.setRedactionLog(Map.of("entities", redactionResult.entitiesFound()));
        query.setStatus("pending");
        queryRepository.save(query);

        // Step 3: Audit prompt_received (raw length only — no PII)
        auditLogService.append(queryId, "prompt_received", userId,
                Map.of("raw_length", originalLength),
                Map.of("redacted_prompt", redactedText, "was_redacted", redactionResult.wasRedacted()));

        // Step 4: Audit pii_redaction
        auditLogService.append(queryId, "pii_redaction", "system",
                Map.of(),
                Map.of("entities_found", redactionResult.entitiesFound(),
                       "count", redactionResult.entitiesFound().size()));

        // Step 5: Risk classification
        RiskAssessment risk = riskClassifier.classify(redactedText);
        auditLogService.append(queryId, "risk_classification", "system",
                Map.of(),
                Map.of("risk_level", risk.riskLevel(), "category",
                       risk.category() != null ? risk.category() : "none"));

        // Step 6: Escalate if high-risk
        if (risk.requiresReview()) {
            ReviewQueueItem item = reviewQueueService.enqueue(queryId, risk.category(), risk.riskLevel());

            query.setStatus("escalated");
            queryRepository.save(query);

            auditLogService.append(queryId, "escalation", "system",
                    Map.of(),
                    Map.of("review_item_id", item.getItemId(), "reason", risk.category()));
            auditLogService.append(queryId, "response_sent", "system",
                    Map.of(),
                    Map.of("status", "escalated"));

            return new QueryOutcome.Escalated(
                    queryId,
                    risk.category() + " detected",
                    item.getItemId(),
                    "This question has been routed to the compliance team for review.");
        }

        // Step 7: Retrieval
        return runRetrievalPipeline(queryId, redactedText, filters, "system", query);
    }

    /**
     * Re-runs the retrieval/generation/gate portion for an already-escalated query
     * after a reviewer approves it. Skips redaction and risk classification.
     */
    @Transactional
    public QueryOutcome handleApproved(String queryId, String originalPrompt, String reviewerId) {
        Query query = queryRepository.findByQueryId(queryId)
                .orElseThrow(() -> new IllegalArgumentException("Query not found: " + queryId));
        String actor = "reviewer-" + reviewerId;
        return runRetrievalPipeline(queryId, originalPrompt, null, actor, query);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private QueryOutcome runRetrievalPipeline(String queryId, String text,
                                               Map<String, Object> filters,
                                               String actor, Query query) {
        List<RetrievalHit> hits = hybridRetriever.retrieve(
                text, filters, properties.getRetrieval().getTopK());

        double topScore = hits.isEmpty() ? 0.0 : hits.get(0).score();
        auditLogService.append(queryId, "retrieval", "system",
                Map.of(),
                Map.of("hits", hits.size(), "top_score", topScore));

        if (hits.isEmpty()) {
            persistAnswer(queryId, "I cannot answer this question based on the available policy documents.",
                    Collections.emptyList(), 0.0, Collections.emptyList());
            query.setStatus("refused");
            queryRepository.save(query);

            auditLogService.append(queryId, "generation", "system",
                    Map.of(),
                    Map.of("skipped", true, "reason", "no_hits"));
            auditLogService.append(queryId, "response_sent", actor,
                    Map.of(),
                    Map.of("status", "refused"));

            return new QueryOutcome.Refused(queryId, "No relevant documents found",
                    "I cannot answer this question based on the available policy documents.");
        }

        CitationResult cr = citationGenerator.generate(text, hits);
        boolean isExplicit = confidenceGate.isExplicitRefusal(cr.responseText());

        auditLogService.append(queryId, "generation", "system",
                Map.of(),
                Map.of("confidence", cr.confidence(),
                       "citation_count", cr.citations().size(),
                       "explicit_refusal", isExplicit));

        if (isExplicit) {
            persistAnswer(queryId, cr.responseText(),
                    Collections.emptyList(), cr.confidence(), hits);
            query.setStatus("refused");
            queryRepository.save(query);

            auditLogService.append(queryId, "response_sent", actor,
                    Map.of(),
                    Map.of("status", "refused"));

            return new QueryOutcome.Refused(queryId,
                    "Model declined to answer based on excerpts",
                    cr.responseText());
        }

        GateOutcome gate = confidenceGate.evaluate(cr.confidence(), cr.citations(), cr.hits());

        if (gate.decision() == GateDecision.REFUSE) {
            persistAnswer(queryId, cr.responseText(),
                    Collections.emptyList(), cr.confidence(), hits);
            query.setStatus("refused");
            queryRepository.save(query);

            auditLogService.append(queryId, "response_sent", actor,
                    Map.of(),
                    Map.of("status", "refused"));

            return new QueryOutcome.Refused(queryId, gate.reason(),
                    "I cannot answer this question based on the available policy documents.");
        }

        // ANSWER
        persistAnswer(queryId, cr.responseText(), cr.citations(), cr.confidence(), hits);
        query.setStatus("answered");
        queryRepository.save(query);

        auditLogService.append(queryId, "response_sent", actor,
                Map.of(),
                Map.of("status", "answered", "citation_count", cr.citations().size()));

        return new QueryOutcome.Answered(queryId, cr.responseText(), cr.citations(),
                cr.confidence(), hits.size());
    }

    private void persistAnswer(String queryId, String responseText,
                                List<Citation> citations, double confidence,
                                List<RetrievalHit> hits) {
        Answer answer = new Answer();
        answer.setQueryId(queryId);
        answer.setResponseText(responseText);
        answer.setCitations(serializeCitations(citations));
        answer.setConfidenceScore(confidence);
        answer.setRetrievalHits(serializeHits(hits));
        answer.setGeneratedAt(OffsetDateTime.now());
        answerRepository.save(answer);
    }

    private List<Map<String, Object>> serializeCitations(List<Citation> citations) {
        if (citations == null) return Collections.emptyList();
        return citations.stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("chunkId", c.chunkId());
                    m.put("documentId", c.documentId());
                    m.put("paragraphRef", c.paragraphRef());
                    m.put("textSnippet", c.textSnippet());
                    return m;
                })
                .toList();
    }

    private List<Map<String, Object>> serializeHits(List<RetrievalHit> hits) {
        if (hits == null) return Collections.emptyList();
        return hits.stream()
                .map(h -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("chunk_id", h.chunkId());
                    m.put("document_id", h.documentId());
                    m.put("paragraph_ref", h.paragraphRef());
                    m.put("score", h.score());
                    String snippet = h.text();
                    if (snippet != null && snippet.length() > 200) {
                        snippet = snippet.substring(0, 200);
                    }
                    m.put("text_snippet", snippet);
                    return m;
                })
                .toList();
    }
}

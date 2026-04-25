package com.policyguard.service.citation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.policyguard.config.PolicyguardProperties;
import com.policyguard.service.retrieval.RetrievalHit;

/**
 * Generates a cited answer by prompting the LLM with retrieved document excerpts
 * and parsing the resulting {@code [Doc: X, Para: Y]} citation markers.
 */
@Service
public class CitationGenerator {

    private static final Logger log = LoggerFactory.getLogger(CitationGenerator.class);

    /** Regex that matches citation tags like {@code [Doc: POL-001, Para: Section 3.2, Paragraph 1]}. */
    private static final Pattern CITATION_PATTERN =
            Pattern.compile("\\[Doc:\\s*([^,\\]]+?)\\s*,\\s*Para:\\s*([^\\]]+?)\\s*\\]");

    private static final int SNIPPET_MAX_LENGTH = 200;

    private final ChatClient chatClient;
    private final PolicyguardProperties properties;

    public CitationGenerator(ChatClient chatClient, PolicyguardProperties properties) {
        this.chatClient = chatClient;
        this.properties = properties;
    }

    /**
     * Calls the LLM with the retrieved excerpts and returns the response together
     * with parsed citations and a confidence score.
     *
     * @param query the (PII-redacted) user question
     * @param hits  retrieval hits that form the context window
     * @return {@link CitationResult} containing the response text, parsed citations,
     *         confidence score (mean of top-3 hit scores), and the input hits
     */
    public CitationResult generate(String query, List<RetrievalHit> hits) {
        String formattedHits = formatHits(hits);

        String prompt = """
                Answer the compliance question using ONLY the provided document excerpts.
                For each claim, cite the source using [Doc: {document_id}, Para: {paragraph_ref}].
                If the excerpts do not contain enough information, respond with EXACTLY:
                "I cannot answer this based on the available policy documents."

                Question: %s

                Excerpts:
                %s
                """.formatted(query, formattedHits);

        String responseText = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        List<Citation> citations = parseCitations(responseText, hits);
        double confidence = computeConfidence(hits);

        return new CitationResult(responseText, citations, confidence, hits);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String formatHits(List<RetrievalHit> hits) {
        StringBuilder sb = new StringBuilder();
        for (RetrievalHit hit : hits) {
            sb.append("[Doc: ").append(hit.documentId())
              .append(", Para: ").append(hit.paragraphRef())
              .append("] ").append(hit.text())
              .append('\n');
        }
        return sb.toString();
    }

    /**
     * Parses all {@code [Doc: X, Para: Y]} citation tags in the response.
     * Each match is verified against the retrieved hits (case-sensitive trim on
     * both documentId and paragraphRef).  If a match cannot be verified,
     * {@code chunkId} is set to {@code null} so the confidence gate can detect
     * hallucinated citations.
     */
    private List<Citation> parseCitations(String responseText, List<RetrievalHit> hits) {
        List<Citation> result = new ArrayList<>();
        Matcher matcher = CITATION_PATTERN.matcher(responseText);

        while (matcher.find()) {
            String docId   = matcher.group(1).strip();
            String paraRef = matcher.group(2).strip();

            RetrievalHit matched = hits.stream()
                    .filter(h -> h.documentId().equals(docId) && h.paragraphRef().equals(paraRef))
                    .findFirst()
                    .orElse(null);

            if (matched != null) {
                String snippet = matched.text().length() > SNIPPET_MAX_LENGTH
                        ? matched.text().substring(0, SNIPPET_MAX_LENGTH)
                        : matched.text();
                result.add(new Citation(matched.chunkId(), docId, paraRef, snippet));
            } else {
                log.debug("Unverifiable citation in LLM response: Doc={}, Para={}", docId, paraRef);
                result.add(new Citation(null, docId, paraRef, null));
            }
        }
        return result;
    }

    /**
     * Confidence = mean of the top-3 hit scores (semantic similarity), clamped to [0, 1].
     * Falls back to 0.0 when no hits are provided.
     */
    private static double computeConfidence(List<RetrievalHit> hits) {
        if (hits.isEmpty()) return 0.0;
        int count = Math.min(3, hits.size());
        double sum = 0.0;
        for (int i = 0; i < count; i++) {
            sum += hits.get(i).score();
        }
        double mean = sum / count;
        return Math.max(0.0, Math.min(1.0, mean));
    }
}

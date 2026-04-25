package com.policyguard.service.chunking;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.policyguard.config.PolicyguardProperties;

/**
 * Splits document text into paragraph-aware chunks with configurable
 * {@code maxChars} size and {@code overlap} between consecutive chunks.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Split on {@code \n\n+} to get raw paragraphs.</li>
 *   <li>Track the current section heading via regex.</li>
 *   <li>Number non-heading paragraphs 1..N within each section.</li>
 *   <li>If a paragraph fits within {@code maxChars}, emit it as one chunk.</li>
 *   <li>Otherwise slide a window of {@code maxChars} with stride
 *       {@code maxChars - overlap}, producing multiple chunks that each
 *       inherit the paragraph's ref.</li>
 * </ol>
 */
@Service
public class ChunkingService {

    /** Matches numeric section headers like "1", "3.2", "10.4.1  Title text" */
    private static final Pattern NUMERIC_HEADING =
            Pattern.compile("^\\s*(\\d+(?:\\.\\d+)*)(?:\\s+\\S.*)?$");

    /** Matches "Section 1", "Section 3.2" (case-insensitive) */
    private static final Pattern SECTION_KEYWORD_HEADING =
            Pattern.compile("^Section\\s+(\\d+(?:\\.\\d+)*)", Pattern.CASE_INSENSITIVE);

    private final int maxChars;
    private final int overlap;

    public ChunkingService(PolicyguardProperties properties) {
        PolicyguardProperties.Chunking cfg = properties.getChunking();
        this.maxChars = cfg.getMaxChars();
        this.overlap  = cfg.getOverlap();
    }

    /** Package-visible constructor for unit tests with explicit settings. */
    ChunkingService(int maxChars, int overlap) {
        this.maxChars = maxChars;
        this.overlap  = overlap;
    }

    /**
     * Chunk {@code text} into paragraph-aware segments.
     *
     * @param text the full document text (PII-redacted)
     * @return ordered list of chunks; empty if {@code text} is blank
     */
    public List<Chunk> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // Split on blank lines
        String[] rawParagraphs = text.split("\\n{2,}");

        List<Chunk> result = new ArrayList<>();
        String currentSection = null;
        int paraIndex = 0; // 1-based within the current section

        for (String raw : rawParagraphs) {
            String para = raw.strip();
            if (para.isEmpty()) continue;

            String detectedSection = detectSectionHeading(para);
            if (detectedSection != null) {
                currentSection = detectedSection;
                paraIndex = 0;
                // Section headings are short; emit them as a single chunk tagged Para 0
                // so the heading itself is searchable but doesn't consume a para slot.
                String ref = buildRef(currentSection, 0);
                result.addAll(splitToParts(para, ref));
                continue;
            }

            paraIndex++;
            String ref = buildRef(currentSection, paraIndex);
            result.addAll(splitToParts(para, ref));
        }

        return result;
    }

    // ── internals ─────────────────────────────────────────────────────────────

    /** Returns the section number string if {@code para} is a heading; null otherwise. */
    private static String detectSectionHeading(String para) {
        // "Section X.Y" keyword form
        Matcher m = SECTION_KEYWORD_HEADING.matcher(para);
        if (m.find()) {
            return m.group(1);
        }

        // Numeric form: the paragraph must be entirely one short heading line
        // (no more than ~120 chars, single line, starting with a digit sequence)
        if (!para.contains("\n") && para.length() <= 120) {
            Matcher nm = NUMERIC_HEADING.matcher(para);
            if (nm.matches()) {
                return nm.group(1);
            }
        }

        return null;
    }

    private static String buildRef(String section, int paraIndex) {
        if (section == null) {
            return "Paragraph " + paraIndex;
        }
        if (paraIndex == 0) {
            // Section heading itself
            return "Section " + section;
        }
        return "Section " + section + ", Paragraph " + paraIndex;
    }

    /**
     * Split {@code text} into parts of at most {@code maxChars}, with
     * {@code overlap} chars of context from the preceding part prepended to
     * each subsequent part.
     */
    private List<Chunk> splitToParts(String text, String ref) {
        if (text.length() <= maxChars) {
            return List.of(new Chunk(text, ref));
        }

        List<Chunk> parts = new ArrayList<>();
        int stride = Math.max(1, maxChars - overlap);
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            parts.add(new Chunk(text.substring(start, end), ref));
            start += stride;
        }

        return parts;
    }
}

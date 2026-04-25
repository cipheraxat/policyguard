package com.policyguard.service.chunking;

/**
 * A text chunk with a reference to the paragraph (and optionally section) it came from.
 *
 * @param text         the chunk text (may begin with overlap from the prior chunk)
 * @param paragraphRef e.g. {@code "Section 3.2, Paragraph 4"} or {@code "Paragraph 2"}
 */
public record Chunk(String text, String paragraphRef) {}

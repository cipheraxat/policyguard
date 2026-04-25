package com.policyguard.api.query.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CitationDto(
        @JsonProperty("chunk_id")    String chunkId,
        @JsonProperty("document_id") String documentId,
        @JsonProperty("paragraph_ref") String paragraphRef,
        @JsonProperty("text_snippet") String textSnippet
) {}

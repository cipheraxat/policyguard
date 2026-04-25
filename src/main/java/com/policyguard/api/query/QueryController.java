package com.policyguard.api.query;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.policyguard.api.query.dto.CitationDto;
import com.policyguard.api.query.dto.QueryAnsweredResponse;
import com.policyguard.api.query.dto.QueryEscalatedResponse;
import com.policyguard.api.query.dto.QueryRefusedResponse;
import com.policyguard.api.query.dto.QueryRequest;
import com.policyguard.service.citation.Citation;
import com.policyguard.service.query.QueryOutcome;
import com.policyguard.service.query.QueryService;

@RestController
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    @PostMapping("/api/query")
    public ResponseEntity<?> query(@Validated @RequestBody QueryRequest request) {
        QueryOutcome outcome = queryService.handle(
                request.question(),
                request.userId(),
                request.filters());

        return switch (outcome) {
            case QueryOutcome.Answered a -> ResponseEntity.ok(
                    new QueryAnsweredResponse(
                            a.queryId(),
                            "answered",
                            a.answer(),
                            toCitationDtos(a.citations()),
                            a.confidenceScore(),
                            a.retrievalHitsCount()));

            case QueryOutcome.Refused r -> ResponseEntity.ok(
                    new QueryRefusedResponse(
                            r.queryId(),
                            "refused",
                            r.reason(),
                            r.message()));

            case QueryOutcome.Escalated e -> ResponseEntity.status(HttpStatus.ACCEPTED).body(
                    new QueryEscalatedResponse(
                            e.queryId(),
                            "escalated",
                            e.reason(),
                            e.reviewItemId(),
                            e.message()));
        };
    }

    private List<CitationDto> toCitationDtos(List<Citation> citations) {
        if (citations == null) return List.of();
        return citations.stream()
                .map(c -> new CitationDto(c.chunkId(), c.documentId(), c.paragraphRef(), c.textSnippet()))
                .toList();
    }
}

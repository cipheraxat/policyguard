package com.policyguard.api.review;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.policyguard.api.review.dto.ResolveRequest;
import com.policyguard.api.review.dto.ResolveResponse;
import com.policyguard.api.review.dto.ReviewQueueItemDto;
import com.policyguard.api.review.dto.ReviewQueueResponse;
import com.policyguard.domain.Query;
import com.policyguard.domain.ReviewQueueItem;
import com.policyguard.repository.QueryRepository;
import com.policyguard.service.query.QueryOutcome;
import com.policyguard.service.query.QueryService;
import com.policyguard.service.review.ReviewQueueService;

@RestController
public class ReviewController {

    private final ReviewQueueService reviewQueueService;
    private final QueryRepository queryRepository;
    private final QueryService queryService;

    public ReviewController(ReviewQueueService reviewQueueService,
                            QueryRepository queryRepository,
                            QueryService queryService) {
        this.reviewQueueService = reviewQueueService;
        this.queryRepository = queryRepository;
        this.queryService = queryService;
    }

    @GetMapping("/api/review-queue")
    public ReviewQueueResponse listPending() {
        List<ReviewQueueItem> items = reviewQueueService.listPending();

        List<ReviewQueueItemDto> dtos = items.stream()
                .map(item -> {
                    String originalQuestion = queryRepository.findByQueryId(item.getQueryId())
                            .map(Query::getOriginalPrompt)
                            .orElse(null);
                    return new ReviewQueueItemDto(
                            item.getItemId(),
                            item.getQueryId(),
                            originalQuestion,
                            item.getEscalationReason(),
                            item.getRiskCategory(),
                            item.getStatus(),
                            item.getCreatedAt()
                    );
                })
                .toList();

        return new ReviewQueueResponse(dtos, dtos.size());
    }

    @PostMapping("/api/review/{itemId}/resolve")
    public ResponseEntity<ResolveResponse> resolve(
            @PathVariable String itemId,
            @RequestHeader(value = "X-Reviewer-Id", required = false) String reviewerHeader,
            @RequestBody ResolveRequest request) {

        // Trusted-header v1 auth: header must be present and match body reviewerId
        if (reviewerHeader == null || !reviewerHeader.equals(request.reviewerId())) {
            return ResponseEntity.status(401).build();
        }

        ReviewQueueItem item = reviewQueueService.resolve(
                itemId,
                request.reviewerId(),
                request.decision(),
                request.notes(),
                request.overrideAnswer()
        );

        String finalAnswer = switch (item.getStatus()) {
            case "overridden" -> item.getReviewerNotes();
            case "approved" -> {
                String originalPrompt = queryRepository.findByQueryId(item.getQueryId())
                        .map(Query::getOriginalPrompt)
                        .orElse(null);
                if (originalPrompt != null) {
                    QueryOutcome pipelineOutcome = queryService.handleApproved(
                            item.getQueryId(), originalPrompt, request.reviewerId());
                    yield switch (pipelineOutcome) {
                        case QueryOutcome.Answered a -> a.answer();
                        case QueryOutcome.Refused r  -> r.message();
                        case QueryOutcome.Escalated e -> null;
                    };
                } else {
                    yield null;
                }
            }
            default -> null;
        };

        return ResponseEntity.ok(new ResolveResponse(
                item.getItemId(),
                item.getStatus(),
                finalAnswer,
                item.getResolvedAt()
        ));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<String> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}

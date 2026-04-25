package com.policyguard.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.policyguard.domain.ReviewQueueItem;

public interface ReviewQueueItemRepository extends JpaRepository<ReviewQueueItem, UUID> {

    Optional<ReviewQueueItem> findByItemId(String itemId);

    List<ReviewQueueItem> findByStatus(String status);

    List<ReviewQueueItem> findByStatusOrderByCreatedAtAsc(String status);

    List<ReviewQueueItem> findByQueryId(String queryId);
}

from __future__ import annotations

import logging
from datetime import datetime, timezone
from uuid import uuid4

from sqlalchemy import select
from sqlalchemy.orm import Session

from policyguard.models import ReviewQueueItem

logger = logging.getLogger(__name__)
REDIS_KEY = "review:queue"
VALID_DECISIONS = {"approved", "rejected", "overridden"}


class ReviewQueueService:
    def __init__(self, session: Session, redis_client=None) -> None:
        self._session = session
        self._redis = redis_client

    def enqueue(self, query_id: str, escalation_reason: str, risk_category: str) -> ReviewQueueItem:
        item = ReviewQueueItem(
            item_id=f"rev-{uuid4()}",
            query_id=query_id,
            escalation_reason=escalation_reason,
            risk_category=risk_category,
            status="pending",
        )
        self._session.add(item)
        self._session.flush()
        if self._redis is not None:
            try:
                self._redis.rpush(REDIS_KEY, item.item_id)
            except Exception as e:
                logger.warning("Failed to push item %s to Redis: %s", item.item_id, e)
        return item

    def list_pending(self) -> list[ReviewQueueItem]:
        return list(
            self._session.scalars(
                select(ReviewQueueItem)
                .where(ReviewQueueItem.status == "pending")
                .order_by(ReviewQueueItem.created_at.asc())
            )
        )

    def resolve(
        self,
        item_id: str,
        reviewer_id: str,
        decision: str,
        notes: str | None,
        override_answer: str | None,
    ) -> ReviewQueueItem:
        item = self._session.scalars(
            select(ReviewQueueItem).where(ReviewQueueItem.item_id == item_id)
        ).first()
        if item is None:
            raise KeyError(f"Review queue item not found: {item_id}")
        if decision not in VALID_DECISIONS:
            raise ValueError(
                f"Invalid decision '{decision}'; must be one of: approved, rejected, overridden"
            )
        if item.status != "pending":
            raise RuntimeError("already resolved")
        if decision == "overridden" and (not override_answer or not override_answer.strip()):
            raise ValueError("overrideAnswer is required for 'overridden' decision")

        item.status = decision
        item.reviewer_id = reviewer_id
        if decision == "overridden":
            item.reviewer_notes = override_answer
            item.override_answer = override_answer
        else:
            item.reviewer_notes = notes
        item.resolved_at = datetime.now(timezone.utc)
        self._session.flush()

        if self._redis is not None:
            try:
                self._redis.lrem(REDIS_KEY, 0, item_id)
            except Exception as e:
                logger.warning("Failed to remove item %s from Redis: %s", item_id, e)
        return item

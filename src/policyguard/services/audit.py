from __future__ import annotations

from typing import Any
from uuid import uuid4

from sqlalchemy.orm import Session

from policyguard.models import AuditLog


class AuditLogService:
    def __init__(self, session: Session) -> None:
        self._session = session

    def append(
        self,
        query_id: str,
        event_type: str,
        actor: str,
        input_data: dict[str, Any] | None = None,
        output_data: dict[str, Any] | None = None,
    ) -> AuditLog:
        # Commit in a nested/independent fashion: flush immediately so audits survive
        log = AuditLog(
            log_id=f"aud-{uuid4()}",
            query_id=query_id,
            event_type=event_type,
            actor=actor,
            input_data=input_data or {},
            output_data=output_data or {},
        )
        self._session.add(log)
        self._session.flush()
        return log

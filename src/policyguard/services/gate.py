from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from policyguard.config import Settings
from policyguard.services.citation import Citation
from policyguard.services.rrf import RetrievalHit

EXPLICIT_REFUSAL_TEXT = "I cannot answer this based on the available policy documents."


class GateDecision(str, Enum):
    ANSWER = "ANSWER"
    REFUSE = "REFUSE"


@dataclass
class GateOutcome:
    decision: GateDecision
    reason: str


class ConfidenceGate:
    def __init__(self, settings: Settings) -> None:
        self._threshold = settings.confidence_threshold

    def evaluate(
        self,
        confidence: float,
        citations: list[Citation],
        hits: list[RetrievalHit],
    ) -> GateOutcome:
        if confidence < self._threshold:
            return GateOutcome(
                GateDecision.REFUSE,
                f"Retrieval confidence below threshold ({confidence} < {self._threshold})",
            )
        if any(c.chunk_id is None for c in citations):
            return GateOutcome(
                GateDecision.REFUSE,
                "Citations could not be verified against retrieved chunks",
            )
        return GateOutcome(GateDecision.ANSWER, "Confidence and citations verified")

    def is_explicit_refusal(self, text: str | None) -> bool:
        return text is not None and text.strip() == EXPLICIT_REFUSAL_TEXT

from __future__ import annotations

import re
from dataclasses import dataclass

from policyguard.config import Settings


@dataclass
class RiskAssessment:
    risk_level: str
    category: str | None
    requires_review: bool


class RiskClassifier:
    def __init__(self, settings: Settings) -> None:
        self._patterns: list[tuple[str, re.Pattern[str]]] = []
        for cfg in settings.risk_patterns or []:
            try:
                self._patterns.append(
                    (cfg["category"], re.compile(cfg["regex"], re.IGNORECASE))
                )
            except re.error:
                continue

    def classify(self, prompt: str | None) -> RiskAssessment:
        if prompt is None:
            return RiskAssessment("LOW", None, False)
        for category, pattern in self._patterns:
            if pattern.search(prompt):
                return RiskAssessment("HIGH", category, True)
        return RiskAssessment("LOW", None, False)

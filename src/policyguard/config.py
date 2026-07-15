from __future__ import annotations

from functools import lru_cache
from typing import Any

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class RiskPattern(BaseSettings):
    category: str
    regex: str


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        env_nested_delimiter="__",
    )

    profile: str = Field(default="stub", alias="POLICYGUARD_PROFILE")

    database_url: str = Field(
        default="postgresql+psycopg://policyguard:policyguard@localhost:5432/policyguard",
        alias="DATABASE_URL",
    )
    redis_url: str = Field(default="redis://localhost:6379/0", alias="REDIS_URL")

    # Chat / embeddings
    chat_base_url: str = Field(default="http://localhost:1234/v1", alias="POLICYGUARD_CHAT_BASE_URL")
    chat_api_key: str = Field(default="not-needed", alias="POLICYGUARD_CHAT_API_KEY")
    chat_model: str = Field(default="local-model", alias="POLICYGUARD_CHAT_MODEL")
    embedding_base_url: str = Field(default="http://localhost:1234/v1", alias="POLICYGUARD_EMBEDDING_BASE_URL")
    embedding_api_key: str = Field(default="not-needed", alias="POLICYGUARD_EMBEDDING_API_KEY")
    embedding_model: str = Field(
        default="text-embedding-nomic-embed-text-v1.5",
        alias="POLICYGUARD_EMBEDDING_MODEL",
    )
    embedding_dim: int = Field(default=1536, alias="POLICYGUARD_EMBEDDING_DIM")

    # Presidio
    presidio_base_url: str = Field(default="http://localhost:5002", alias="POLICYGUARD_PRESIDIO_BASE_URL")
    presidio_connect_timeout_ms: int = 1000
    presidio_read_timeout_ms: int = 5000
    # When True (default for stub/tests), skip HTTP and return no entities
    presidio_stub: bool = Field(default=False, alias="POLICYGUARD_PRESIDIO_STUB")

    # Reviewers — empty = dev mode
    reviewers_allowed_ids: list[str] = Field(default_factory=list, alias="POLICYGUARD_REVIEWERS_ALLOWED_IDS")

    # Ingestion / retrieval / confidence
    ingestion_max_file_bytes: int = 26_214_400
    retrieval_top_k: int = 5
    retrieval_rrf_k: int = 60
    confidence_threshold: float = 0.65
    chunking_max_chars: int = 1200
    chunking_overlap: int = 150

    risk_patterns: list[dict[str, str]] = Field(
        default_factory=lambda: [
            {
                "category": "regulatory_interpretation",
                "regex": r"\b(FINRA|SEC|GDPR|PCI-DSS|SOX|HIPAA)\b.*\b(interpret|apply|compliance requirement)\b",
            },
            {
                "category": "customer_data_exposure",
                "regex": r"\b(customer|client|user)\b.*\b(data|SSN|account|PII)\b.*\b(access|view|share|disclose)\b",
            },
            {
                "category": "policy_exception",
                "regex": r"\b(exception|waiver|override|bypass)\b.*\b(policy|control|requirement)\b",
            },
            {
                "category": "financial_advice",
                "regex": r"\b(advise|recommend|should we|can we)\b.*\b(invest|allocate|transfer|withdraw)\b",
            },
        ]
    )

    def apply_profile_defaults(self) -> None:
        """Adjust provider URLs / stub flags from POLICYGUARD_PROFILE."""
        profile = (self.profile or "stub").lower()
        if profile == "stub":
            self.presidio_stub = True
        elif profile == "lmstudio":
            self.chat_base_url = self.chat_base_url or "http://host.docker.internal:1234/v1"
            self.embedding_base_url = self.embedding_base_url or "http://host.docker.internal:1234/v1"
        elif profile == "openrouter":
            self.chat_base_url = "https://openrouter.ai/api/v1"
            # embeddings still local by default unless overridden


@lru_cache
def get_settings() -> Settings:
    s = Settings()
    s.apply_profile_defaults()
    return s

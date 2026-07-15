from __future__ import annotations

import hashlib
import math
import re
from typing import Protocol

from openai import OpenAI

from policyguard.config import Settings


class ChatProvider(Protocol):
    def complete(self, prompt: str) -> str: ...


class EmbeddingProvider(Protocol):
    def embed(self, text: str) -> list[float]: ...
    def embed_batch(self, texts: list[str]) -> list[list[float]]: ...


class StubEmbeddingProvider:
    def __init__(self, dim: int = 1536) -> None:
        self.dim = dim

    def embed(self, text: str) -> list[float]:
        digest = hashlib.sha256(text.encode("utf-8")).digest()
        raw = [(digest[i % len(digest)] & 0xFF) / 255.0 for i in range(self.dim)]
        return _l2_normalize(raw)

    def embed_batch(self, texts: list[str]) -> list[list[float]]:
        return [self.embed(t) for t in texts]


class StubChatProvider:
    _HIT = re.compile(r"\[Doc:\s*([^,\]]+),\s*Para:\s*([^\]]+)\]\s*(.+)")

    def complete(self, prompt: str) -> str:
        m = self._HIT.search(prompt)
        if m:
            doc_id, para_ref, text = m.group(1).strip(), m.group(2).strip(), m.group(3).strip()
            return f"Per the policy: {text} [Doc: {doc_id}, Para: {para_ref}]"
        return "I cannot answer this based on the available policy documents."


class OpenAICompatibleChat:
    def __init__(self, base_url: str, api_key: str, model: str) -> None:
        self._client = OpenAI(base_url=base_url, api_key=api_key)
        self._model = model

    def complete(self, prompt: str) -> str:
        resp = self._client.chat.completions.create(
            model=self._model,
            messages=[{"role": "user", "content": prompt}],
            temperature=0,
        )
        return (resp.choices[0].message.content or "").strip()


class OpenAICompatibleEmbedding:
    def __init__(self, base_url: str, api_key: str, model: str, dim: int) -> None:
        self._client = OpenAI(base_url=base_url, api_key=api_key)
        self._model = model
        self.dim = dim

    def embed(self, text: str) -> list[float]:
        resp = self._client.embeddings.create(model=self._model, input=text)
        return list(resp.data[0].embedding)

    def embed_batch(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        resp = self._client.embeddings.create(model=self._model, input=texts)
        by_idx = {d.index: list(d.embedding) for d in resp.data}
        return [by_idx[i] for i in range(len(texts))]


def build_providers(settings: Settings) -> tuple[ChatProvider, EmbeddingProvider]:
    profile = (settings.profile or "stub").lower()
    if profile == "stub":
        return StubChatProvider(), StubEmbeddingProvider(settings.embedding_dim)

    chat = OpenAICompatibleChat(settings.chat_base_url, settings.chat_api_key, settings.chat_model)
    embed = OpenAICompatibleEmbedding(
        settings.embedding_base_url,
        settings.embedding_api_key,
        settings.embedding_model,
        settings.embedding_dim,
    )
    return chat, embed


def _l2_normalize(v: list[float]) -> list[float]:
    norm = math.sqrt(sum(x * x for x in v))
    if norm == 0:
        return v
    return [x / norm for x in v]

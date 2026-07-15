from __future__ import annotations

from policyguard.providers import StubChatProvider


def test_stub_chat_with_excerpt():
    chat = StubChatProvider()
    prompt = (
        "Question: x\n\nExcerpts:\n"
        "[Doc: POL-TEST-001, Para: Section 4.1, Paragraph 2] Customer PII must be retained."
    )
    out = chat.complete(prompt)
    assert "POL-TEST-001" in out
    assert "Section 4.1, Paragraph 2" in out


def test_stub_chat_refusal():
    assert (
        StubChatProvider().complete("no excerpts here")
        == "I cannot answer this based on the available policy documents."
    )

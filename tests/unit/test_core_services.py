from __future__ import annotations

from policyguard.services.chunking import ChunkingService
from policyguard.services.gate import ConfidenceGate, GateDecision
from policyguard.services.pii import PiiRedactionGateway, PresidioClient, to_placeholder
from policyguard.services.risk import RiskClassifier
from policyguard.services.rrf import RetrievalHit, RrfFusionService
from policyguard.config import Settings
from policyguard.services.citation import Citation


def test_to_placeholder():
    assert to_placeholder("PERSON") == "<PERSON>"
    assert to_placeholder("EMAIL_ADDRESS") == "<EMAIL>"
    assert to_placeholder("CUSTOM") == "<CUSTOM>"


def test_pii_redaction_right_to_left():
    class FakeClient:
        def analyze(self, text, language="en"):
            from policyguard.services.pii import PresidioEntity

            return [
                PresidioEntity("PERSON", 0, 4),
                PresidioEntity("EMAIL_ADDRESS", 10, 25),
            ]

    gw = PiiRedactionGateway(FakeClient())  # type: ignore[arg-type]
    result = gw.redact("John lives a@b.com now")
    assert "<PERSON>" in result.redacted_text
    assert "<EMAIL>" in result.redacted_text
    assert result.was_redacted


def test_risk_classifier_policy_exception():
    s = Settings()
    clf = RiskClassifier(s)
    high = clf.classify("Can we request a waiver of the GDPR policy for a VIP customer?")
    assert high.requires_review
    assert high.category == "policy_exception"
    low = clf.classify("What is the refund policy for employees in Antarctica?")
    assert not low.requires_review


def test_rrf_fusion_prefers_list_a():
    a = [RetrievalHit("c1", "d1", "p1", "t1", 0.9), RetrievalHit("c2", "d1", "p2", "t2", 0.8)]
    b = [RetrievalHit("c2", "d1", "p2", "t2-kw", 0.5), RetrievalHit("c3", "d2", "p1", "t3", 0.4)]
    fused = RrfFusionService().fuse(a, b, 60)
    assert fused[0].chunk_id in {"c1", "c2"}
    c2 = next(h for h in fused if h.chunk_id == "c2")
    assert c2.text == "t2"  # from list a


def test_confidence_gate():
    s = Settings()
    s.confidence_threshold = 0.65
    gate = ConfidenceGate(s)
    assert gate.is_explicit_refusal("I cannot answer this based on the available policy documents.")
    refuse = gate.evaluate(0.2, [], [])
    assert refuse.decision == GateDecision.REFUSE
    ok = gate.evaluate(0.9, [Citation("c1", "d1", "p1", "snip")], [])
    assert ok.decision == GateDecision.ANSWER
    bad_cite = gate.evaluate(0.9, [Citation(None, "d1", "p1", None)], [])
    assert bad_cite.decision == GateDecision.REFUSE


def test_chunking_section_refs():
    svc = ChunkingService(max_chars=1200, overlap=150)
    text = "4.1 Retention Periods\n\nFirst paragraph about retention.\n\nSecond paragraph about closure."
    chunks = svc.chunk(text)
    refs = [c.paragraph_ref for c in chunks]
    assert any(r.startswith("Section 4.1") for r in refs)
    assert "Section 4.1, Paragraph 2" in refs


def test_stub_embedding_deterministic():
    from policyguard.providers import StubEmbeddingProvider

    emb = StubEmbeddingProvider(1536)
    a = emb.embed("hello")
    b = emb.embed("hello")
    assert a == b
    assert abs(sum(x * x for x in a) - 1.0) < 1e-5

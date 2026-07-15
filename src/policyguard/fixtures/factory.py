"""Synthetic policy fixtures for seed + eval (text form, PDF-wrapped for ingest)."""
from __future__ import annotations

import io
from dataclasses import dataclass
from typing import Any

from reportlab.lib.pagesizes import letter
from reportlab.pdfgen import canvas


@dataclass
class PolicyFixture:
    document_id: str
    doc_type: str
    title: str
    text: str
    metadata: dict[str, Any]

    def to_pdf_bytes(self) -> bytes:
        buf = io.BytesIO()
        c = canvas.Canvas(buf, pagesize=letter)
        width, height = letter
        y = height - 72
        c.setFont("Helvetica-Bold", 14)
        c.drawString(72, y, self.title)
        y -= 28
        c.setFont("Helvetica", 10)
        for para in self.text.split("\n\n"):
            lines = _wrap(para, 90)
            for line in lines:
                if y < 72:
                    c.showPage()
                    c.setFont("Helvetica", 10)
                    y = height - 72
                c.drawString(72, y, line)
                y -= 14
            y -= 10
        c.showPage()
        c.setFont("Helvetica", 10)
        c.drawString(72, height - 72, f"End of {self.document_id}")
        c.save()
        return buf.getvalue()


def _wrap(text: str, width: int) -> list[str]:
    words = text.split()
    lines: list[str] = []
    cur: list[str] = []
    for w in words:
        trial = (" ".join(cur + [w])).strip()
        if len(trial) > width and cur:
            lines.append(" ".join(cur))
            cur = [w]
        else:
            cur.append(w)
    if cur:
        lines.append(" ".join(cur))
    return lines or [""]


def build_all() -> list[PolicyFixture]:
    return [
        PolicyFixture(
            "POL-TEST-001",
            "policy",
            "Data Retention and Privacy Policy",
            """1 Overview

This policy governs the collection, retention, protection, and disposal of personal information processed by the organization across all business units and systems.

All employees, contractors, and third-party service providers who handle personal data on behalf of the organization are bound by this policy.

4 Data Retention Requirements

Personal data must be retained only for as long as necessary to fulfil the purposes for which it was collected, subject to legal and regulatory retention mandates.

4.1 Retention Periods

Customer account records must follow the retention periods as specified in the Data Retention Schedule appended to this policy.

Customer PII must be retained for seven years after account closure before secure disposal, unless a longer period is required by applicable law.

Contact for retention questions: Jane Doe-TEST at jane.doe@company-test.example phone (555) 000-0001 SSN 000-00-0001.
""",
            {"highRisk": False},
        ),
        PolicyFixture(
            "SOP-TEST-002",
            "sop",
            "Security Incident Response SOP",
            """1 Purpose

This SOP defines the required steps for detecting, reporting, and responding to security incidents affecting the organization's systems and data.

4.1 Detection and Reporting

Upon detecting or suspecting a security incident, personnel must immediately notify the Security Operations Center and open an incident ticket within fifteen minutes.

The first action to take when a security incident is detected is to isolate affected systems from the network while preserving forensic evidence.
""",
            {},
        ),
        PolicyFixture(
            "CTL-TEST-003",
            "control",
            "Access Control Framework",
            """1 Scope

This control framework defines privileged access roles and monitoring requirements.

2.1 System Administrator Role

The System Administrator role is managed by John Smith-FAKE in the Infrastructure team.

3.1 System Administrator Access

System Administrators have full read, write, and execute access to all production servers subject to change-management approval.
""",
            {},
        ),
        PolicyFixture(
            "FAQ-TEST-004",
            "faq",
            "Employee Benefits FAQ",
            """1 Benefits Overview

This FAQ summarizes medical insurance and related employee benefits.

2.1 Medical Coverage

What medical insurance coverage tiers are available to employees?

Employees may enroll in Bronze, Silver, or Gold medical insurance tiers during open enrollment.
""",
            {},
        ),
        PolicyFixture(
            "POL-TEST-005",
            "policy",
            "GDPR and Regulatory Policy",
            """1 Purpose

This policy describes GDPR and related regulatory obligations.

4.1 GDPR Requirements

Personal data subject to GDPR must be processed lawfully, fairly, and transparently with documented lawful basis.

Exception and waiver requests for GDPR policy requirements require written approval from the Data Protection Officer.
""",
            {"highRisk": True},
        ),
        PolicyFixture(
            "SOP-TEST-006",
            "sop",
            "Vendor Risk SOP",
            """1 Overview

Vendor onboarding and continuous monitoring requirements.

2.1 Due Diligence

All critical vendors must complete security questionnaires annually.
""",
            {},
        ),
        PolicyFixture(
            "POL-TEST-007",
            "policy",
            "Acceptable Use Policy",
            """1 Overview

Acceptable use of company systems and data.

2.1 Prohibited Activities

Users must not share credentials or disclose customer PII outside approved channels.
""",
            {},
        ),
        PolicyFixture(
            "FAQ-TEST-008",
            "faq",
            "IT Support FAQ",
            """1 Support Channels

How to reach IT support and escalate issues.

4.1 VPN Setup and Troubleshooting

Employees must use the corporate VPN when accessing internal tools from untrusted networks.
""",
            {},
        ),
    ]


def build(document_id: str) -> PolicyFixture:
    for f in build_all():
        if f.document_id == document_id:
            return f
    raise ValueError(f"Unknown fixture: {document_id}")

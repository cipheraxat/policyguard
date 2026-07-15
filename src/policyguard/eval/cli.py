from __future__ import annotations

import argparse
from pathlib import Path

from policyguard.eval.harness import run_eval


def main() -> None:
    parser = argparse.ArgumentParser(description="Run PolicyGuard offline eval")
    parser.add_argument("-o", "--output", type=Path, default=Path("target/eval-report.json"))
    args = parser.parse_args()
    report = run_eval(args.output)
    print(
        f"passed={report.passed}/{report.total} "
        f"citation_precision={report.citation_precision:.2f} "
        f"escalation_recall={report.escalation_recall:.2f} "
        f"p95_ms={report.p95_latency_ms:.1f}"
    )
    print(f"wrote {args.output}")


if __name__ == "__main__":
    main()

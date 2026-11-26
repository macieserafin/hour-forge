"""Writes test-stats.json from the test reports of one CI run.

Usage: test-stats.py <reports dir> <commit SHA> <output file>

The reports directory holds what the test jobs uploaded:
  surefire-reports/TEST-*.xml   backend unit tests (*Test)
  failsafe-reports/TEST-*.xml   backend integration tests (*IT)
  vitest.json           Vitest JSON reporter
  playwright.json       Playwright JSON reporter (desktop project)

Every number counts the tests that ran, skipped ones included. A missing or empty report stops the script, so the
published file never shows a zero by accident.
"""

import json
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path


def junit_count(directory: Path) -> int:
    reports = sorted(directory.glob("TEST-*.xml"))
    if not reports:
        sys.exit(f"no JUnit reports in {directory}")
    total = 0
    for report in reports:
        root = ET.parse(report).getroot()
        suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
        total += sum(int(suite.get("tests", "0")) for suite in suites)
    return total


def vitest_count(report: Path) -> int:
    return int(json.loads(report.read_text(encoding="utf-8"))["numTotalTests"])


def playwright_count(report: Path) -> int:
    stats = json.loads(report.read_text(encoding="utf-8"))["stats"]
    return sum(int(stats.get(key, 0)) for key in ("expected", "unexpected", "flaky", "skipped"))


def main() -> None:
    reports, commit, output = Path(sys.argv[1]), sys.argv[2], Path(sys.argv[3])
    unit = junit_count(reports / "surefire-reports")
    integration = junit_count(reports / "failsafe-reports")
    vitest = vitest_count(reports / "vitest.json")
    playwright = playwright_count(reports / "playwright.json")
    if min(unit, integration, vitest, playwright) <= 0:
        sys.exit("a report holds no tests")
    stats = {
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "commit": commit[:7],
        "backend": {"total": unit + integration, "unit": unit, "integration": integration},
        "frontend": {"vitest": vitest, "playwright": playwright},
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(stats, indent=2) + "\n", encoding="utf-8")
    print(output.read_text(encoding="utf-8"))


if __name__ == "__main__":
    main()

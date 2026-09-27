"""Summarize real javac diagnostics; no estimate of functional completeness."""
from __future__ import annotations

import argparse
import collections
import json
import re
from datetime import datetime, timezone
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--log", default="build/migration-compile.log")
    parser.add_argument("--output", default="build/migration-status.json")
    parser.add_argument("--check", action="store_true", help="Fail unless the logged build succeeded")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    log = root / args.log
    if not log.is_file():
        parser.error(f"Compilation log does not exist: {log}")
    text = log.read_text(encoding="utf-8-sig", errors="replace")
    by_file: collections.Counter[str] = collections.Counter()
    by_kind: collections.Counter[str] = collections.Counter()
    for match in re.finditer(r"^(.+?\.java):(\d+): error: (.+)$", text, re.MULTILINE):
        path = match[1].replace("\\", "/")
        path = path[path.index("src/"):] if "src/" in path else path
        by_file[path] += 1
        by_kind[match[3]] += 1
    totals = re.findall(r"^(\d+) errors?\s*$", text, re.MULTILINE)
    total = int(totals[-1]) if totals else None
    success = "BUILD SUCCESSFUL" in text and "BUILD FAILED" not in text
    if success and total is None:
        total = 0
    status = {
        "updated_utc": datetime.now(timezone.utc).isoformat(),
        "log": args.log,
        "log_modified_utc": datetime.fromtimestamp(log.stat().st_mtime, timezone.utc).isoformat(),
        "build_succeeded": success,
        "reported_errors": total,
        "displayed_errors": sum(by_file.values()),
        "errors_by_file": dict(by_file.most_common()),
        "errors_by_kind": dict(by_kind.most_common()),
        "runtime_verified": False,
        "note": "A successful compile does not validate mixin application or feature parity in game.",
    }
    output = root / args.output
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(status, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Build: {'SUCCESS' if success else 'NOT VALIDATED'}; errors: {total if total is not None else 'unknown'}")
    for path, count in by_file.most_common(15):
        print(f"{count:4}  {path}")
    print(f"Report: {output}")
    return int(args.check and not success)


if __name__ == "__main__":
    raise SystemExit(main())

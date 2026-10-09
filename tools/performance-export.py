#!/usr/bin/env python3
"""Export an existing Hytale Civ performance recording through the localhost bridge."""
import argparse
import json
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


def request(port, command):
    payload = json.dumps({"command": "civagent " + command}).encode("utf-8")
    req = urllib.request.Request(
        f"http://127.0.0.1:{port}/command", data=payload,
        headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=20) as response:
        result = json.load(response)
    if not result.get("success"):
        raise RuntimeError(str(result.get("error", result)))
    for line in result.get("output", []):
        if line.startswith("CIVAGENT_RESULT "):
            return json.loads(line[len("CIVAGENT_RESULT "):])["data"]
    raise RuntimeError("No CIVAGENT_RESULT returned: " + str(result.get("output")))


def collect(port, action, limit, page_size):
    rows = []
    total = None
    while total is None or len(rows) < total:
        page = request(port, action + " " + str(len(rows)))
        if total is None:
            total = min(int(page["total"]), limit)
        batch = page["samples"] if action == "perf-samples" else page["events"]
        if not batch:
            break
        rows.extend(batch[: max(0, total - len(rows))])
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=5523)
    parser.add_argument("--out", type=Path, default=Path("civ-performance.json"))
    parser.add_argument("--stop", action="store_true",
                        help="Stop an active session before exporting")
    args = parser.parse_args()
    if args.stop:
        request(args.port, "perf-stop")
    export = {
        "exportedAt": datetime.now(timezone.utc).isoformat(),
        "report": request(args.port, "perf-report"),
        "samples": collect(args.port, "perf-samples", 900, 10),
        "events": collect(args.port, "perf-events", 2048, 100),
        "clientFps": None,
        "clientFpsNote": "Not available from verified Hytale server API",
    }
    args.out.write_text(json.dumps(export, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Exported {len(export['samples'])} samples and {len(export['events'])} events to {args.out}")


if __name__ == "__main__":
    main()

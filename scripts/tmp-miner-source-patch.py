from pathlib import Path

path = Path("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
text = path.read_text(encoding="utf-8")
old = "        frontCoordinator.clearClaims(plan.frontId);\n        decisionSink.record("
new = "        frontCoordinator.releaseFront(plan.frontId);\n        decisionSink.record("
if old not in text:
    raise SystemExit("blocked-front claim release fragment not found")
path.write_text(text.replace(old, new, 1), encoding="utf-8")

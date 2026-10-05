from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MINE_DIR = ROOT / "asset-pack" / "Server" / "Prefabs" / "Civilizations" / "Mine"

SIDE_X = (-3, 2)
TOP_X = (-2, -1, 0, 1)
LANTERN_X = (-2, 1)


def block_map(data: dict) -> dict[tuple[int, int, int], dict]:
    return {(b["x"], b["y"], b["z"]): b for b in data["blocks"]}


def require_name(blocks: dict, coord: tuple[int, int, int], expected: str) -> None:
    block = blocks.get(coord)
    actual = None if block is None else block.get("name")
    if actual != expected:
        raise RuntimeError(f"Unexpected block at {coord}: expected {expected}, got {actual}")


def replace_block(blocks: dict, coord: tuple[int, int, int], name: str, **extra: int) -> None:
    old = blocks[coord]
    replacement = {"x": coord[0], "y": coord[1], "z": coord[2], "name": name}
    replacement.update(extra)
    old.clear()
    old.update(replacement)


def patch_prefab(file_name: str, move_lanterns: bool) -> None:
    path = MINE_DIR / file_name
    data = json.loads(path.read_text(encoding="utf-8"))
    blocks = block_map(data)

    # The tunnel connector itself stays centered at Z=9.5. Move only the timber portal
    # one block inward (Z=8), leaving solid stone at the connector-facing row (Z=9).
    for x in SIDE_X:
        for y in range(1, 5):
            require_name(blocks, (x, y, 8), "Rock_Stone")
            require_name(blocks, (x, y, 9), "Wood_Fir_Trunk")
            replace_block(blocks, (x, y, 8), "Wood_Fir_Trunk", support=15)
            replace_block(blocks, (x, y, 9), "Rock_Stone")

    for x in TOP_X:
        require_name(blocks, (x, 5, 8), "Rock_Stone")
        require_name(blocks, (x, 5, 9), "Wood_Fir_Trunk")
        replace_block(blocks, (x, 5, 8), "Wood_Fir_Trunk", support=15, rotation=5)
        replace_block(blocks, (x, 5, 9), "Rock_Stone")

    if move_lanterns:
        for x in LANTERN_X:
            require_name(blocks, (x, 4, 8), "Empty")
            require_name(blocks, (x, 4, 9), "Deco_Lantern_Ceiling")
            replace_block(blocks, (x, 4, 8), "Deco_Lantern_Ceiling")
            replace_block(blocks, (x, 4, 9), "Empty")
    else:
        # Mine 1 intentionally has no lanterns on this portal.
        for x in LANTERN_X:
            require_name(blocks, (x, 4, 8), "Empty")
            require_name(blocks, (x, 4, 9), "Empty")

    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def replace_once(text: str, old: str, new: str, description: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected exactly one {description} occurrence, got {count}")
    return text.replace(old, new, 1)


def patch_miner_runtime() -> None:
    path = ROOT / "src" / "main" / "java" / "dev" / "civilizations" / "hytale" / "MinerWorkSystem.java"
    text = path.read_text(encoding="utf-8")

    text = replace_once(
        text,
        """        if (!mine.id().equals(runtime.mineId)) {\n            stopMiningAnimation(ref, store, runtime);\n            runtime.reset(mine.id());\n        }\n""",
        """        if (!mine.id().equals(runtime.mineId) || mine.phase() != runtime.minePhase) {\n            stopMiningAnimation(ref, store, runtime);\n            runtime.reset(mine.id(), mine.phase());\n        }\n""",
        "mine identity reset",
    )

    text = replace_once(
        text,
        """        private UUID mineId;\n        private UUID segmentId;\n""",
        """        private UUID mineId;\n        private int minePhase;\n        private UUID segmentId;\n""",
        "worker mine fields",
    )

    text = replace_once(
        text,
        """        private void reset(UUID nextMineId) {\n            mineId = nextMineId;\n            segmentId = null;\n""",
        """        private void reset(UUID nextMineId, int nextMinePhase) {\n            mineId = nextMineId;\n            minePhase = nextMinePhase;\n            segmentId = null;\n""",
        "worker reset method",
    )

    path.write_text(text, encoding="utf-8")


patch_prefab("Mine_01.prefab.json", move_lanterns=False)
patch_prefab("Mine_02.prefab.json", move_lanterns=True)
patch_prefab("Mine_03.prefab.json", move_lanterns=True)
patch_miner_runtime()

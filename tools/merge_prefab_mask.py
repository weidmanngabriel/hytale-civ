#!/usr/bin/env python3
import argparse
import json
import sys
from pathlib import Path

DEFAULT_MARKER = "Rock_Crystal_White_Block"
EMPTY = "Empty"


def pos(entry):
    return (entry["x"], entry["y"], entry["z"])


def load(path: Path):
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def validate_header(source, mask):
    for key in ("version", "blockIdVersion", "anchorX", "anchorY", "anchorZ"):
        if source.get(key) != mask.get(key):
            raise ValueError(f"Prefab mismatch for {key}: {source.get(key)!r} != {mask.get(key)!r}")


def merge(source, mask, marker):
    validate_header(source, mask)

    source_blocks = {pos(block): block for block in source.get("blocks", [])}
    mask_blocks = {pos(block): block for block in mask.get("blocks", [])}
    source_fluids = {pos(fluid): fluid for fluid in source.get("fluids", [])}

    if any(block.get("name") == marker for block in source_blocks.values()):
        raise ValueError(f"Marker block {marker!r} already exists in source prefab")

    unexpected_new = [
        (coord, block)
        for coord, block in mask_blocks.items()
        if coord not in source_blocks and block.get("name") != marker
    ]
    if unexpected_new:
        sample = unexpected_new[:5]
        raise ValueError(f"Mask contains non-marker blocks at new coordinates, e.g. {sample}")

    marker_coords = {
        coord for coord, block in mask_blocks.items() if block.get("name") == marker
    }
    kept_coords = {
        coord for coord in source_blocks if coord in mask_blocks
    }
    removed_coords = set(source_blocks) - set(mask_blocks)

    changed_non_marker = []
    for coord in kept_coords:
        source_block = source_blocks[coord]
        mask_block = mask_blocks[coord]
        if mask_block.get("name") != marker and source_block != mask_block:
            changed_non_marker.append((coord, source_block, mask_block))

    final_blocks = [dict(source_blocks[coord]) for coord in kept_coords]
    final_blocks.extend(
        {"x": x, "y": y, "z": z, "name": EMPTY}
        for x, y, z in marker_coords
    )
    final_blocks.sort(key=lambda e: (e["x"], e["z"], e["y"]))

    final_coords = kept_coords | marker_coords
    final_fluids = []
    for coord in final_coords:
        if coord in source_fluids:
            final_fluids.append(dict(source_fluids[coord]))
        else:
            x, y, z = coord
            final_fluids.append({"x": x, "y": y, "z": z, "name": EMPTY, "level": 0})
    final_fluids.sort(key=lambda e: (e["x"], e["z"], e["y"]))

    result = dict(source)
    result["blocks"] = final_blocks
    result["fluids"] = final_fluids
    result["entities"] = source.get("entities", [])

    stats = {
        "source_blocks": len(source_blocks),
        "mask_blocks": len(mask_blocks),
        "kept_source_blocks": len(kept_coords),
        "removed_source_blocks": len(removed_coords),
        "explicit_empty_blocks": len(marker_coords),
        "final_blocks": len(final_blocks),
        "non_marker_differences_ignored": len(changed_non_marker),
    }
    return result, stats, changed_non_marker


def main():
    parser = argparse.ArgumentParser(
        description="Merge a Hytale source prefab with a marker/empty authoring prefab."
    )
    parser.add_argument("source", type=Path)
    parser.add_argument("mask", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--marker", default=DEFAULT_MARKER)
    parser.add_argument("--pretty", action="store_true")
    args = parser.parse_args()

    result, stats, differences = merge(load(args.source), load(args.mask), args.marker)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8") as f:
        if args.pretty:
            json.dump(result, f, ensure_ascii=False, indent=2)
            f.write("\n")
        else:
            json.dump(result, f, ensure_ascii=False, separators=(",", ":"))
            f.write("\n")

    print(json.dumps(stats, indent=2))
    if differences:
        print(
            f"Warning: ignored {len(differences)} non-marker differences in the mask; "
            "the source prefab remained authoritative for those cells.",
            file=sys.stderr,
        )


if __name__ == "__main__":
    main()

from pathlib import Path

path = Path("src/main/java/dev/civilizations/hytale/MinerWorkSystem.java")
text = path.read_text(encoding="utf-8")

replacements = [
    (
        "import java.util.ArrayList;\nimport java.util.Comparator;\nimport java.util.List;",
        "import java.util.ArrayList;\nimport java.util.List;",
    ),
    (
        "        MineTunnelGeometry.Slice slice = plan.slices.get(plan.sliceIndex);\n        BlockPosition target = frontCoordinator.claimNext(",
        "        BlockPosition target = frontCoordinator.claimNext(",
    ),
    (
        "        frontCoordinator.clearClaims(plan.frontId);\n        tunnelRegistry.putNetwork(world, network.withWorkFront(updated));",
        "        frontCoordinator.releaseFront(plan.frontId);\n        tunnelRegistry.putNetwork(world, network.withWorkFront(updated));",
    ),
    (
        "    private static List<List<BlockPosition>> orderedBlocks(List<MineTunnelGeometry.Slice> slices) {\n        List<List<BlockPosition>> result = new ArrayList<>(slices.size());\n        Comparator<BlockPosition> order = Comparator\n            .comparingInt(BlockPosition::y)\n            .thenComparingInt(BlockPosition::x)\n            .thenComparingInt(BlockPosition::z);\n        for (MineTunnelGeometry.Slice slice : slices) {\n            result.add(slice.excavationBlocks().stream().sorted(order).toList());\n        }\n        return List.copyOf(result);\n    }",
        "    private static List<List<BlockPosition>> orderedBlocks(List<MineTunnelGeometry.Slice> slices) {\n        List<List<BlockPosition>> result = new ArrayList<>(slices.size());\n        for (MineTunnelGeometry.Slice slice : slices) {\n            result.add(List.copyOf(slice.excavationBlocks()));\n        }\n        return List.copyOf(result);\n    }",
    ),
    (
        "    private static BlockPosition initialCenterlineOrigin(BuildingBounds connector, MineHeading heading) {\n        int x = (int) Math.floor((connector.minX() + connector.maxX()) * 0.5 + heading.unitX());\n        int y = (int) Math.floor(connector.minY());\n        int z = (int) Math.floor((connector.minZ() + connector.maxZ()) * 0.5 + heading.unitZ());\n        return new BlockPosition(x, y, z);\n    }",
        "    static BlockPosition initialCenterlineOrigin(BuildingBounds connector, MineHeading heading) {\n        int centerX = (int) Math.floor((connector.minX() + connector.maxX()) * 0.5);\n        int centerZ = (int) Math.floor((connector.minZ() + connector.maxZ()) * 0.5);\n        int y = (int) Math.floor(connector.minY());\n        int x = heading.unitX() > 0.01\n            ? (int) Math.ceil(connector.maxX())\n            : heading.unitX() < -0.01 ? (int) Math.floor(connector.minX()) - 1 : centerX;\n        int z = heading.unitZ() > 0.01\n            ? (int) Math.ceil(connector.maxZ())\n            : heading.unitZ() < -0.01 ? (int) Math.floor(connector.minZ()) - 1 : centerZ;\n        return new BlockPosition(x, y, z);\n    }",
    ),
]

for old, new in replacements:
    if old not in text:
        raise SystemExit(f"expected source fragment not found: {old[:120]!r}")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")

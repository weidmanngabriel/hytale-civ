package dev.civilizations.hytale;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.civilizations.core.BlockPosition;
import dev.civilizations.core.DwarvenMineFinishPlan;

/**
 * Immediate native finishing after excavation has advanced past an arch location.
 * No worker task, artificial renderer, or replacement of solid world blocks.
 */
public final class DwarvenMineFinishExecutor {
    private DwarvenMineFinishExecutor() {
    }

    public static boolean finish(World world, DwarvenMineFinishPlan.Feature feature) {
        if (world == null || feature == null) return false;
        String pillar = MineBlockPlacement.resolveAsset(
            new String[]{"Stone_Brick_Pillar_Base"}, "stone", "brick", "pillar", "base"
        );
        String lantern = MineBlockPlacement.resolveAsset(
            new String[]{"Deco_Lantern"}, "lantern"
        );
        if (pillar == null || lantern == null) return false;
        // All placements use the native loaded-world checks: an occupied block is never
        // overwritten, and a partial placement may be retried idempotently next slice.
        if (!place(world, feature.left(), pillar, below(feature.left()))) return false;
        if (!place(world, feature.right(), pillar, below(feature.right()))) return false;
        if (!place(world, above(feature.left()), lantern, feature.left())) return false;
        return place(world, above(feature.right()), lantern, feature.right());
    }

    private static boolean place(
        World world, BlockPosition position, String asset, BlockPosition support
    ) {
        return MineBlockPlacement.place(
            world, position, asset, RotationTuple.NONE, support, false
        );
    }

    private static BlockPosition above(BlockPosition block) {
        return new BlockPosition(block.x(), block.y() + 1, block.z());
    }

    private static BlockPosition below(BlockPosition block) {
        return new BlockPosition(block.x(), block.y() - 1, block.z());
    }
}

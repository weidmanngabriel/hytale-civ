package dev.civilizations.core;

import java.util.List;
import java.util.UUID;

/** Logical tunnel in the persistent mine network. Geometry is represented separately by segments. */
public record MineTunnel(
    UUID id,
    Kind kind,
    UUID parentTunnelId,
    int branchDepth,
    BlockPosition origin,
    List<UUID> segmentIds
) {
    public MineTunnel {
        if (id == null || kind == null || origin == null || segmentIds == null) {
            throw new IllegalArgumentException("Mine tunnel fields must not be null.");
        }
        segmentIds = List.copyOf(segmentIds);
        if (kind == Kind.MAIN) {
            if (parentTunnelId != null || branchDepth != 0) {
                throw new IllegalArgumentException("Main tunnel cannot have a parent or branch depth.");
            }
        } else if (parentTunnelId == null || branchDepth <= 0) {
            throw new IllegalArgumentException("Branch tunnel requires parent and positive branch depth.");
        }
    }

    public MineTunnel withSegment(UUID segmentId) {
        if (segmentId == null || segmentIds.contains(segmentId)) return this;
        java.util.ArrayList<UUID> next = new java.util.ArrayList<>(segmentIds);
        next.add(segmentId);
        return new MineTunnel(id, kind, parentTunnelId, branchDepth, origin, next);
    }

    public enum Kind {
        MAIN,
        BRANCH
    }
}

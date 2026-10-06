package dev.civilizations.core;

import java.util.UUID;

/** Logical tunnel in the persistent mine network. Concrete geometry is planned separately. */
public record MineTunnel(
    UUID id,
    Kind kind,
    UUID parentTunnelId,
    int branchDepth,
    BlockPosition origin
) {
    public MineTunnel {
        if (id == null || kind == null || origin == null) {
            throw new IllegalArgumentException("Mine tunnel fields must not be null.");
        }
        if (kind == Kind.MAIN) {
            if (parentTunnelId != null || branchDepth != 0) {
                throw new IllegalArgumentException("Main tunnel cannot have a parent or branch depth.");
            }
        } else if (parentTunnelId == null || branchDepth <= 0) {
            throw new IllegalArgumentException("Branch tunnel requires parent and positive branch depth.");
        }
    }

    public enum Kind {
        MAIN,
        BRANCH
    }
}

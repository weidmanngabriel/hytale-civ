# World region snapshot export

The pinned project `HytaleServer.jar` exposes `World.getChunkIfLoaded` and `WorldChunk.getBlockType(int,int,int)`, `getFluidId(int,int,int)`, and `getFluidLevel(int,int,int)`. The Civ export command reads only already loaded chunks on the world executor. No save-file decoding is assumed.

The `/worldexport x y z width height depth` development command is registered by Civ, uses a bounded region, and writes a compressed `.civworld.gz` archive to `civ-world-archives/` in the server working directory. The original block asset ID, native fluid ID, fluid level, and fluid category are retained for every sampled cell, including air. Chunk data unavailable at export time is treated as an error, never as air. An archive should be copied separately to the local simulator and used as immutable input; the reduced material categories can be rebuilt on every run.

**Evidence limit:** Signatures in the JAR establish only available API members. Actual player-world export behavior, chunk loadedness, thread semantics and fluid classification need runtime verification in the pinned engine. Direct parsing of player save storage is not verified.

This is development tooling, not automatic sharing of saves. Archive source data should not be committed by default.

package dev.civilizations.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.civilizations.hytale.CivActivityRegistry;
import dev.civilizations.hytale.CivUnitRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.UUID;

/** Plugin-owned, explicitly enabled live session. File/network I/O runs off the World thread. */
final class CivLiveBridgeService implements AutoCloseable {
    private final CivUnitRegistry units;
    private final CivActivityRegistry activities;
    private final Path connectionFile;
    private CivDevBridge bridge;
    private boolean closed;
    private FileChannel lockChannel;
    private FileLock lock;

    CivLiveBridgeService(CivUnitRegistry units, CivActivityRegistry activities) {
        this(units, activities, Path.of(System.getProperty("user.home"), ".hytale-civ", "bridge.json"));
    }

    CivLiveBridgeService(CivUnitRegistry units, CivActivityRegistry activities, Path connectionFile) {
        this.units = units;
        this.activities = activities;
        this.connectionFile = connectionFile;
    }

    synchronized String enable(World world, UUID owner) throws IOException {
        java.util.Objects.requireNonNull(world);
        java.util.Objects.requireNonNull(owner);
        if (closed) throw new IllegalStateException("Plugin is shutting down");
        if (bridge != null) return "MCP bereits aktiv. Verbindungsdatei: " + connectionFile;
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        String session = UUID.randomUUID().toString();
        CivDevBridge candidate = new CivDevBridge(units, activities, token, session, world, owner);
        Path temporary = null;
        try {
            Files.createDirectories(connectionFile.getParent());
            lockChannel = FileChannel.open(connectionFile.resolveSibling("bridge.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = lockChannel.tryLock();
            if (lock == null) throw new IllegalStateException("Another live Hytale session already owns the connection file");
            candidate.start(0); // OS-selected free port; no collision with the isolated runtime.
            temporary = Files.createTempFile(connectionFile.getParent(), "bridge-", ".tmp");
            if (Files.getFileStore(temporary).supportsFileAttributeView("posix"))
                Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
            new ObjectMapper().writeValue(temporary.toFile(), Map.of("version", 1, "mode", "live",
                "port", candidate.port(), "token", token, "sessionId", session));
            Files.move(temporary, connectionFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            bridge = candidate;
            return "MCP aktiv fuer diese Welt. Verbindungsdatei: " + connectionFile;
        } catch (IOException | RuntimeException exception) {
            candidate.close();
            releaseLock();
            if (temporary != null) Files.deleteIfExists(temporary);
            throw exception;
        }
    }

    synchronized String disable() throws IOException {
        if (bridge != null) {
            bridge.close(); bridge = null;
            try { Files.deleteIfExists(connectionFile); } finally { releaseLock(); }
        }
        return "MCP deaktiviert. Spiel und NPCs bleiben bestehen.";
    }

    private void releaseLock() throws IOException {
        try { if (lock != null) lock.release(); }
        finally { lock = null; if (lockChannel != null) lockChannel.close(); lockChannel = null; }
    }

    @Override public synchronized void close() {
        closed = true;
        try { disable(); }
        catch (IOException exception) { System.err.println("CIV_DEV_DISCOVERY_CLEANUP_FAILED"); }
    }
}

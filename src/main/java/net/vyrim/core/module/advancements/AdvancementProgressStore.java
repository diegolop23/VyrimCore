package net.vyrim.core.module.advancements;

import net.vyrim.core.VyrimCore;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * High-performance, thread-safe progress store for multi-step advancements (amount > 1).
 * <p>
 * Maintains an in-memory cache for zero-latency operations on the Bukkit main thread
 * and periodically flushes pending updates and deletions to SQLite in batch transactions.
 */
public class AdvancementProgressStore {

    public record ProgressKey(UUID playerUuid, String advancementId) {
        public ProgressKey {
            Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
            Objects.requireNonNull(advancementId, "advancementId cannot be null");
        }
    }

    private final VyrimCore core;
    private final Supplier<Connection> connectionSupplier;

    private final Map<ProgressKey, Integer> cache = new ConcurrentHashMap<>();
    private final Set<ProgressKey> dirtyWrites = ConcurrentHashMap.newKeySet();
    private final Set<ProgressKey> pendingDeletes = ConcurrentHashMap.newKeySet();

    private final Object dbLock = new Object();
    private BukkitTask flushTask;

    public AdvancementProgressStore(VyrimCore core) {
        this(core, () -> core != null && core.storage() != null ? core.storage().getConnection() : null);
    }

    public AdvancementProgressStore(VyrimCore core, Supplier<Connection> connectionSupplier) {
        this.core = core;
        this.connectionSupplier = connectionSupplier;
    }

    /**
     * Initializes the SQLite schema and loads existing progress counters into memory.
     */
    public void init() {
        createTableIfNotExists();
        loadAll();
    }

    /**
     * Starts the periodic async flush task.
     *
     * @param intervalTicks interval in Bukkit ticks between batch flushes (e.g. 100 ticks = 5 seconds)
     */
    public void start(long intervalTicks) {
        if (flushTask != null) {
            flushTask.cancel();
        }
        if (core != null && Bukkit.getServer() != null && Bukkit.getScheduler() != null) {
            this.flushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(core, this::flushSync, intervalTicks, intervalTicks);
        }
    }

    /**
     * Creates the SQLite table if it does not already exist.
     */
    public void createTableIfNotExists() {
        Connection conn = getConnection();
        if (conn == null) {
            return;
        }

        String sql = """
            CREATE TABLE IF NOT EXISTS advancement_progress (
                player_uuid TEXT NOT NULL,
                advancement_id TEXT NOT NULL,
                progress INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid, advancement_id)
            );
            """;

        synchronized (dbLock) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            } catch (SQLException ex) {
                log(Level.SEVERE, "[Advancements] Failed to initialize advancement_progress table: " + ex.getMessage(), ex);
            }
        }
    }

    /**
     * Bulk loads all stored counters from SQLite into memory.
     */
    public void loadAll() {
        Connection conn = getConnection();
        if (conn == null) {
            return;
        }

        String query = "SELECT player_uuid, advancement_id, progress FROM advancement_progress";

        synchronized (dbLock) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                int count = 0;
                while (rs.next()) {
                    String uuidStr = rs.getString("player_uuid");
                    String advId = rs.getString("advancement_id");
                    int progress = rs.getInt("progress");

                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        ProgressKey key = new ProgressKey(uuid, advId);
                        cache.put(key, progress);
                        count++;
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                log(Level.INFO, "[Advancements] Loaded " + count + " active progress counter(s) from SQLite.");
            } catch (SQLException ex) {
                log(Level.SEVERE, "[Advancements] Failed to bulk load advancement progress: " + ex.getMessage(), ex);
            }
        }
    }

    /**
     * Increments the cumulative counter for (playerUUID, advancementId) by delta and returns the new value.
     * Safe to call from Bukkit event handlers (main thread).
     *
     * @param playerUuid    the player's UUID
     * @param advancementId the advancement ID
     * @param delta         the amount to increment (must be > 0)
     * @return the new progress value
     */
    public int incrementAndGet(UUID playerUuid, String advancementId, int delta) {
        if (playerUuid == null || advancementId == null || delta <= 0) {
            return getProgress(playerUuid, advancementId);
        }

        ProgressKey key = new ProgressKey(playerUuid, advancementId);
        pendingDeletes.remove(key);
        int updated = cache.compute(key, (k, current) -> (current == null ? 0 : current) + delta);
        dirtyWrites.add(key);
        return updated;
    }

    /**
     * Gets the current progress counter for (playerUUID, advancementId), or 0 if not tracked.
     *
     * @param playerUuid    the player's UUID
     * @param advancementId the advancement ID
     * @return current progress
     */
    public int getProgress(UUID playerUuid, String advancementId) {
        if (playerUuid == null || advancementId == null) {
            return 0;
        }
        return cache.getOrDefault(new ProgressKey(playerUuid, advancementId), 0);
    }

    /**
     * Resets the counter for (playerUUID, advancementId).
     * Removes from in-memory cache and schedules deletion from SQLite.
     *
     * @param playerUuid    the player's UUID
     * @param advancementId the advancement ID
     */
    public void reset(UUID playerUuid, String advancementId) {
        if (playerUuid == null || advancementId == null) {
            return;
        }

        ProgressKey key = new ProgressKey(playerUuid, advancementId);
        cache.remove(key);
        dirtyWrites.remove(key);
        pendingDeletes.add(key);
    }

    /**
     * Synchronously flushes all pending writes and deletions to SQLite within a single transaction.
     */
    public void flushSync() {
        Connection conn = getConnection();
        if (conn == null) {
            return;
        }

        // Snapshot pending keys to write/delete
        List<Map.Entry<ProgressKey, Integer>> writesToProcess = new ArrayList<>();
        for (ProgressKey key : new ArrayList<>(dirtyWrites)) {
            Integer val = cache.get(key);
            if (val != null) {
                writesToProcess.add(Map.entry(key, val));
            }
            dirtyWrites.remove(key);
        }

        List<ProgressKey> deletesToProcess = new ArrayList<>();
        for (ProgressKey key : new ArrayList<>(pendingDeletes)) {
            deletesToProcess.add(key);
            pendingDeletes.remove(key);
        }

        if (writesToProcess.isEmpty() && deletesToProcess.isEmpty()) {
            return;
        }

        String upsertSql = """
            INSERT INTO advancement_progress (player_uuid, advancement_id, progress)
            VALUES (?, ?, ?)
            ON CONFLICT(player_uuid, advancement_id) DO UPDATE SET progress = excluded.progress;
            """;

        String deleteSql = "DELETE FROM advancement_progress WHERE player_uuid = ? AND advancement_id = ?;";

        synchronized (dbLock) {
            boolean initialAutoCommit = true;
            try {
                initialAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                // 1. Process batch upserts
                if (!writesToProcess.isEmpty()) {
                    try (PreparedStatement upsertStmt = conn.prepareStatement(upsertSql)) {
                        for (Map.Entry<ProgressKey, Integer> entry : writesToProcess) {
                            upsertStmt.setString(1, entry.getKey().playerUuid().toString());
                            upsertStmt.setString(2, entry.getKey().advancementId());
                            upsertStmt.setInt(3, entry.getValue());
                            upsertStmt.addBatch();
                        }
                        upsertStmt.executeBatch();
                    }
                }

                // 2. Process batch deletions
                if (!deletesToProcess.isEmpty()) {
                    try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                        for (ProgressKey key : deletesToProcess) {
                            deleteStmt.setString(1, key.playerUuid().toString());
                            deleteStmt.setString(2, key.advancementId());
                            deleteStmt.addBatch();
                        }
                        deleteStmt.executeBatch();
                    }
                }

                conn.commit();
            } catch (SQLException ex) {
                log(Level.WARNING, "[Advancements] Error writing progress batch to SQLite: " + ex.getMessage(), ex);
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    // ignore
                }
            } finally {
                try {
                    conn.setAutoCommit(initialAutoCommit);
                } catch (SQLException ex) {
                    // ignore
                }
            }
        }
    }

    /**
     * Closes the store, stops the periodic flush task, and flushes any pending state synchronously.
     */
    public void close() {
        if (flushTask != null) {
            try {
                flushTask.cancel();
            } catch (Throwable ignored) {
            }
            flushTask = null;
        }
        flushSync();
    }

    /**
     * Package-private getter for testing cache contents.
     */
    Map<ProgressKey, Integer> getCache() {
        return Collections.unmodifiableMap(cache);
    }

    private Connection getConnection() {
        try {
            Connection conn = connectionSupplier != null ? connectionSupplier.get() : null;
            if (conn != null && !conn.isClosed()) {
                return conn;
            }
        } catch (SQLException ex) {
            log(Level.WARNING, "[Advancements] Connection check failed: " + ex.getMessage());
        }
        return null;
    }

    private void log(Level level, String msg) {
        log(level, msg, null);
    }

    private void log(Level level, String msg, Throwable t) {
        if (core != null && core.getLogger() != null) {
            if (t != null) {
                core.getLogger().log(level, msg, t);
            } else {
                core.getLogger().log(level, msg);
            }
        }
    }
}

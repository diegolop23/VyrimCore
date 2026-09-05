package net.vyrim.core.module.advancements;

import net.vyrim.core.VyrimCore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdvancementProgressStoreTest {

    @TempDir
    File tempDir;

    private File dbFile;
    private Connection connection;
    private VyrimCore mockCore;

    @BeforeEach
    void setUp() throws SQLException {
        dbFile = new File(tempDir, "test_advancements.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

        mockCore = mock(VyrimCore.class);
        when(mockCore.getLogger()).thenReturn(Logger.getLogger("AdvancementProgressStoreTest"));
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("init() creates advancement_progress table and loads empty cache")
    void testInitCreatesTable() throws SQLException {
        AdvancementProgressStore store = new AdvancementProgressStore(mockCore, () -> connection);
        store.init();

        // Verify table existence in SQLite
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='advancement_progress'")) {
            assertTrue(rs.next());
            assertEquals("advancement_progress", rs.getString("name"));
        }
    }

    @Test
    @DisplayName("incrementAndGet increments cumulative counter in memory and getProgress returns value")
    void testIncrementAndGet() {
        AdvancementProgressStore store = new AdvancementProgressStore(mockCore, () -> connection);
        store.init();

        UUID playerUuid = UUID.randomUUID();
        String advId = "mining_stone";

        assertEquals(0, store.getProgress(playerUuid, advId));

        int first = store.incrementAndGet(playerUuid, advId, 5);
        assertEquals(5, first);
        assertEquals(5, store.getProgress(playerUuid, advId));

        int second = store.incrementAndGet(playerUuid, advId, 10);
        assertEquals(15, second);
        assertEquals(15, store.getProgress(playerUuid, advId));
    }

    @Test
    @DisplayName("flushSync writes batch updates to SQLite and persists across store instances (server restart)")
    void testFlushAndRestartPersistence() throws SQLException {
        AdvancementProgressStore store1 = new AdvancementProgressStore(mockCore, () -> connection);
        store1.init();

        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        store1.incrementAndGet(player1, "mining_stone", 25);
        store1.incrementAndGet(player2, "kill_zombies", 12);

        // Synchronously flush to SQLite
        store1.flushSync();

        // Verify rows exist directly in SQLite database
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS total FROM advancement_progress")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt("total"));
        }

        // Simulate server restart: create a second store instance connected to the same SQLite db
        AdvancementProgressStore store2 = new AdvancementProgressStore(mockCore, () -> connection);
        store2.init(); // runs loadAll()

        assertEquals(25, store2.getProgress(player1, "mining_stone"));
        assertEquals(12, store2.getProgress(player2, "kill_zombies"));
    }

    @Test
    @DisplayName("reset() removes counter from cache and deletes row from SQLite database directly")
    void testResetClearsFromCacheAndDatabase() throws SQLException {
        AdvancementProgressStore store = new AdvancementProgressStore(mockCore, () -> connection);
        store.init();

        UUID playerUuid = UUID.randomUUID();
        String advId = "mining_stone";

        store.incrementAndGet(playerUuid, advId, 50);
        store.flushSync();

        // Verify row exists in DB
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT progress FROM advancement_progress WHERE player_uuid = ? AND advancement_id = ?")) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, advId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(50, rs.getInt("progress"));
            }
        }

        // Reset progress (advancement unlocked)
        store.reset(playerUuid, advId);
        assertEquals(0, store.getProgress(playerUuid, advId));

        store.flushSync();

        // Spot-check SQLite backend directly: row MUST be cleared!
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT progress FROM advancement_progress WHERE player_uuid = ? AND advancement_id = ?")) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, advId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertFalse(rs.next(), "Progress counter for already-unlocked advancement must be cleared from SQLite");
            }
        }
    }

    @Test
    @DisplayName("Null arguments and negative deltas are handled safely")
    void testNullAndEdgeCases() {
        AdvancementProgressStore store = new AdvancementProgressStore(mockCore, () -> connection);
        store.init();

        UUID uuid = UUID.randomUUID();

        assertEquals(0, store.incrementAndGet(null, "test", 1));
        assertEquals(0, store.incrementAndGet(uuid, null, 1));
        assertEquals(0, store.incrementAndGet(uuid, "test", -5));
        assertEquals(0, store.incrementAndGet(uuid, "test", 0));

        assertDoesNotThrow(() -> store.reset(null, "test"));
        assertDoesNotThrow(() -> store.reset(uuid, null));
        assertEquals(0, store.getProgress(null, "test"));
        assertEquals(0, store.getProgress(uuid, null));
    }
}

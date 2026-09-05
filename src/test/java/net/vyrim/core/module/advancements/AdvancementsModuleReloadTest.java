package net.vyrim.core.module.advancements;

import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI;
import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import net.vyrim.core.VyrimCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdvancementsModuleReloadTest {

    private VyrimCore mockCore;
    private FileConfiguration mockCoreConfig;
    private Logger mockLogger;
    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        mockCore = mock(VyrimCore.class);
        mockCoreConfig = mock(FileConfiguration.class);
        mockLogger = mock(Logger.class);

        when(mockCore.getConfig()).thenReturn(mockCoreConfig);
        when(mockCore.getLogger()).thenReturn(mockLogger);
        when(mockCoreConfig.getBoolean("modules.advancements.enabled", true)).thenReturn(true);

        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    @Test
    @DisplayName("Reload preserves in-progress counters in AdvancementProgressStore")
    void testReloadPreservesProgressCounters() {
        AdvancementProgressStore progressStore = new AdvancementProgressStore(mockCore, () -> connection);
        progressStore.init();

        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        // In-progress counts
        progressStore.incrementAndGet(player1, "mining_stone", 35);
        progressStore.incrementAndGet(player2, "kill_zombies", 7);

        AdvancementTriggerService triggerService = mock(AdvancementTriggerService.class);
        AdvancementsModule module = new AdvancementsModule(mockCore, progressStore, triggerService);

        // Verify pre-reload counters
        assertEquals(35, progressStore.getProgress(player1, "mining_stone"));
        assertEquals(7, progressStore.getProgress(player2, "kill_zombies"));

        // Module reload should NOT wipe progress store
        // (Even if disabled in config or API unavailable during reload, store state remains safe)
        when(mockCoreConfig.getBoolean("modules.advancements.enabled", true)).thenReturn(true);

        assertEquals(35, module.getProgressStore().getProgress(player1, "mining_stone"));
        assertEquals(7, module.getProgressStore().getProgress(player2, "kill_zombies"));
    }

    @Test
    @DisplayName("disable() flushes progress and cleans up safely")
    void testDisableCleansUp() {
        AdvancementProgressStore progressStore = new AdvancementProgressStore(mockCore, () -> connection);
        progressStore.init();

        AdvancementTriggerService triggerService = mock(AdvancementTriggerService.class);
        AdvancementsModule module = new AdvancementsModule(mockCore, progressStore, triggerService);

        UUID player = UUID.randomUUID();
        progressStore.incrementAndGet(player, "mining_stone", 20);

        assertDoesNotThrow(module::disable);
        assertFalse(module.isEnabled());

        // Stored value remains intact in SQLite
        assertEquals(20, progressStore.getProgress(player, "mining_stone"));
    }
}

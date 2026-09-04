package net.vyrim.core.module.biomecompass;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.vyrim.core.VyrimCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BiomeCompassCooldownTest {

    @Test
    @DisplayName("Cooldown lifecycle: set, check, remaining seconds, clear")
    void testCooldownLifecycle() {
        VyrimCore mockCore = mock(VyrimCore.class);
        FileConfiguration mockConfig = mock(FileConfiguration.class);
        when(mockCore.getConfig()).thenReturn(mockConfig);
        when(mockConfig.getInt("modules.biome_compass.cooldown", 30)).thenReturn(10);

        BiomeCompassModule module = new BiomeCompassModule(mockCore, null);
        UUID uuid = UUID.randomUUID();

        assertFalse(module.isOnCooldown(uuid));
        assertEquals(0, module.getRemainingCooldownSeconds(uuid));

        module.setCooldown(uuid);
        assertTrue(module.isOnCooldown(uuid));
        long remaining = module.getRemainingCooldownSeconds(uuid);
        assertTrue(remaining > 0 && remaining <= 10, "Remaining seconds should be between 1 and 10, got: " + remaining);

        module.clearCooldown(uuid);
        assertFalse(module.isOnCooldown(uuid));
        assertEquals(0, module.getRemainingCooldownSeconds(uuid));
    }

    @Test
    @DisplayName("Expired cooldown cleanup prunes old records")
    void testExpiredCleanup() {
        VyrimCore mockCore = mock(VyrimCore.class);
        FileConfiguration mockConfig = mock(FileConfiguration.class);
        when(mockCore.getConfig()).thenReturn(mockConfig);
        when(mockConfig.getInt("modules.biome_compass.cooldown", 30)).thenReturn(0);

        BiomeCompassModule module = new BiomeCompassModule(mockCore, null);
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();

        // If cooldown duration is 0, setCooldown should not store any entries
        module.setCooldown(uuid1);
        assertFalse(module.isOnCooldown(uuid1));

        // When cooldown is positive, reload configuration
        when(mockConfig.getInt("modules.biome_compass.cooldown", 30)).thenReturn(5);
        module.reload(mockCore);
        module.setCooldown(uuid2);
        assertTrue(module.isOnCooldown(uuid2));

        // Calling cleanup shouldn't remove active entry
        module.cleanupExpiredCooldowns();
        assertTrue(module.isOnCooldown(uuid2));
    }

    @Test
    @DisplayName("Cooldown message formatting replaces %time% and %seconds% with MiniMessage support")
    void testMessageFormatting() {
        VyrimCore mockCore = mock(VyrimCore.class);
        FileConfiguration mockConfig = mock(FileConfiguration.class);
        when(mockCore.getConfig()).thenReturn(mockConfig);
        when(mockConfig.getString(eq("modules.biome_compass.messages.cooldown"), anyString()))
                .thenReturn("<red>You must wait <yellow>%seconds%s</yellow> before scanning again!</red>");

        BiomeCompassModule module = new BiomeCompassModule(mockCore, null);
        Component component = module.formatCooldownMessage(15);

        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        assertEquals("You must wait 15s before scanning again!", plain);
    }

    @Test
    @DisplayName("Module reload updates cooldown duration, radius, and message templates")
    void testModuleReload() {
        VyrimCore mockCore = mock(VyrimCore.class);
        FileConfiguration mockConfig = mock(FileConfiguration.class);
        when(mockCore.getConfig()).thenReturn(mockConfig);
        when(mockConfig.getInt("modules.biome_compass.cooldown", 30)).thenReturn(30);
        when(mockConfig.getInt("modules.biome_compass.search_radius", 6400)).thenReturn(6400);

        BiomeCompassModule module = new BiomeCompassModule(mockCore, null);
        assertEquals(30, module.getCooldownSeconds());
        assertEquals(6400, module.getSearchRadius());

        // Update config values on disk
        when(mockConfig.getInt("modules.biome_compass.cooldown", 30)).thenReturn(60);
        when(mockConfig.getInt("modules.biome_compass.search_radius", 6400)).thenReturn(3200);

        module.reload(mockCore);
        assertEquals(60, module.getCooldownSeconds());
        assertEquals(3200, module.getSearchRadius());
    }
}

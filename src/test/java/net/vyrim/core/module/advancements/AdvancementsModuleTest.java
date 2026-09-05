package net.vyrim.core.module.advancements;

import net.vyrim.core.VyrimCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdvancementsModuleTest {

    @Test
    @DisplayName("AdvancementsModule reports correct name and config key")
    void testModuleNameAndKey() {
        VyrimCore mockCore = mock(VyrimCore.class);
        AdvancementsModule module = new AdvancementsModule(mockCore);
        assertEquals(AdvancementsModule.MODULE_NAME, module.name());
        assertEquals("advancements", module.configKey());
        assertFalse(module.isEnabled());
    }

    @Test
    @DisplayName("isAvailable returns false gracefully when disabled in config")
    void testDisabledInConfig() {
        VyrimCore mockCore = mock(VyrimCore.class);
        FileConfiguration mockConfig = mock(FileConfiguration.class);
        Logger mockLogger = mock(Logger.class);

        when(mockCore.getConfig()).thenReturn(mockConfig);
        when(mockCore.getLogger()).thenReturn(mockLogger);
        when(mockConfig.getBoolean("modules.advancements.enabled", true)).thenReturn(false);

        AdvancementsModule module = new AdvancementsModule(mockCore);
        assertFalse(module.isAvailable(mockCore));
        verify(mockLogger).info(contains("disabled in config.yml"));
    }

    @Test
    @DisplayName("isAvailable returns false gracefully when UltimateAdvancementAPI is missing")
    void testMissingUltimateAdvancementAPI() {
        VyrimCore mockCore = mock(VyrimCore.class);
        FileConfiguration mockConfig = mock(FileConfiguration.class);
        Logger mockLogger = mock(Logger.class);

        when(mockCore.getConfig()).thenReturn(mockConfig);
        when(mockCore.getLogger()).thenReturn(mockLogger);
        when(mockConfig.getBoolean("modules.advancements.enabled", true)).thenReturn(true);

        AdvancementsModule module = new AdvancementsModule(mockCore);
        // Bukkit.getPluginManager() is null or returns false
        assertFalse(module.isAvailable(mockCore));
    }

    @Test
    @DisplayName("disable() can be called safely when module is not active")
    void testSafeDisable() {
        VyrimCore mockCore = mock(VyrimCore.class);
        AdvancementsModule module = new AdvancementsModule(mockCore);
        assertDoesNotThrow(module::disable);
        assertFalse(module.isEnabled());
    }

    @Test
    @DisplayName("registerListeners degrades gracefully when MMOItems is absent")
    void testRegisterListenersWithoutMMOItems() {
        VyrimCore mockCore = mock(VyrimCore.class);
        Logger mockLogger = mock(Logger.class);
        when(mockCore.getLogger()).thenReturn(mockLogger);

        org.bukkit.Server mockServer = mock(org.bukkit.Server.class);
        org.bukkit.plugin.PluginManager mockPm = mock(org.bukkit.plugin.PluginManager.class);
        when(mockServer.getPluginManager()).thenReturn(mockPm);
        try {
            java.lang.reflect.Field serverField = org.bukkit.Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, mockServer);
        } catch (Throwable ignored) {
        }

        net.vyrim.core.hook.MMOItemsHook mockHook = mock(net.vyrim.core.hook.MMOItemsHook.class);
        when(mockHook.isFullyAvailable()).thenReturn(false);
        when(mockCore.getMMOItemsHook()).thenReturn(mockHook);

        AdvancementsModule module = new AdvancementsModule(mockCore);
        module.registerListeners();

        // 9 native listeners registered, 0 MMOItems listeners
        assertEquals(9, module.getRegisteredListeners().size());
        verify(mockLogger).info(contains("MMOItems is not installed or enabled"));
    }

    @Test
    @DisplayName("registerListeners activates MMOItems listeners when MMOItems is active")
    void testRegisterListenersWithMMOItems() {
        VyrimCore mockCore = mock(VyrimCore.class);
        Logger mockLogger = mock(Logger.class);
        when(mockCore.getLogger()).thenReturn(mockLogger);

        org.bukkit.Server mockServer = mock(org.bukkit.Server.class);
        org.bukkit.plugin.PluginManager mockPm = mock(org.bukkit.plugin.PluginManager.class);
        when(mockServer.getPluginManager()).thenReturn(mockPm);
        try {
            java.lang.reflect.Field serverField = org.bukkit.Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, mockServer);
        } catch (Throwable ignored) {
        }

        net.vyrim.core.hook.MMOItemsHook mockHook = mock(net.vyrim.core.hook.MMOItemsHook.class);
        when(mockHook.isFullyAvailable()).thenReturn(true);
        when(mockCore.getMMOItemsHook()).thenReturn(mockHook);

        AdvancementsModule module = new AdvancementsModule(mockCore);
        module.registerListeners();

        // 9 native listeners + 3 MMOItems listeners = 12 listeners
        assertEquals(12, module.getRegisteredListeners().size());
        verify(mockLogger).info(contains("Activated MMOItems trigger listeners"));
    }
}

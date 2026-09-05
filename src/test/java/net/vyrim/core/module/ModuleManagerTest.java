package net.vyrim.core.module;

import net.vyrim.core.VyrimCore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModuleManagerTest {

    private static class TestModule implements Module {
        private final String name;
        private boolean available;
        private boolean configEnabled = true;
        private boolean enabled = false;
        private int reloadCount = 0;

        TestModule(String name, boolean available) {
            this.name = name;
            this.available = available;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean isAvailable(VyrimCore core) {
            return available;
        }

        @Override
        public boolean isConfigEnabled(VyrimCore core) {
            return configEnabled;
        }

        @Override
        public void onEnable(VyrimCore plugin) {
            this.enabled = true;
        }

        @Override
        public void onDisable() {
            this.enabled = false;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void reload(VyrimCore core) {
            reloadCount++;
            Module.super.reload(core);
        }
    }

    @Test
    @DisplayName("ModuleManager correctly tracks registrations and lookups")
    void testRegistrationAndLookup() {
        VyrimCore mockCore = mock(VyrimCore.class);
        when(mockCore.getLogger()).thenReturn(Logger.getAnonymousLogger());

        ModuleManager manager = new ModuleManager(mockCore);
        TestModule modA = new TestModule("Alpha", true);
        TestModule modB = new TestModule("Beta", false);

        manager.register(modA);
        manager.register(modB);

        assertEquals(2, manager.getRegisteredModules().size());
        assertEquals(List.of("Alpha", "Beta"), manager.getRegisteredModuleNames());

        Optional<Module> found = manager.getModule("alpha");
        assertTrue(found.isPresent());
        assertEquals("Alpha", found.get().name());

        Optional<Module> foundUpper = manager.getModule("BETA");
        assertTrue(foundUpper.isPresent());
        assertEquals("Beta", foundUpper.get().name());

        assertTrue(manager.getModule("Gamma").isEmpty());
    }

    @Test
    @DisplayName("ModuleManager enables available modules and skips unavailable ones")
    void testEnableAndDisableAll() {
        VyrimCore mockCore = mock(VyrimCore.class);
        when(mockCore.getLogger()).thenReturn(Logger.getAnonymousLogger());

        ModuleManager manager = new ModuleManager(mockCore);
        TestModule modA = new TestModule("Alpha", true);
        TestModule modB = new TestModule("Beta", false);

        manager.register(modA);
        manager.register(modB);

        manager.enableAll();

        assertTrue(modA.isEnabled());
        assertFalse(modB.isEnabled());
        assertEquals(1, manager.getEnabledModules().size());
        assertTrue(manager.getEnabledModules().contains(modA));

        manager.disableAll();
        assertFalse(modA.isEnabled());
        assertTrue(manager.getEnabledModules().isEmpty());
    }

    @Test
    @DisplayName("ModuleManager dynamically enables, disables, and reloads modules based on config")
    void testReloadModule() {
        VyrimCore mockCore = mock(VyrimCore.class);
        when(mockCore.getLogger()).thenReturn(Logger.getAnonymousLogger());

        ModuleManager manager = new ModuleManager(mockCore);
        TestModule modA = new TestModule("Alpha", true);
        manager.register(modA);
        manager.enableAll();
        assertTrue(modA.isEnabled());

        // 1. Config enabled = true, module enabled = true -> calls module.reload()
        boolean reloaded = manager.reload("alpha");
        assertTrue(reloaded);
        assertEquals(1, modA.reloadCount);
        assertTrue(modA.isEnabled());
        assertTrue(manager.getEnabledModules().contains(modA));

        // 2. Config enabled = false, module enabled = true -> calls module.disable()
        modA.configEnabled = false;
        manager.reload(modA);
        assertFalse(modA.isEnabled());
        assertFalse(manager.getEnabledModules().contains(modA));

        // 3. Config enabled = true, module enabled = false -> calls module.enable()
        modA.configEnabled = true;
        manager.reload(modA);
        assertTrue(modA.isEnabled());
        assertTrue(manager.getEnabledModules().contains(modA));

        // Reload nonexistent
        assertFalse(manager.reload("NonExistent"));
    }

    @Test
    @DisplayName("ModuleManager reloads all registered modules")
    void testReloadAll() {
        VyrimCore mockCore = mock(VyrimCore.class);
        when(mockCore.getLogger()).thenReturn(Logger.getAnonymousLogger());

        ModuleManager manager = new ModuleManager(mockCore);
        TestModule modA = new TestModule("Alpha", true);
        TestModule modB = new TestModule("Beta", true);
        manager.register(modA);
        manager.register(modB);

        manager.enableAll();
        manager.reloadAll();

        assertEquals(1, modA.reloadCount);
        assertEquals(1, modB.reloadCount);
        assertTrue(modA.isEnabled());
        assertTrue(modB.isEnabled());
    }
}

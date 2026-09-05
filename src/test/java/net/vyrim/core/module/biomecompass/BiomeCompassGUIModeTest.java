package net.vyrim.core.module.biomecompass;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.vyrim.core.VyrimCore;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BiomeCompassGUIModeTest {

    private VyrimCore mockCore;
    private FileConfiguration mockConfig;
    private BiomeCompassModule module;
    private BiomeLocatorService mockLocator;
    private TestableGUI gui;
    private Player mockPlayer;
    private UUID playerUuid;

    @BeforeEach
    void setUp() {
        mockCore = mock(VyrimCore.class);
        when(mockCore.getName()).thenReturn("VyrimCore");
        mockConfig = mock(FileConfiguration.class);
        when(mockCore.getConfig()).thenReturn(mockConfig);

        module = new BiomeCompassModule(mockCore, null);
        mockLocator = mock(BiomeLocatorService.class);
        when(mockLocator.isPlaySounds()).thenReturn(true);

        gui = new TestableGUI(mockCore, mockLocator, module);

        playerUuid = UUID.randomUUID();
        mockPlayer = mock(Player.class);
        when(mockPlayer.getUniqueId()).thenReturn(playerUuid);
        when(mockPlayer.getLocation()).thenReturn(new Location(null, 0, 64, 0));
    }

    @Test
    @DisplayName("Mode button creates Compass icon for Nearest mode with correct metadata")
    void testModeButtonNearest() {
        // Default mode is Nearest (false)
        assertFalse(module.isIgnoreCurrentBiomeMode(playerUuid));

        MockItemBundle compassBundle = gui.registerMock(Material.COMPASS);
        ItemStack item = gui.createModeToggleItem(mockPlayer);

        assertNotNull(item);
        assertEquals(Material.COMPASS, item.getType());

        String displayName = PlainTextComponentSerializer.plainText().serialize(compassBundle.displayName);
        assertTrue(displayName.contains("Nearest"), "Name should indicate Nearest mode, got: " + displayName);

        assertNotNull(compassBundle.lore);
        String fullLore = compassBundle.lore.stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .reduce("", (a, b) -> a + " " + b);
        assertTrue(fullLore.contains("Always returns the closest match"), "Lore should explain Nearest mode, got: " + fullLore);

        assertEquals(BiomeCompassGUI.ACTION_TOGGLE_MODE, compassBundle.pdcMap.get(gui.getPdcActionKey()));
    }

    @Test
    @DisplayName("Mode button creates Recovery Compass icon for Ignore Current Biome mode with correct metadata")
    void testModeButtonIgnoreCurrentBiome() {
        module.toggleIgnoreCurrentBiomeMode(playerUuid);
        assertTrue(module.isIgnoreCurrentBiomeMode(playerUuid));

        MockItemBundle recoveryBundle = gui.registerMock(Material.RECOVERY_COMPASS);
        ItemStack item = gui.createModeToggleItem(mockPlayer);

        assertNotNull(item);
        assertEquals(Material.RECOVERY_COMPASS, item.getType());

        String displayName = PlainTextComponentSerializer.plainText().serialize(recoveryBundle.displayName);
        assertTrue(displayName.contains("Ignore Current Biome"), "Name should indicate Ignore mode, got: " + displayName);

        assertNotNull(recoveryBundle.lore);
        String fullLore = recoveryBundle.lore.stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .reduce("", (a, b) -> a + " " + b);
        assertTrue(fullLore.contains("Skips the patch you're standing in"), "Lore should explain Ignore mode, got: " + fullLore);

        assertEquals(BiomeCompassGUI.ACTION_TOGGLE_MODE, compassBundleOrMap(recoveryBundle));
    }

    private String compassBundleOrMap(MockItemBundle bundle) {
        return (String) bundle.pdcMap.get(gui.getPdcActionKey());
    }

    @Test
    @DisplayName("Clicking ACTION_TOGGLE_MODE flips mode, plays click sound, and does not close inventory")
    void testClickModeToggle() {
        assertFalse(module.isIgnoreCurrentBiomeMode(playerUuid));

        MockItemBundle toggleBundle = gui.registerMock(Material.COMPASS);
        toggleBundle.pdcMap.put(gui.getPdcActionKey(), BiomeCompassGUI.ACTION_TOGGLE_MODE);

        BiomeCompassHolder holder = new BiomeCompassHolder(playerUuid, World.Environment.NORMAL, 0,
                EquipmentSlot.HAND, 0, 1);
        Inventory mockInventory = mock(Inventory.class);
        when(mockInventory.getHolder()).thenReturn(holder);

        InventoryView mockView = mock(InventoryView.class);
        when(mockView.getTopInventory()).thenReturn(mockInventory);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(mockView);
        when(event.getClickedInventory()).thenReturn(mockInventory);
        when(event.getCurrentItem()).thenReturn(toggleBundle.item);
        when(event.getWhoClicked()).thenReturn(mockPlayer);

        gui.onInventoryClick(event);

        // Mode must have toggled from false to true
        assertTrue(module.isIgnoreCurrentBiomeMode(playerUuid));

        // UI click sound must have been played
        assertTrue(gui.clickSoundPlayed, "Click sound should have been triggered");

        // Page should have been re-rendered
        assertEquals(1, gui.openPageCallCount, "Current page should be re-rendered");

        // Inventory must NOT be closed
        verify(mockPlayer, never()).closeInventory();
    }

    private static class TestableGUI extends BiomeCompassGUI {
        private final Map<Material, MockItemBundle> bundles = new HashMap<>();
        int openPageCallCount = 0;
        boolean clickSoundPlayed = false;

        public TestableGUI(VyrimCore core, BiomeLocatorService locator, BiomeCompassModule module) {
            super(core, locator, module);
        }

        @Override
        void playClickSound(Player player) {
            clickSoundPlayed = true;
        }

        public MockItemBundle registerMock(Material material) {
            MockItemBundle bundle = new MockItemBundle();
            bundle.item = mock(ItemStack.class);
            bundle.meta = mock(ItemMeta.class);
            bundle.pdc = mock(PersistentDataContainer.class);
            when(bundle.item.getType()).thenReturn(material);
            when(bundle.item.hasItemMeta()).thenReturn(true);
            when(bundle.item.getItemMeta()).thenReturn(bundle.meta);
            when(bundle.meta.getPersistentDataContainer()).thenReturn(bundle.pdc);

            when(bundle.pdc.get(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
                    .thenAnswer(inv -> bundle.pdcMap.get(inv.getArgument(0)));
            when(bundle.pdc.has(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
                    .thenAnswer(inv -> bundle.pdcMap.containsKey(inv.getArgument(0)));

            doAnswer(inv -> {
                bundle.pdcMap.put(inv.getArgument(0), inv.getArgument(2));
                return null;
            }).when(bundle.pdc).set(any(NamespacedKey.class), eq(PersistentDataType.STRING), anyString());

            doAnswer(inv -> {
                bundle.displayName = inv.getArgument(0);
                return null;
            }).when(bundle.meta).displayName(any());
            when(bundle.meta.displayName()).thenAnswer(inv -> bundle.displayName);

            doAnswer(inv -> {
                bundle.lore = inv.getArgument(0);
                return null;
            }).when(bundle.meta).lore(any());
            when(bundle.meta.lore()).thenAnswer(inv -> bundle.lore);

            bundles.put(material, bundle);
            return bundle;
        }

        @Override
        ItemStack createItemStack(Material material) {
            MockItemBundle bundle = bundles.get(material);
            return bundle != null ? bundle.item : new ItemStack(material);
        }

        @Override
        public void openPage(Player player, int page, EquipmentSlot hand, int slot, int tier) {
            openPageCallCount++;
        }
    }

    private static class MockItemBundle {
        ItemStack item;
        ItemMeta meta;
        PersistentDataContainer pdc;
        Map<NamespacedKey, Object> pdcMap = new HashMap<>();
        Component displayName;
        List<Component> lore;
    }
}

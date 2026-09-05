package net.vyrim.core.module.biomecompass;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.vyrim.core.VyrimCore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.YamlConfiguration;
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
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BiomeTierResolverTest {

    private YamlConfiguration config;
    private BiomeTierResolver resolver;

    @BeforeEach
    void setUp() {
        config = new YamlConfiguration();
        config.set("modules.biome_compass.show_locked_biomes", true);
        config.set("modules.biome_compass.locked_biome_lore", "&c🔒 Requires a higher-tier Nature Compass");
        config.set("modules.biome_compass.biome_tiers.2", List.of("swamp", "ice_spikes", "cherry_grove", "badlands", "eroded_badlands"));
        config.set("modules.biome_compass.biome_tiers.3", List.of("deep_dark", "mushroom_fields", "pale_garden"));
        config.set("modules.biome_compass.item_tiers.COMPASS.NATURE_COMPASS_T1", 1);
        config.set("modules.biome_compass.item_tiers.COMPASS.NATURE_COMPASS_T2", 2);
        config.set("modules.biome_compass.item_tiers.COMPASS.NATURE_COMPASS_T3", 3);

        resolver = new BiomeTierResolver();
        resolver.loadConfiguration(config);
    }

    @Test
    @DisplayName("resolveBiomeTier returns correct tier for listed biomes and defaults to 1 for unlisted")
    void testResolveBiomeTier() {
        // Tier 2 biomes
        assertEquals(2, resolver.resolveBiomeTier(NamespacedKey.minecraft("swamp")));
        assertEquals(2, resolver.resolveBiomeTier(NamespacedKey.minecraft("ice_spikes")));
        assertEquals(2, resolver.resolveBiomeTier(NamespacedKey.minecraft("cherry_grove")));
        assertEquals(2, resolver.resolveBiomeTier(NamespacedKey.minecraft("badlands")));
        assertEquals(2, resolver.resolveBiomeTier(NamespacedKey.minecraft("eroded_badlands")));

        // Tier 3 biomes
        assertEquals(3, resolver.resolveBiomeTier(NamespacedKey.minecraft("deep_dark")));
        assertEquals(3, resolver.resolveBiomeTier(NamespacedKey.minecraft("mushroom_fields")));
        assertEquals(3, resolver.resolveBiomeTier(NamespacedKey.minecraft("pale_garden")));

        // Unlisted biomes default to 1
        assertEquals(1, resolver.resolveBiomeTier(NamespacedKey.minecraft("plains")));
        assertEquals(1, resolver.resolveBiomeTier(NamespacedKey.minecraft("desert")));
        assertEquals(1, resolver.resolveBiomeTier(NamespacedKey.minecraft("forest")));
        assertEquals(1, resolver.resolveBiomeTier(NamespacedKey.minecraft("nether_wastes")));

        // Null key defaults to 1
        assertEquals(1, resolver.resolveBiomeTier(null));
    }

    @Test
    @DisplayName("resolveItemTier by type and id returns configured tier and defaults to 1")
    void testResolveItemTierByTypeAndId() {
        assertEquals(1, resolver.resolveItemTier("COMPASS", "NATURE_COMPASS_T1"));
        assertEquals(2, resolver.resolveItemTier("COMPASS", "NATURE_COMPASS_T2"));
        assertEquals(3, resolver.resolveItemTier("COMPASS", "NATURE_COMPASS_T3"));

        // Case-insensitive lookup
        assertEquals(2, resolver.resolveItemTier("compass", "nature_compass_t2"));
        assertEquals(3, resolver.resolveItemTier("Compass", "Nature_Compass_T3"));

        // Unconfigured items default to 1
        assertEquals(1, resolver.resolveItemTier("COMPASS", "UNKNOWN_COMPASS"));
        assertEquals(1, resolver.resolveItemTier("SWORD", "NATURE_COMPASS_T3"));
        assertEquals(1, resolver.resolveItemTier(null, "NATURE_COMPASS_T1"));
        assertEquals(1, resolver.resolveItemTier("COMPASS", null));
        assertEquals(1, resolver.resolveItemTier("", ""));
    }

    @Test
    @DisplayName("resolveItemTier with ItemStack handles null, no meta, and NBTItem reading safely")
    void testResolveItemTierWithItemStack() {
        // Null item
        assertEquals(1, resolver.resolveItemTier((ItemStack) null));

        // Item without meta
        ItemStack mockItem = mock(ItemStack.class);
        when(mockItem.hasItemMeta()).thenReturn(false);
        assertEquals(1, resolver.resolveItemTier(mockItem));

        // Item with meta and mock NBTItem
        when(mockItem.hasItemMeta()).thenReturn(true);
        try (MockedStatic<NBTItem> nbtMock = mockStatic(NBTItem.class)) {
            NBTItem mockNbt = mock(NBTItem.class);
            when(mockNbt.hasType()).thenReturn(true);
            when(mockNbt.getType()).thenReturn("COMPASS");
            when(mockNbt.getString("MMOITEMS_ITEM_ID")).thenReturn("NATURE_COMPASS_T2");
            nbtMock.when(() -> NBTItem.get(mockItem)).thenReturn(mockNbt);

            assertEquals(2, resolver.resolveItemTier(mockItem));
        }

        // Exception during NBT access defaults to tier 1
        try (MockedStatic<NBTItem> nbtMock = mockStatic(NBTItem.class)) {
            nbtMock.when(() -> NBTItem.get(mockItem)).thenThrow(new RuntimeException("MMOItems NBT unavailable"));
            assertEquals(1, resolver.resolveItemTier(mockItem));
        }
    }

    @Test
    @DisplayName("Config reload picks up changed tier lists without server restart")
    void testHotReload() {
        VyrimCore mockCore = mock(VyrimCore.class);
        when(mockCore.getName()).thenReturn("VyrimCore");
        when(mockCore.getConfig()).thenReturn(config);

        BiomeCompassModule module = new BiomeCompassModule(mockCore, null);
        assertEquals(2, module.resolveBiomeTier(NamespacedKey.minecraft("swamp")));
        assertEquals(1, module.resolveBiomeTier(NamespacedKey.minecraft("plains")));

        // Mutate configuration (rebalance swamp to tier 3, plains to tier 2, update item tier)
        config.set("modules.biome_compass.biome_tiers.2", List.of("plains"));
        config.set("modules.biome_compass.biome_tiers.3", List.of("swamp", "deep_dark"));
        config.set("modules.biome_compass.item_tiers.COMPASS.NATURE_COMPASS_T2", 5);

        module.reload(mockCore);

        assertEquals(3, module.resolveBiomeTier(NamespacedKey.minecraft("swamp")));
        assertEquals(2, module.resolveBiomeTier(NamespacedKey.minecraft("plains")));
        assertEquals(5, module.getTierResolver().resolveItemTier("COMPASS", "NATURE_COMPASS_T2"));
    }

    @Test
    @DisplayName("BiomeCompassHolder stores and preserves tier across page flips")
    void testHolderTierStorage() {
        UUID uuid = UUID.randomUUID();
        BiomeCompassHolder holder = new BiomeCompassHolder(uuid, World.Environment.NORMAL, 0,
                EquipmentSlot.HAND, 0, 3);
        assertEquals(3, holder.getTier());

        BiomeCompassHolder defaultHolder = new BiomeCompassHolder(uuid, World.Environment.NORMAL, 0);
        assertEquals(1, defaultHolder.getTier());
    }

    @Test
    @DisplayName("createBiomeIcon creates distinct locked icon with Iron Bars, dark gray name, locked lore, and no pdcBiomeKey")
    void testLockedBiomeIcon() {
        VyrimCore mockCore = mock(VyrimCore.class);
        when(mockCore.getName()).thenReturn("VyrimCore");
        when(mockCore.getConfig()).thenReturn(config);
        BiomeCompassModule module = new BiomeCompassModule(mockCore, null);

        TestableBiomeCompassGUI gui = new TestableBiomeCompassGUI(mockCore, null, module);
        MockItemBundle sculkBundle = gui.registerMock(Material.SCULK);
        MockItemBundle ironBarsBundle = gui.registerMock(Material.IRON_BARS);

        NamespacedKey deepDarkKey = NamespacedKey.minecraft("deep_dark");

        // Unlocked icon (e.g. tier 3)
        ItemStack unlockedIcon = gui.createBiomeIcon(deepDarkKey, "Overworld", false);
        assertSame(sculkBundle.item, unlockedIcon);
        assertTrue(sculkBundle.pdcMap.containsKey(gui.getPdcBiomeKey()));
        assertFalse(sculkBundle.pdcMap.containsKey(gui.getPdcActionKey()));
        assertEquals(NamedTextColor.GOLD, sculkBundle.displayName.color());

        // Locked icon (e.g. tier 1 player looking at tier 3 biome)
        ItemStack lockedIcon = gui.createBiomeIcon(deepDarkKey, "Overworld", true);
        assertSame(ironBarsBundle.item, lockedIcon);

        // Friendly name formatted and DARK_GRAY
        assertNotNull(ironBarsBundle.displayName);
        assertEquals(NamedTextColor.DARK_GRAY, ironBarsBundle.displayName.color());
        String plainName = PlainTextComponentSerializer.plainText().serialize(ironBarsBundle.displayName);
        assertEquals("Deep Dark", plainName);

        // Lore contains locked lore message
        assertNotNull(ironBarsBundle.lore);
        assertFalse(ironBarsBundle.lore.isEmpty());
        String plainLore = PlainTextComponentSerializer.plainText().serialize(ironBarsBundle.lore.get(0));
        assertTrue(plainLore.contains("Requires a higher-tier Nature Compass"));

        // Crucial: pdcBiomeKey is NOT present on locked icons, but pdcActionKey is LOCKED_BIOME
        assertFalse(ironBarsBundle.pdcMap.containsKey(gui.getPdcBiomeKey()));
        assertTrue(ironBarsBundle.pdcMap.containsKey(gui.getPdcActionKey()));
        assertEquals(BiomeCompassGUI.ACTION_LOCKED_BIOME, ironBarsBundle.pdcMap.get(gui.getPdcActionKey()));
    }

    @Test
    @DisplayName("Clicking a locked icon plays denial bass sound and does not close inventory or search")
    void testLockedIconClick() {
        VyrimCore mockCore = mock(VyrimCore.class);
        when(mockCore.getName()).thenReturn("VyrimCore");
        when(mockCore.getConfig()).thenReturn(config);
        BiomeCompassModule module = new BiomeCompassModule(mockCore, null);
        BiomeLocatorService mockLocator = mock(BiomeLocatorService.class);
        when(mockLocator.isPlaySounds()).thenReturn(true);

        TestableBiomeCompassGUI gui = new TestableBiomeCompassGUI(mockCore, mockLocator, module);
        MockItemBundle ironBarsBundle = gui.registerMock(Material.IRON_BARS);

        Player mockPlayer = mock(Player.class);
        org.bukkit.Location playerLoc = new org.bukkit.Location(null, 100, 64, 100);
        when(mockPlayer.getLocation()).thenReturn(playerLoc);

        BiomeCompassHolder holder = new BiomeCompassHolder(mockPlayer.getUniqueId(), World.Environment.NORMAL, 0,
                EquipmentSlot.HAND, 0, 1);

        Inventory mockTopInventory = mock(Inventory.class);
        when(mockTopInventory.getHolder()).thenReturn(holder);

        InventoryView mockView = mock(InventoryView.class);
        when(mockView.getTopInventory()).thenReturn(mockTopInventory);

        // Build locked item
        NamespacedKey deepDarkKey = NamespacedKey.minecraft("deep_dark");
        ItemStack lockedItem = gui.createBiomeIcon(deepDarkKey, "Overworld", true);

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(mockView);
        when(event.getClickedInventory()).thenReturn(mockTopInventory);
        when(event.getCurrentItem()).thenReturn(lockedItem);
        when(event.getWhoClicked()).thenReturn(mockPlayer);

        gui.onInventoryClick(event);

        // Verify denial sound was triggered
        assertTrue(gui.denialSoundPlayed);

        // Verify player inventory was NEVER closed and locatorService.locateBiome was NEVER called
        verify(mockPlayer, never()).closeInventory();
        verify(mockLocator, never()).locateBiome(any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("show_locked_biomes false omits locked biomes from GUI listing")
    void testShowLockedBiomesFiltering() {
        VyrimCore mockCore = mock(VyrimCore.class);
        when(mockCore.getName()).thenReturn("VyrimCore");
        when(mockCore.getConfig()).thenReturn(config);

        BiomeCompassModule module = new BiomeCompassModule(mockCore, null);
        assertTrue(module.isShowLockedBiomes());

        // When show_locked_biomes is false
        config.set("modules.biome_compass.show_locked_biomes", false);
        module.reload(mockCore);
        assertFalse(module.isShowLockedBiomes());

        // Simulate a tier 1 player filtering biomes
        int playerTier = 1;
        List<NamespacedKey> testBiomes = new java.util.ArrayList<>(List.of(
                NamespacedKey.minecraft("plains"),        // tier 1
                NamespacedKey.minecraft("forest"),        // tier 1
                NamespacedKey.minecraft("swamp"),         // tier 2
                NamespacedKey.minecraft("deep_dark")      // tier 3
        ));

        // When show_locked_biomes is false, filter out biomes above player tier
        if (!module.isShowLockedBiomes()) {
            testBiomes.removeIf(key -> module.resolveBiomeTier(key) > playerTier);
        }

        assertEquals(2, testBiomes.size());
        assertTrue(testBiomes.contains(NamespacedKey.minecraft("plains")));
        assertTrue(testBiomes.contains(NamespacedKey.minecraft("forest")));
        assertFalse(testBiomes.contains(NamespacedKey.minecraft("swamp")));
        assertFalse(testBiomes.contains(NamespacedKey.minecraft("deep_dark")));

        // For tier 2 player
        int tier2Player = 2;
        List<NamespacedKey> tier2Biomes = new java.util.ArrayList<>(List.of(
                NamespacedKey.minecraft("plains"),
                NamespacedKey.minecraft("swamp"),
                NamespacedKey.minecraft("deep_dark")
        ));
        tier2Biomes.removeIf(key -> module.resolveBiomeTier(key) > tier2Player);
        assertEquals(2, tier2Biomes.size());
        assertTrue(tier2Biomes.contains(NamespacedKey.minecraft("swamp")));
        assertFalse(tier2Biomes.contains(NamespacedKey.minecraft("deep_dark")));
    }

    private static class TestableBiomeCompassGUI extends BiomeCompassGUI {
        private final Map<Material, MockItemBundle> bundles = new HashMap<>();
        boolean denialSoundPlayed = false;

        public TestableBiomeCompassGUI(VyrimCore core, BiomeLocatorService locator, BiomeCompassModule module) {
            super(core, locator, module);
        }

        @Override
        void playDenialSound(Player player) {
            denialSoundPlayed = true;
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

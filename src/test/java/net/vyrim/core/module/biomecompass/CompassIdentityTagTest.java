package net.vyrim.core.module.biomecompass;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.LodestoneTracker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.vyrim.core.VyrimCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BiomeSearchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompassIdentityTagTest {

    private VyrimCore mockCore;
    private FileConfiguration mockConfig;
    private BiomeLocatorService service;
    private Player mockPlayer;
    private PlayerInventory mockInventory;
    private ItemStack[] inventoryContents;
    private ItemStack[] offHandItem;
    private UUID playerUuid;
    private Location playerLoc;
    private World mockWorld;
    private WorldBorder mockBorder;
    private MockedStatic<Bukkit> bukkitMock;

    @BeforeEach
    void setUp() {
        mockCore = mock(VyrimCore.class);
        mockConfig = mock(FileConfiguration.class);
        when(mockCore.getConfig()).thenReturn(mockConfig);
        when(mockConfig.getInt("modules.biome_compass.radius", 6400)).thenReturn(6400);
        when(mockConfig.getBoolean("modules.biome_compass.play_sounds", true)).thenReturn(false);

        service = new BiomeLocatorService(mockCore);

        playerUuid = UUID.randomUUID();
        mockPlayer = mock(Player.class);
        when(mockPlayer.getUniqueId()).thenReturn(playerUuid);
        when(mockPlayer.isOnline()).thenReturn(true);

        mockWorld = mock(World.class);
        when(mockWorld.getName()).thenReturn("world");
        mockBorder = mock(WorldBorder.class);
        when(mockWorld.getWorldBorder()).thenReturn(mockBorder);
        when(mockBorder.getCenter()).thenReturn(new Location(mockWorld, 0, 64, 0));
        when(mockBorder.getSize()).thenReturn(10000.0);
        when(mockBorder.isInside(any())).thenReturn(true);

        playerLoc = new Location(mockWorld, 0, 64, 0);
        when(mockPlayer.getLocation()).thenReturn(playerLoc);

        mockInventory = mock(PlayerInventory.class);
        inventoryContents = new ItemStack[36];
        offHandItem = new ItemStack[1];

        when(mockPlayer.getInventory()).thenReturn(mockInventory);
        when(mockInventory.getSize()).thenReturn(36);
        when(mockInventory.getContents()).thenAnswer(inv -> inventoryContents);
        when(mockInventory.getHeldItemSlot()).thenReturn(0);

        when(mockInventory.getItem(anyInt())).thenAnswer(inv -> {
            int slot = inv.getArgument(0);
            return (slot >= 0 && slot < 36) ? inventoryContents[slot] : null;
        });
        doAnswer(inv -> {
            int slot = inv.getArgument(0);
            if (slot >= 0 && slot < 36) {
                inventoryContents[slot] = inv.getArgument(1);
            }
            return null;
        }).when(mockInventory).setItem(anyInt(), any());

        when(mockInventory.getItemInMainHand()).thenAnswer(inv -> inventoryContents[mockInventory.getHeldItemSlot()]);
        doAnswer(inv -> {
            inventoryContents[mockInventory.getHeldItemSlot()] = inv.getArgument(0);
            return null;
        }).when(mockInventory).setItemInMainHand(any());

        when(mockInventory.getItemInOffHand()).thenAnswer(inv -> offHandItem[0]);
        doAnswer(inv -> {
            offHandItem[0] = inv.getArgument(0);
            return null;
        }).when(mockInventory).setItemInOffHand(any());

        bukkitMock = mockStatic(Bukkit.class);
        bukkitMock.when(() -> Bukkit.getPlayer(playerUuid)).thenReturn(mockPlayer);
        bukkitMock.when(Bukkit::getOnlinePlayers).thenReturn(Collections.singletonList(mockPlayer));
    }

    @AfterEach
    void tearDown() {
        if (bukkitMock != null) {
            bukkitMock.close();
        }
    }

    private static class MockCompassBundle {
        ItemStack item;
        CompassMeta meta;
        PersistentDataContainer pdc;
        Map<NamespacedKey, Object> pdcMap = new HashMap<>();
    }

    private MockCompassBundle createCompass() {
        MockCompassBundle bundle = new MockCompassBundle();
        bundle.item = mock(ItemStack.class);
        when(bundle.item.getType()).thenReturn(Material.COMPASS);
        when(bundle.item.hasItemMeta()).thenReturn(true);

        bundle.meta = mock(CompassMeta.class);
        bundle.pdc = mock(PersistentDataContainer.class);

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
            bundle.pdcMap.remove(inv.getArgument(0));
            return null;
        }).when(bundle.pdc).remove(any(NamespacedKey.class));

        doAnswer(inv -> {
            Consumer consumer = inv.getArgument(0);
            consumer.accept(bundle.meta);
            return true;
        }).when(bundle.item).editMeta(any(Consumer.class));

        doAnswer(inv -> {
            Consumer consumer = inv.getArgument(1);
            consumer.accept(bundle.meta);
            return true;
        }).when(bundle.item).editMeta(eq(CompassMeta.class), any(Consumer.class));

        return bundle;
    }

    private BiomeSearchResult createMockSearchResult(Location target) {
        BiomeSearchResult result = mock(BiomeSearchResult.class);
        when(result.getLocation()).thenReturn(target);
        return result;
    }

    @Test
    @DisplayName("Item found in original slot is calibrated correctly and tag is removed")
    void testItemFoundInOriginalSlot() {
        MockCompassBundle compass = createCompass();
        inventoryContents[0] = compass.item;

        UUID marker = UUID.randomUUID();
        compass.pdcMap.put(service.getPdcSearchTag(), marker.toString());

        Location target = new Location(mockWorld, 100, 64, 200);
        BiomeSearchResult result = createMockSearchResult(target);

        service.handleSearchResult(playerUuid, NamespacedKey.minecraft("plains"), "Plains",
                playerLoc, result, 6400, marker, EquipmentSlot.HAND, 0);

        // Verify calibrated
        verify(compass.meta).setLodestone(target);
        verify(compass.meta).setLodestoneTracked(false);

        // Verify tag stripped
        assertFalse(compass.pdcMap.containsKey(service.getPdcSearchTag()), "pdcSearchTag should be stripped after calibration");
        assertSame(compass.item, inventoryContents[0]);
    }

    @Test
    @DisplayName("Item moved to different hotbar slot is located and calibrated there without duplication")
    void testItemMovedToDifferentSlot() {
        MockCompassBundle compass = createCompass();
        // Item was initially at slot 0, but player moved it to slot 5 during search delay
        inventoryContents[0] = null;
        inventoryContents[5] = compass.item;

        UUID marker = UUID.randomUUID();
        compass.pdcMap.put(service.getPdcSearchTag(), marker.toString());

        Location target = new Location(mockWorld, 150, 64, 300);
        BiomeSearchResult result = createMockSearchResult(target);

        // Cast snapshot had hand = HAND, slot = 0
        service.handleSearchResult(playerUuid, NamespacedKey.minecraft("cherry_grove"), "Cherry Grove",
                playerLoc, result, 6400, marker, EquipmentSlot.HAND, 0);

        // Verify original slot 0 was NOT overwritten or duplicated into
        assertNull(inventoryContents[0], "Slot 0 must remain null, no duplication!");

        // Verify item was calibrated in slot 5
        assertSame(compass.item, inventoryContents[5]);
        verify(compass.meta).setLodestone(target);
        assertFalse(compass.pdcMap.containsKey(service.getPdcSearchTag()), "pdcSearchTag should be stripped from moved item");
    }

    @Test
    @DisplayName("Item removed entirely aborts calibration and produces no items")
    void testItemRemovedEntirely() {
        // Inventory is completely empty
        UUID marker = UUID.randomUUID();
        Location target = new Location(mockWorld, 100, 64, 100);
        BiomeSearchResult result = createMockSearchResult(target);

        service.handleSearchResult(playerUuid, NamespacedKey.minecraft("desert"), "Desert",
                playerLoc, result, 6400, marker, EquipmentSlot.HAND, 0);

        // Verify error message sent
        ArgumentCaptor<Component> msgCaptor = ArgumentCaptor.forClass(Component.class);
        verify(mockPlayer).sendMessage(msgCaptor.capture());
        String text = PlainTextComponentSerializer.plainText().serialize(msgCaptor.getValue());
        assertTrue(text.contains("You are no longer holding a compass"), "Should notify player about missing compass");

        // Verify no items were set anywhere
        for (int i = 0; i < 36; i++) {
            assertNull(inventoryContents[i], "Slot " + i + " must remain empty");
        }
        assertNull(offHandItem[0], "Offhand must remain empty");
    }

    @Test
    @DisplayName("Two identical compasses held: only the tagged compass is modified")
    void testTwoCompassesOnlyTaggedIsModified() {
        MockCompassBundle compassA = createCompass();
        MockCompassBundle compassB = createCompass();

        inventoryContents[0] = compassA.item; // Untagged compass in slot 0
        inventoryContents[1] = compassB.item; // Tagged compass in slot 1

        UUID marker = UUID.randomUUID();
        compassB.pdcMap.put(service.getPdcSearchTag(), marker.toString());

        Location target = new Location(mockWorld, 50, 64, 50);
        BiomeSearchResult result = createMockSearchResult(target);

        // Pass cast-time slot 0 (even though slot 0 has compassA)
        service.handleSearchResult(playerUuid, NamespacedKey.minecraft("forest"), "Forest",
                playerLoc, result, 6400, marker, EquipmentSlot.HAND, 0);

        // Compass A in slot 0 must NOT be modified
        verify(compassA.meta, never()).setLodestone(any());
        assertFalse(compassA.pdcMap.containsKey(service.getPdcSearchTag()));

        // Compass B in slot 1 MUST be calibrated and its tag removed
        verify(compassB.meta).setLodestone(target);
        assertFalse(compassB.pdcMap.containsKey(service.getPdcSearchTag()), "Tag on compass B must be removed");
    }

    @Test
    @DisplayName("Exit path cleanup: Biome not found removes tag")
    void testTagRemovedOnBiomeNotFound() {
        MockCompassBundle compass = createCompass();
        inventoryContents[2] = compass.item;

        UUID marker = UUID.randomUUID();
        compass.pdcMap.put(service.getPdcSearchTag(), marker.toString());

        // Null search result -> not found
        service.handleSearchResult(playerUuid, NamespacedKey.minecraft("jungle"), "Jungle",
                playerLoc, null, 6400, marker, EquipmentSlot.HAND, 2);

        assertFalse(compass.pdcMap.containsKey(service.getPdcSearchTag()), "Tag should be removed when biome not found");
        verify(compass.meta, never()).setLodestone(any());
    }

    @Test
    @DisplayName("Exit path cleanup: Target outside border removes tag")
    void testTagRemovedOnTargetOutsideBorder() {
        MockCompassBundle compass = createCompass();
        inventoryContents[3] = compass.item;

        UUID marker = UUID.randomUUID();
        compass.pdcMap.put(service.getPdcSearchTag(), marker.toString());

        Location outsideTarget = new Location(mockWorld, 20000, 64, 20000);
        when(mockBorder.isInside(outsideTarget)).thenReturn(false);
        BiomeSearchResult result = createMockSearchResult(outsideTarget);

        service.handleSearchResult(playerUuid, NamespacedKey.minecraft("badlands"), "Badlands",
                playerLoc, result, 6400, marker, EquipmentSlot.HAND, 3);

        assertFalse(compass.pdcMap.containsKey(service.getPdcSearchTag()), "Tag should be removed when target is outside border");
        verify(compass.meta, never()).setLodestone(any());
    }

    @Test
    @DisplayName("Defensive sweep on shutdown strips all lingering tags from online players")
    void testDefensiveSweepOnShutdown() {
        MockCompassBundle lingeringCompass1 = createCompass();
        MockCompassBundle lingeringCompass2 = createCompass();
        lingeringCompass1.pdcMap.put(service.getPdcSearchTag(), UUID.randomUUID().toString());
        lingeringCompass2.pdcMap.put(service.getPdcSearchTag(), UUID.randomUUID().toString());

        inventoryContents[0] = lingeringCompass1.item;
        offHandItem[0] = lingeringCompass2.item;

        assertTrue(lingeringCompass1.pdcMap.containsKey(service.getPdcSearchTag()));
        assertTrue(lingeringCompass2.pdcMap.containsKey(service.getPdcSearchTag()));

        service.shutdown();

        assertFalse(lingeringCompass1.pdcMap.containsKey(service.getPdcSearchTag()), "Lingering tag 1 must be swept");
        assertFalse(lingeringCompass2.pdcMap.containsKey(service.getPdcSearchTag()), "Lingering tag 2 must be swept");
    }
}

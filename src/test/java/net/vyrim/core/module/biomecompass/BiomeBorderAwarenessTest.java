package net.vyrim.core.module.biomecompass;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.vyrim.core.VyrimCore;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.BiomeSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BiomeBorderAwarenessTest {

    private VyrimCore mockCore;
    private FileConfiguration mockConfig;
    private BiomeLocatorService service;

    @BeforeEach
    void setUp() {
        mockCore = mock(VyrimCore.class);
        mockConfig = mock(FileConfiguration.class);
        when(mockCore.getConfig()).thenReturn(mockConfig);
        service = new BiomeLocatorService(mockCore);
    }

    private int invokeClampRadiusToBorder(Location origin, int desiredRadius) throws Exception {
        Method method = BiomeLocatorService.class.getDeclaredMethod("clampRadiusToBorder", Location.class, int.class);
        method.setAccessible(true);
        return (int) method.invoke(service, origin, desiredRadius);
    }

    private void invokeHandleSearchResult(UUID playerUuid, NamespacedKey biomeKey, String friendlyName,
                                          Location playerLoc, BiomeSearchResult searchResult, int searchRadius,
                                          EquipmentSlot hand, int slot) throws Exception {
        Method method = BiomeLocatorService.class.getDeclaredMethod("handleSearchResult",
                UUID.class, NamespacedKey.class, String.class, Location.class,
                BiomeSearchResult.class, int.class, EquipmentSlot.class, int.class);
        method.setAccessible(true);
        method.invoke(service, playerUuid, biomeKey, friendlyName, playerLoc, searchResult, searchRadius, hand, slot);
    }

    @Test
    @DisplayName("clampRadiusToBorder limits search radius to distance to nearest border edge")
    void testClampRadiusToBorderLimits() throws Exception {
        World mockWorld = mock(World.class);
        WorldBorder mockBorder = mock(WorldBorder.class);
        when(mockWorld.getWorldBorder()).thenReturn(mockBorder);
        when(mockBorder.getCenter()).thenReturn(new Location(mockWorld, 0, 64, 0));
        when(mockBorder.getSize()).thenReturn(1000.0); // minX=-500, maxX=500, minZ=-500, maxZ=500

        // 1. Origin at center: max radius to border is 500
        Location centerLoc = new Location(mockWorld, 0, 64, 0);
        assertEquals(500, invokeClampRadiusToBorder(centerLoc, 6400));
        assertEquals(200, invokeClampRadiusToBorder(centerLoc, 200));

        // 2. Origin at (400, 64, 0): distEast is 100, distWest is 900, North/South is 500 -> nearest is 100
        Location nearEastLoc = new Location(mockWorld, 400, 64, 0);
        assertEquals(100, invokeClampRadiusToBorder(nearEastLoc, 6400));
        assertEquals(50, invokeClampRadiusToBorder(nearEastLoc, 50));

        // 3. Origin at (0, 64, -450): distNorth is 50 (-450 - (-500) = 50) -> nearest is 50
        Location nearNorthLoc = new Location(mockWorld, 0, 64, -450);
        assertEquals(50, invokeClampRadiusToBorder(nearNorthLoc, 100));

        // 4. Non-zero border center: center at (1000, 64, -2000), size=2000
        // minX=0, maxX=2000, minZ=-3000, maxZ=-1000
        when(mockBorder.getCenter()).thenReturn(new Location(mockWorld, 1000, 64, -2000));
        when(mockBorder.getSize()).thenReturn(2000.0);
        Location offsetLoc = new Location(mockWorld, 500, 64, -2800);
        // distWest = 500, distEast = 1500, distNorth = 200, distSouth = 1800 -> min = 200
        assertEquals(200, invokeClampRadiusToBorder(offsetLoc, 500));
    }

    @Test
    @DisplayName("clampRadiusToBorder returns <= 0 when origin is at or outside the border")
    void testClampRadiusToBorderOutsideOrNearEdge() throws Exception {
        World mockWorld = mock(World.class);
        WorldBorder mockBorder = mock(WorldBorder.class);
        when(mockWorld.getWorldBorder()).thenReturn(mockBorder);
        when(mockBorder.getCenter()).thenReturn(new Location(mockWorld, 0, 64, 0));
        when(mockBorder.getSize()).thenReturn(1000.0); // minX=-500, maxX=500

        // Sub-block distance: 499.5 -> distance to 500 is 0.5 -> floor is 0
        Location edgeLoc = new Location(mockWorld, 499.5, 64, 0);
        int clampedEdge = invokeClampRadiusToBorder(edgeLoc, 6400);
        assertTrue(clampedEdge <= 0, "Distance < 1 block should floor to <= 0, got: " + clampedEdge);
        assertEquals(0, clampedEdge);

        // Outside border: 510 -> distance to 500 is -10 -> floor is -10
        Location outsideLoc = new Location(mockWorld, 510, 64, 0);
        int clampedOutside = invokeClampRadiusToBorder(outsideLoc, 6400);
        assertTrue(clampedOutside <= 0, "Outside border should be <= 0, got: " + clampedOutside);
    }

    @Test
    @DisplayName("clampRadiusToBorder falls back to desiredRadius when world or border is null")
    void testClampRadiusToBorderNullSafety() throws Exception {
        assertEquals(6400, invokeClampRadiusToBorder(null, 6400));

        Location locNoWorld = new Location(null, 10, 64, 10);
        assertEquals(6400, invokeClampRadiusToBorder(locNoWorld, 6400));

        World mockWorld = mock(World.class);
        when(mockWorld.getWorldBorder()).thenReturn(null);
        Location locNoBorder = new Location(mockWorld, 10, 64, 10);
        assertEquals(6400, invokeClampRadiusToBorder(locNoBorder, 6400));
    }

    @Test
    @DisplayName("locateBiome sends too_close_to_border message and aborts when clamped radius is <= 0")
    void testLocateBiomeAbortsTooCloseToBorder() {
        World mockWorld = mock(World.class);
        WorldBorder mockBorder = mock(WorldBorder.class);
        when(mockWorld.getWorldBorder()).thenReturn(mockBorder);
        when(mockBorder.getCenter()).thenReturn(new Location(mockWorld, 0, 64, 0));
        when(mockBorder.getSize()).thenReturn(1000.0);

        Player mockPlayer = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(mockPlayer.getUniqueId()).thenReturn(uuid);
        PlayerInventory mockInv = mock(PlayerInventory.class);
        when(mockPlayer.getInventory()).thenReturn(mockInv);
        when(mockInv.getHeldItemSlot()).thenReturn(0);

        // Position player 0.2 blocks from border (clamped radius = 0)
        Location playerLoc = new Location(mockWorld, 499.8, 64, 0);
        when(mockPlayer.getLocation()).thenReturn(playerLoc);

        when(mockConfig.getString(eq("modules.biome_compass.messages.too_close_to_border"), anyString()))
                .thenReturn("<red>Cannot search: Too close to border for %biome%!</red>");
        when(mockConfig.getBoolean("modules.biome_compass.play_sounds", true)).thenReturn(false);

        NamespacedKey biomeKey = NamespacedKey.minecraft("cherry_grove");

        service.locateBiome(mockPlayer, null, biomeKey);

        // Player should receive too_close_to_border message exactly once
        ArgumentCaptor<Component> msgCaptor = ArgumentCaptor.forClass(Component.class);
        verify(mockPlayer, times(1)).sendMessage(msgCaptor.capture());
        String text = PlainTextComponentSerializer.plainText().serialize(msgCaptor.getValue());
        assertEquals("Cannot search: Too close to border for Cherry Grove!", text);

        // locateNearestBiome should NOT be called on world
        verify(mockWorld, never()).locateNearestBiome(any(), anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("handleSearchResult rejects target outside world border and sends outside_border message")
    void testHandleSearchResultRejectsOutsideBorder() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        Player mockPlayer = mock(Player.class);
        when(mockPlayer.getUniqueId()).thenReturn(playerUuid);
        when(mockPlayer.isOnline()).thenReturn(true);
        PlayerInventory mockInv = mock(PlayerInventory.class);
        when(mockPlayer.getInventory()).thenReturn(mockInv);

        World mockWorld = mock(World.class);
        WorldBorder mockBorder = mock(WorldBorder.class);
        when(mockWorld.getWorldBorder()).thenReturn(mockBorder);
        when(mockBorder.getCenter()).thenReturn(new Location(mockWorld, 0, 64, 0));
        when(mockBorder.getSize()).thenReturn(1000.0);

        Location playerLoc = new Location(mockWorld, 400, 64, 0);
        when(mockPlayer.getLocation()).thenReturn(playerLoc);

        // Mock target located outside the world border (e.g. x = 600)
        Location targetLoc = new Location(mockWorld, 600, 64, 0);
        when(mockBorder.isInside(targetLoc)).thenReturn(false);

        BiomeSearchResult mockResult = mock(BiomeSearchResult.class);
        when(mockResult.getLocation()).thenReturn(targetLoc);

        when(mockConfig.getString(eq("modules.biome_compass.messages.outside_border"), anyString()))
                .thenReturn("<red>%biome% found at X:%x% Z:%z% (~%distance%m), but it is outside border!</red>");
        when(mockConfig.getBoolean("modules.biome_compass.play_sounds", true)).thenReturn(false);

        try (var bukkitMock = mockStatic(org.bukkit.Bukkit.class)) {
            bukkitMock.when(() -> org.bukkit.Bukkit.getPlayer(playerUuid)).thenReturn(mockPlayer);

            invokeHandleSearchResult(playerUuid, NamespacedKey.minecraft("desert"), "Desert",
                    playerLoc, mockResult, 6400, EquipmentSlot.HAND, 0);

            // Verify outside_border message sent with placeholders replaced
            ArgumentCaptor<Component> msgCaptor = ArgumentCaptor.forClass(Component.class);
            verify(mockPlayer, times(1)).sendMessage(msgCaptor.capture());
            String text = PlainTextComponentSerializer.plainText().serialize(msgCaptor.getValue());
            assertEquals("Desert found at X:600 Z:0 (~200m), but it is outside border!", text);

            // Verify player inventory was never accessed for compass calibration
            verify(mockPlayer, never()).getItemInHand();
            verify(mockPlayer, never()).updateInventory();
        }
    }


    @Test
    @DisplayName("Fallback default messages in code are used when config keys are absent")
    void testDefaultFallbackMessages() {
        when(mockConfig.getString(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));

        assertEquals("<gray>Locating closest <aqua>%biome%</aqua>...</gray>",
                BiomeLocatorService.DEFAULT_SCANNING_MESSAGE);
        assertEquals("<green>Compass tuned to <aqua>%biome%</aqua> (~%distance%m away)!</green>",
                BiomeLocatorService.DEFAULT_FOUND_MESSAGE);
        assertEquals("<red>No %biome% found within range.</red>",
                BiomeLocatorService.DEFAULT_NOT_FOUND_MESSAGE);
        assertEquals("<red>Cannot search: You are too close to the world border!</red>",
                BiomeLocatorService.DEFAULT_TOO_CLOSE_TO_BORDER_MESSAGE);
        assertEquals("<red>Closest %biome% is outside the world border.</red>",
                BiomeLocatorService.DEFAULT_OUTSIDE_BORDER_MESSAGE);
    }
}

package net.vyrim.core.module.advancements;

import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI;
import com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.RootAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay;
import net.vyrim.core.VyrimCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AdvancementManualLayoutTest {

    private VyrimCore mockCore;
    private UltimateAdvancementAPI mockApi;
    private AdvancementTab mockTab;
    private Logger mockLogger;
    private Server mockServer;
    private ItemFactory mockItemFactory;
    private ItemMeta mockItemMeta;

    @BeforeEach
    void setUp() throws Exception {
        mockCore = mock(VyrimCore.class);
        mockApi = mock(UltimateAdvancementAPI.class);
        mockTab = mock(AdvancementTab.class);
        mockLogger = mock(Logger.class);
        mockServer = mock(Server.class);
        mockItemFactory = mock(ItemFactory.class);
        mockItemMeta = mock(ItemMeta.class);

        when(mockServer.getMinecraftVersion()).thenReturn("1.21.1");
        when(mockServer.getBukkitVersion()).thenReturn("1.21.1-R0.1-SNAPSHOT");
        when(mockServer.getVersion()).thenReturn("git-Paper-1.21.1");
        when(mockServer.getLogger()).thenReturn(mockLogger);
        when(mockServer.getItemFactory()).thenReturn(mockItemFactory);
        when(mockItemFactory.getItemMeta(any(Material.class))).thenReturn(mockItemMeta);
        when(mockItemFactory.isApplicable(any(ItemMeta.class), any(ItemStack.class))).thenReturn(true);
        when(mockItemFactory.isApplicable(any(ItemMeta.class), any(Material.class))).thenReturn(true);
        when(mockItemFactory.asMetaFor(any(ItemMeta.class), any(ItemStack.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mockItemFactory.asMetaFor(any(ItemMeta.class), any(Material.class))).thenAnswer(inv -> inv.getArgument(0));

        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, mockServer);

        when(mockCore.getLogger()).thenReturn(mockLogger);
        when(mockTab.getNamespace()).thenReturn("test_tab");
        when(mockApi.createAdvancementTab(anyString())).thenAnswer(inv -> {
            String tabId = inv.getArgument(0);
            AdvancementTab tab = mock(AdvancementTab.class);
            when(tab.getNamespace()).thenReturn(tabId);
            return tab;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, null);
    }

    private static class TestAdvancementLoader extends AdvancementLoader {
        final Map<String, float[]> recordedCoords = new LinkedHashMap<>();
        final List<String> capturedRootBackgrounds = new ArrayList<>();
        final Map<String, RawAdvancementDefinition> capturedDefs = new LinkedHashMap<>();

        TestAdvancementLoader(VyrimCore core, UltimateAdvancementAPI api) {
            super(core, api);
        }

        @Override
        protected AdvancementDisplay createDisplay(RawAdvancementDefinition def, float x, float y) {
            recordedCoords.put(def.id(), new float[]{x, y});
            capturedDefs.put(def.id(), def);
            AdvancementDisplay display = mock(AdvancementDisplay.class);
            when(display.getX()).thenReturn(x);
            when(display.getY()).thenReturn(y);
            return display;
        }

        @Override
        protected RootAdvancement createRootAdvancement(AdvancementTab tab, RawAdvancementDefinition def, AdvancementDisplay display, String backgroundTexture, int maxProgression) {
            capturedRootBackgrounds.add(backgroundTexture);
            return mock(RootAdvancement.class);
        }

        @Override
        protected BaseAdvancement createBaseAdvancement(RawAdvancementDefinition def, AdvancementDisplay display, com.fren_gor.ultimateAdvancementAPI.advancement.Advancement parentAdv, int maxProgression) {
            return mock(BaseAdvancement.class);
        }
    }

    @Test
    @DisplayName("Tab with all manually-placed advancements assigns exact coordinates verbatim including negative values")
    void testAllManualTabAssignments() {
        String yaml = """
            tabs:
              manual_tab:
                advancements:
                  root:
                    title: "Root"
                    icon: "STONE"
                    x: 0.0
                    y: 0.0
                  left_branch:
                    parent: root
                    title: "Left"
                    icon: "IRON_SWORD"
                    x: -2.5
                    y: 1.5
                  right_branch:
                    parent: root
                    title: "Right"
                    icon: "GOLDEN_SWORD"
                    x: 3.5
                    y: -1.0
            """;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        TestAdvancementLoader loader = new TestAdvancementLoader(mockCore, mockApi);
        loader.loadTabs(config);

        assertEquals(3, loader.recordedCoords.size());
        assertArrayEquals(new float[]{0.0f, 0.0f}, loader.recordedCoords.get("root"));
        assertArrayEquals(new float[]{-2.5f, 1.5f}, loader.recordedCoords.get("left_branch"));
        assertArrayEquals(new float[]{3.5f, -1.0f}, loader.recordedCoords.get("right_branch"));
    }

    @Test
    @DisplayName("Tab mixing manual and auto nodes places manual verbatim and auto without overlap")
    void testMixedManualAndAutoPlacement() {
        String yaml = """
            tabs:
              mixed_tab:
                advancements:
                  root:
                    title: "Root"
                    icon: "GRASS_BLOCK"
                  manual_child:
                    parent: root
                    title: "Manual"
                    icon: "DIAMOND"
                    x: 2.0
                    y: 1.0
                  auto_child:
                    parent: root
                    title: "Auto"
                    icon: "EMERALD"
            """;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        TestAdvancementLoader loader = new TestAdvancementLoader(mockCore, mockApi);
        loader.loadTabs(config);

        float[] rootCoords = loader.recordedCoords.get("root");
        float[] manualCoords = loader.recordedCoords.get("manual_child");
        float[] autoCoords = loader.recordedCoords.get("auto_child");

        assertNotNull(rootCoords);
        assertNotNull(manualCoords);
        assertNotNull(autoCoords);

        // Manual child is verbatim
        assertEquals(2.0f, manualCoords[0]);
        assertEquals(1.0f, manualCoords[1]);

        // Auto child should not overlap manual child
        assertFalse(manualCoords[0] == autoCoords[0] && manualCoords[1] == autoCoords[1],
                "Manual and auto child must not have identical coordinates");
        assertTrue(autoCoords[0] >= 3.5f, "Auto child should be spaced past manual sibling (>= 3.5)");
    }

    @Test
    @DisplayName("Partial coordinate specification (only x or only y) falls back to auto-layout")
    void testPartialCoordinatesFallbackToAuto() {
        String yaml = """
            tabs:
              partial_tab:
                advancements:
                  root:
                    title: "Root"
                    icon: "STONE"
                  child_only_x:
                    parent: root
                    title: "Only X"
                    icon: "DIRT"
                    x: 10.0
                  child_only_y:
                    parent: root
                    title: "Only Y"
                    icon: "COBBLESTONE"
                    y: 5.0
            """;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        TestAdvancementLoader loader = new TestAdvancementLoader(mockCore, mockApi);
        loader.loadTabs(config);

        // Since x is missing for child_only_y and y is missing for child_only_x, both should be auto-placed at depth 1
        float[] child1 = loader.recordedCoords.get("child_only_x");
        float[] child2 = loader.recordedCoords.get("child_only_y");

        assertEquals(1.0f, child1[1], "child_only_x y should be auto-computed depth 1.0");
        assertEquals(1.0f, child2[1], "child_only_y y should be auto-computed depth 1.0");
    }

    @Test
    @DisplayName("Coordinate collision between auto and manual node logs clear warning without crashing")
    void testCollisionDetectionLogsWarning() {
        // Here manual_sibling is at (0.0, 1.0) under root, and we configure collision detector
        Map<String, AdvancementLoader.RawAdvancementDefinition> defs = new LinkedHashMap<>();
        defs.put("manual_node", new AdvancementLoader.RawAdvancementDefinition(
                "manual_node", "root", "Manual", List.of(), "STONE", null, 0.0f, 1.0f, "TASK", true, true,
                new AdvancementTriggerData("JOIN_SERVER", null, 1),
                new AdvancementRewardData(null, null)
        ));
        defs.put("auto_node", new AdvancementLoader.RawAdvancementDefinition(
                "auto_node", "root", "Auto", List.of(), "STONE", null, null, null, "TASK", true, true,
                new AdvancementTriggerData("JOIN_SERVER", null, 1),
                new AdvancementRewardData(null, null)
        ));

        Map<String, Float> xCoords = new HashMap<>();
        Map<String, Float> yCoords = new HashMap<>();
        xCoords.put("manual_node", 0.0f);
        yCoords.put("manual_node", 1.0f);
        xCoords.put("auto_node", 0.0f);
        yCoords.put("auto_node", 1.0f);

        AdvancementLoader.detectCollisions("test_tab", defs, xCoords, yCoords, mockLogger);

        verify(mockLogger).warning(contains("Coordinate collision in tab 'test_tab': Auto-placed advancement 'auto_node' computed position (0.0, 1.0) collides with manually-placed advancement 'manual_node'."));
    }

    @Test
    @DisplayName("Tab-level background texture and icon inheritance works as expected")
    void testTabBackgroundAndIconInheritance() {
        String yaml = """
            tabs:
              story:
                background: "vyrim:textures/gui/advancements/backgrounds/story.png"
                icon: "vyrim:advancement/dragon_head"
                icon_fallback: "DRAGON_HEAD"
                advancements:
                  root:
                    title: "&6Story Root"
                  chapter1:
                    parent: root
                    title: "&eChapter 1"
                    icon: "IRON_SWORD"
            """;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        TestAdvancementLoader loader = new TestAdvancementLoader(mockCore, mockApi);
        loader.loadTabs(config);

        assertEquals(1, loader.capturedRootBackgrounds.size());
        assertEquals("vyrim:textures/gui/advancements/backgrounds/story.png", loader.capturedRootBackgrounds.get(0));

        AdvancementLoader.RawAdvancementDefinition rootDef = loader.capturedDefs.get("root");
        assertNotNull(rootDef);
        assertEquals("vyrim:advancement/dragon_head", rootDef.icon());
        assertEquals("DRAGON_HEAD", rootDef.iconFallback());
    }
}

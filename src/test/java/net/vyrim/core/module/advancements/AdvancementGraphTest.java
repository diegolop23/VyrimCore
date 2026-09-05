package net.vyrim.core.module.advancements;

import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI;
import net.vyrim.core.VyrimCore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdvancementGraphTest {

    private VyrimCore mockCore;
    private UltimateAdvancementAPI mockApi;
    private AdvancementTab mockTab;
    private Logger mockLogger;

    @BeforeEach
    void setUp() {
        mockCore = mock(VyrimCore.class);
        mockApi = mock(UltimateAdvancementAPI.class);
        mockTab = mock(AdvancementTab.class);
        mockLogger = mock(Logger.class);

        org.bukkit.Server mockServer = mock(org.bukkit.Server.class);
        when(mockServer.getMinecraftVersion()).thenReturn("1.21.1");
        when(mockServer.getBukkitVersion()).thenReturn("1.21.1-R0.1-SNAPSHOT");
        when(mockServer.getVersion()).thenReturn("git-Paper-1.21.1");
        when(mockServer.getLogger()).thenReturn(mockLogger);
        try {
            java.lang.reflect.Field serverField = org.bukkit.Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, mockServer);
        } catch (Throwable ignored) {
        }

        when(mockCore.getLogger()).thenReturn(mockLogger);
        doAnswer(inv -> { System.err.println("SEVR: " + inv.getArgument(0)); return null; }).when(mockLogger).severe(anyString());
        doAnswer(inv -> { System.err.println("WARN: " + inv.getArgument(0)); return null; }).when(mockLogger).warning(anyString());
        doAnswer(inv -> { System.err.println("INFO: " + inv.getArgument(0)); return null; }).when(mockLogger).info(anyString());
        when(mockTab.getNamespace()).thenReturn("test_tab");
        when(mockApi.createAdvancementTab(anyString())).thenAnswer(inv -> {
            String tabId = inv.getArgument(0);
            AdvancementTab tab = mock(AdvancementTab.class);
            when(tab.getNamespace()).thenReturn(tabId);
            return tab;
        });
    }

    @Test
    @DisplayName("Auto-compute coordinates assigns proper depth and produces no overlapping nodes")
    void testComputeCoordinatesNoOverlaps() {
        // Tree structure:
        // root
        //   -> c1
        //        -> gc1
        //        -> gc2
        //   -> c2
        //        -> gc3
        Map<String, List<String>> childrenMap = new HashMap<>();
        childrenMap.put("root", List.of("c1", "c2"));
        childrenMap.put("c1", List.of("gc1", "gc2"));
        childrenMap.put("c2", List.of("gc3"));
        childrenMap.put("gc1", Collections.emptyList());
        childrenMap.put("gc2", Collections.emptyList());
        childrenMap.put("gc3", Collections.emptyList());

        Map<String, Float> xCoords = new HashMap<>();
        Map<String, Float> yCoords = new HashMap<>();
        float[] nextLeafX = new float[]{0.0f};

        AdvancementLoader.computeCoordinates("root", 0, childrenMap, xCoords, yCoords, nextLeafX);

        // Check depths (y)
        assertEquals(0.0f, yCoords.get("root"));
        assertEquals(1.0f, yCoords.get("c1"));
        assertEquals(1.0f, yCoords.get("c2"));
        assertEquals(2.0f, yCoords.get("gc1"));
        assertEquals(2.0f, yCoords.get("gc2"));
        assertEquals(2.0f, yCoords.get("gc3"));

        // Check sibling x progression
        assertTrue(xCoords.get("gc1") < xCoords.get("gc2"), "gc1 must be to the left of gc2");
        assertTrue(xCoords.get("gc2") < xCoords.get("gc3"), "gc2 must be to the left of gc3");
        assertTrue(xCoords.get("c1") < xCoords.get("c2"), "c1 must be to the left of c2");

        // Verify NO two nodes share the exact same (x, y) coordinate
        Set<String> coordPairs = new HashSet<>();
        for (String node : List.of("root", "c1", "c2", "gc1", "gc2", "gc3")) {
            String key = xCoords.get(node) + ":" + yCoords.get(node);
            assertTrue(coordPairs.add(key), "Duplicate coordinate detected for node: " + node + " at " + key);
        }
    }

    @Test
    @DisplayName("Unknown parent reference skips the tab and logs a clear error naming the offending ID")
    void testUnknownParentSkipsTab() {
        String yaml = """
            tabs:
              test_tab:
                advancements:
                  root:
                    title: "Root"
                    icon: "STONE"
                  broken_child:
                    parent: non_existent_parent
                    title: "Broken"
                    icon: "DIRT"
            """;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        AdvancementLoader loader = new AdvancementLoader(mockCore, mockApi);
        loader.loadTabs(config);

        assertTrue(loader.getRegisteredTabs().isEmpty(), "Tab should have been skipped due to unknown parent");
        verify(mockLogger).severe(contains("references unknown parent 'non_existent_parent'"));
    }

    @Test
    @DisplayName("Cycle detection skips the tab and logs a clear error naming the offending advancement ID")
    void testCycleDetectionSkipsTab() {
        String yaml = """
            tabs:
              cyclic_tab:
                advancements:
                  root:
                    title: "Root"
                    icon: "STONE"
                  node_a:
                    parent: node_b
                    title: "Node A"
                    icon: "DIRT"
                  node_b:
                    parent: node_a
                    title: "Node B"
                    icon: "GRASS_BLOCK"
            """;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        AdvancementLoader loader = new AdvancementLoader(mockCore, mockApi);
        loader.loadTabs(config);

        assertTrue(loader.getRegisteredTabs().isEmpty(), "Tab with cycle must be skipped");
        verify(mockLogger).severe(contains("Cycle detected in advancement hierarchy"));
    }

    @Test
    @DisplayName("Missing root skips the tab")
    void testMissingRootSkipsTab() {
        String yaml = """
            tabs:
              no_root_tab:
                advancements:
                  child1:
                    parent: child2
                    title: "Child 1"
                    icon: "STONE"
                  child2:
                    parent: child1
                    title: "Child 2"
                    icon: "DIRT"
            """;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        AdvancementLoader loader = new AdvancementLoader(mockCore, mockApi);
        loader.loadTabs(config);

        assertTrue(loader.getRegisteredTabs().isEmpty(), "Tab without root must be skipped");
        verify(mockLogger).severe(contains("No root advancement found"));
    }

    private AdvancementLoader createTestLoader() {
        return new AdvancementLoader(mockCore, mockApi) {
            @Override
            protected com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay createDisplay(RawAdvancementDefinition def, float x, float y) {
                return mock(com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay.class);
            }

            @Override
            protected com.fren_gor.ultimateAdvancementAPI.advancement.RootAdvancement createRootAdvancement(AdvancementTab tab, RawAdvancementDefinition def, com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay display, String backgroundTexture, int maxProgression) {
                return mock(com.fren_gor.ultimateAdvancementAPI.advancement.RootAdvancement.class);
            }

            @Override
            protected com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement createBaseAdvancement(RawAdvancementDefinition def, com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay display, com.fren_gor.ultimateAdvancementAPI.advancement.Advancement parentAdv, int maxProgression) {
                return mock(com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement.class);
            }
        };
    }

    @Test
    @DisplayName("Trigger and reward blocks are accurately parsed and stored in memory maps")
    void testTriggerAndRewardParsing() {
        String yaml = """
            tabs:
              valid_tab:
                advancements:
                  root:
                    title: "&6Root"
                    icon: "GRASS_BLOCK"
                    trigger:
                      type: JOIN_SERVER
                      amount: 1
                    reward:
                      command: "eco give %player% 50"
                      permission: "vyrim.welcome"
                  miner:
                    parent: root
                    title: "&eMiner"
                    icon: "IRON_PICKAXE"
                    trigger:
                      type: BREAK_BLOCK
                      target: "STONE"
                      amount: 100
                    reward:
                      command: "give %player% diamond 1"
            """;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader(yaml));
        AdvancementLoader loader = createTestLoader();
        loader.loadTabs(config);

        assertEquals(1, loader.getRegisteredTabs().size());

        // Verify trigger data
        AdvancementTriggerData rootTrigger = loader.getTriggerDataMap().get("root");
        assertNotNull(rootTrigger);
        assertEquals("JOIN_SERVER", rootTrigger.type());
        assertNull(rootTrigger.target());
        assertEquals(1, rootTrigger.amount());

        AdvancementTriggerData minerTrigger = loader.getTriggerDataMap().get("miner");
        assertNotNull(minerTrigger);
        assertEquals("BREAK_BLOCK", minerTrigger.type());
        assertEquals("STONE", minerTrigger.target());
        assertEquals(100, minerTrigger.amount());

        // Verify reward data
        AdvancementRewardData rootReward = loader.getRewardDataMap().get("root");
        assertNotNull(rootReward);
        assertEquals("eco give %player% 50", rootReward.command());
        assertEquals("vyrim.welcome", rootReward.permission());

        AdvancementRewardData minerReward = loader.getRewardDataMap().get("miner");
        assertNotNull(minerReward);
        assertEquals("give %player% diamond 1", minerReward.command());
        assertNull(minerReward.permission());
    }
}

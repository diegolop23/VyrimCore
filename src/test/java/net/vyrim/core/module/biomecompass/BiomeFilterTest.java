package net.vyrim.core.module.biomecompass;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BiomeFilterTest {

    @Test
    @DisplayName("formatBiomeName formats snake_case keys to Title Case")
    void testFormatBiomeName() {
        assertEquals("Cherry Grove", BiomeLocatorService.formatBiomeName(NamespacedKey.minecraft("cherry_grove")));
        assertEquals("Deep Dark", BiomeLocatorService.formatBiomeName(NamespacedKey.minecraft("deep_dark")));
        assertEquals("Plains", BiomeLocatorService.formatBiomeName(NamespacedKey.minecraft("plains")));
        assertEquals("Nether Wastes", BiomeLocatorService.formatBiomeName(NamespacedKey.minecraft("nether_wastes")));
        assertEquals("The End", BiomeLocatorService.formatBiomeName(NamespacedKey.minecraft("the_end")));
        assertEquals("Unknown", BiomeLocatorService.formatBiomeName(null));
    }

    @Test
    @DisplayName("formatEnvironmentName correctly labels dimensions")
    void testFormatEnvironmentName() {
        assertEquals("Overworld", BiomeCompassGUI.formatEnvironmentName(World.Environment.NORMAL));
        assertEquals("Nether", BiomeCompassGUI.formatEnvironmentName(World.Environment.NETHER));
        assertEquals("The End", BiomeCompassGUI.formatEnvironmentName(World.Environment.THE_END));
        assertEquals("Custom", BiomeCompassGUI.formatEnvironmentName(World.Environment.CUSTOM));
        assertEquals("Unknown", BiomeCompassGUI.formatEnvironmentName(null));
    }

    @Test
    @DisplayName("resolveBiomeMaterial returns appropriate representative icons")
    void testResolveBiomeMaterial() {
        assertEquals(Material.CHERRY_SAPLING,
                BiomeCompassGUI.resolveBiomeMaterial(NamespacedKey.minecraft("cherry_grove"), "Overworld"));
        assertEquals(Material.SAND,
                BiomeCompassGUI.resolveBiomeMaterial(NamespacedKey.minecraft("desert"), "Overworld"));
        assertEquals(Material.SCULK,
                BiomeCompassGUI.resolveBiomeMaterial(NamespacedKey.minecraft("deep_dark"), "Overworld"));
        assertEquals(Material.NETHERRACK,
                BiomeCompassGUI.resolveBiomeMaterial(NamespacedKey.minecraft("nether_wastes"), "Nether"));
        assertEquals(Material.CHORUS_FLOWER,
                BiomeCompassGUI.resolveBiomeMaterial(NamespacedKey.minecraft("end_highlands"), "The End"));
        assertEquals(Material.GRASS_BLOCK,
                BiomeCompassGUI.resolveBiomeMaterial(NamespacedKey.minecraft("unknown_plains"), "Overworld"));
        assertEquals(Material.NETHERRACK,
                BiomeCompassGUI.resolveBiomeMaterial(NamespacedKey.minecraft("unknown_nether"), "Nether"));
        assertEquals(Material.END_STONE,
                BiomeCompassGUI.resolveBiomeMaterial(NamespacedKey.minecraft("unknown_end"), "The End"));
    }
}

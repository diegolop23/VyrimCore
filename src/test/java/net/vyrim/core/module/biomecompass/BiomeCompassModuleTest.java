package net.vyrim.core.module.biomecompass;

import org.bukkit.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BiomeCompassModuleTest {

    @Test
    @DisplayName("BiomeCompassHolder holds correct state without player references")
    void testBiomeCompassHolder() {
        UUID uuid = UUID.randomUUID();
        BiomeCompassHolder holder = new BiomeCompassHolder(uuid, World.Environment.NORMAL, 0,
                org.bukkit.inventory.EquipmentSlot.OFF_HAND, 40);
        holder.setTotalPages(3);

        assertEquals(uuid, holder.getPlayerUuid());
        assertEquals(World.Environment.NORMAL, holder.getEnvironment());
        assertEquals(0, holder.getCurrentPage());
        assertEquals(3, holder.getTotalPages());
        assertEquals(org.bukkit.inventory.EquipmentSlot.OFF_HAND, holder.getHand());
        assertEquals(40, holder.getInventorySlot());

        holder.setCurrentPage(2);
        assertEquals(2, holder.getCurrentPage());
    }

    @Test
    @DisplayName("BiomeCompassModule reports correct name and default disabled status")
    void testModuleDefaults() {
        BiomeCompassModule module = new BiomeCompassModule(null, null);
        assertEquals(BiomeCompassModule.MODULE_NAME, module.name());
        assertFalse(module.isEnabled());
    }
}

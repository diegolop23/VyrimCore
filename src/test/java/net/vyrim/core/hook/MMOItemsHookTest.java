package net.vyrim.core.hook;

import io.lumine.mythic.lib.skill.trigger.TriggerType;
import net.vyrim.core.module.biomecompass.BiomeCompassAbility;
import net.vyrim.core.module.biomecompass.BiomeCompassModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MMOItemsHookTest {

    @Test
    @DisplayName("BiomeCompassModule getOrCreateAbility returns cached BIOME_LOCATOR ability")
    void testBiomeCompassModuleGetOrCreateAbility() {
        BiomeCompassModule module = new BiomeCompassModule(null, null);
        BiomeCompassAbility ability1 = module.getOrCreateAbility();
        assertNotNull(ability1);
        assertEquals(BiomeCompassAbility.ABILITY_ID, ability1.getId());
        assertEquals("BIOME_LOCATOR", ability1.getId());

        BiomeCompassAbility ability2 = module.getOrCreateAbility();
        assertSame(ability1, ability2, "getOrCreateAbility should cache and return the same instance");
    }

    @Test
    @DisplayName("BiomeCompassAbility constructs properly with BIOME_LOCATOR ID and RIGHT_CLICK trigger")
    void testBiomeCompassAbilityBasics() {
        BiomeCompassAbility ability = new BiomeCompassAbility();
        assertEquals(BiomeCompassAbility.ABILITY_ID, ability.getId());
        assertEquals("BIOME_LOCATOR", ability.getId());
        assertEquals(TriggerType.RIGHT_CLICK, ability.getDefaultTriggerType());
        assertNull(ability.getModule());
        assertNull(ability.getGui());
    }

    @Test
    @DisplayName("BiomeCompassAbility late-binding binds module and GUI safely")
    void testBiomeCompassAbilityBinding() {
        BiomeCompassAbility ability = new BiomeCompassAbility();

        BiomeCompassModule mockModule = mock(BiomeCompassModule.class);
        when(mockModule.isEnabled()).thenReturn(true);

        ability.bind(mockModule, null);
        assertSame(mockModule, ability.getModule());

        // Unbind on module disable
        ability.bind(null, null);
        assertNull(ability.getModule());
    }

    @Test
    @DisplayName("BiomeCompassAbility getResult returns false when module is disabled")
    void testBiomeCompassAbilityDisabledGuard() {
        BiomeCompassAbility ability = new BiomeCompassAbility();

        BiomeCompassModule mockModule = mock(BiomeCompassModule.class);
        when(mockModule.isEnabled()).thenReturn(false);
        ability.bind(mockModule, null);

        assertFalse(ability.getResult(mock(io.lumine.mythic.lib.skill.SkillMetadata.class)).isSuccessful());
    }
}

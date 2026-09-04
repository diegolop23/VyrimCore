package net.vyrim.core.module.biomecompass;

import io.lumine.mythic.lib.skill.SkillMetadata;
import io.lumine.mythic.lib.skill.handler.SkillHandler;
import io.lumine.mythic.lib.skill.result.def.SimpleSkillResult;
import io.lumine.mythic.lib.skill.trigger.TriggerType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

/**
 * MMOItems & MythicLib custom ability handler (ID: BIOME_LOCATOR).
 * Triggered on RIGHT_CLICK to open the dynamic Biome Compass selection GUI.
 */
public class BiomeCompassAbility extends SkillHandler<SimpleSkillResult> {

    public static final String ABILITY_ID = "BIOME_LOCATOR";

    private final BiomeCompassGUI gui;
    private final BiomeCompassModule module;

    public BiomeCompassAbility(BiomeCompassModule module, BiomeCompassGUI gui) {
        super(new org.bukkit.configuration.file.YamlConfiguration().createSection(ABILITY_ID));
        this.module = module;
        this.gui = gui;
    }

    @Override
    public TriggerType getDefaultTriggerType() {
        return TriggerType.RIGHT_CLICK;
    }

    @Override
    public SimpleSkillResult getResult(SkillMetadata meta) {
        if (!module.isEnabled()) {
            return new SimpleSkillResult(false);
        }
        Player player = meta.getCaster().getData().getPlayer();
        return new SimpleSkillResult(player != null && player.isOnline());
    }

    @Override
    public void whenCast(SimpleSkillResult result, SkillMetadata meta) {
        whenCast(meta);
    }

    public void whenCast(SkillMetadata meta) {
        if (!module.isEnabled()) {
            return;
        }
        Player player = meta.getCaster().getData().getPlayer();
        if (player != null && player.isOnline()) {
            EquipmentSlot bukkitSlot = EquipmentSlot.HAND;
            if (meta.getCaster().getActionHand() != null) {
                bukkitSlot = meta.getCaster().getActionHand().toBukkit();
            }
            int slot = (bukkitSlot == EquipmentSlot.OFF_HAND) ? 40 : player.getInventory().getHeldItemSlot();
            gui.open(player, bukkitSlot, slot);
        }
    }
}

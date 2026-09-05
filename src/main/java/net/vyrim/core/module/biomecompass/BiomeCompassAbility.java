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

    private BiomeCompassGUI gui;
    private BiomeCompassModule module;

    public BiomeCompassAbility() {
        this(null, null);
    }

    public BiomeCompassAbility(BiomeCompassModule module) {
        this(module, null);
    }

    public BiomeCompassAbility(BiomeCompassModule module, BiomeCompassGUI gui) {
        super(new org.bukkit.configuration.file.YamlConfiguration().createSection(ABILITY_ID));
        this.module = module;
        this.gui = gui;
    }

    /**
     * Binds or updates the active BiomeCompassModule and GUI instances.
     *
     * @param module the BiomeCompassModule instance
     * @param gui    the active GUI instance
     */
    public void bind(BiomeCompassModule module, BiomeCompassGUI gui) {
        this.module = module;
        this.gui = gui;
    }

    public BiomeCompassModule getModule() {
        return resolveModule();
    }

    public BiomeCompassGUI getGui() {
        return resolveGui();
    }

    private BiomeCompassModule resolveModule() {
        if (this.module != null) {
            return this.module;
        }
        net.vyrim.core.VyrimCore core = net.vyrim.core.VyrimCore.getInstance();
        if (core != null && core.modules() != null) {
            return core.modules().getModule(BiomeCompassModule.MODULE_NAME)
                    .filter(m -> m instanceof BiomeCompassModule)
                    .map(m -> (BiomeCompassModule) m)
                    .orElse(null);
        }
        return null;
    }

    private BiomeCompassGUI resolveGui() {
        if (this.gui != null) {
            return this.gui;
        }
        BiomeCompassModule mod = resolveModule();
        return mod != null ? mod.getGui() : null;
    }

    @Override
    public TriggerType getDefaultTriggerType() {
        return TriggerType.RIGHT_CLICK;
    }

    @Override
    public SimpleSkillResult getResult(SkillMetadata meta) {
        BiomeCompassModule mod = resolveModule();
        if (mod == null || !mod.isEnabled()) {
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
        BiomeCompassModule mod = resolveModule();
        BiomeCompassGUI activeGui = resolveGui();
        if (mod == null || !mod.isEnabled() || activeGui == null) {
            return;
        }
        Player player = meta.getCaster().getData().getPlayer();
        if (player != null && player.isOnline()) {
            EquipmentSlot bukkitSlot = EquipmentSlot.HAND;
            if (meta.getCaster().getActionHand() != null) {
                bukkitSlot = meta.getCaster().getActionHand().toBukkit();
            }
            int slot = (bukkitSlot == EquipmentSlot.OFF_HAND) ? 40 : player.getInventory().getHeldItemSlot();
            activeGui.open(player, bukkitSlot, slot);
        }
    }
}

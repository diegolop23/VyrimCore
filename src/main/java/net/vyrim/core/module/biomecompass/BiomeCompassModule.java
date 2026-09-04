package net.vyrim.core.module.biomecompass;

import net.vyrim.core.VyrimCore;
import net.vyrim.core.hook.MMOItemsHook;
import net.vyrim.core.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Modular implementation of the Biome Compass feature.
 * Lifecycle is controlled by config.yml and soft-dependency availability.
 */
public class BiomeCompassModule implements Module {

    public static final String MODULE_NAME = "BiomeCompass";

    private final VyrimCore core;
    private final MMOItemsHook mmoItemsHook;

    private boolean enabled;
    private BiomeLocatorService locatorService;
    private BiomeCompassGUI gui;
    private BiomeCompassAbility ability;

    public BiomeCompassModule(VyrimCore core, MMOItemsHook mmoItemsHook) {
        this.core = core;
        this.mmoItemsHook = mmoItemsHook;
    }

    @Override
    public String name() {
        return MODULE_NAME;
    }

    @Override
    public boolean isAvailable(VyrimCore core) {
        boolean configEnabled = core.getConfig().getBoolean("modules.biome_compass.enabled", true);
        if (!configEnabled) {
            core.getLogger().info("[BiomeCompass] Module is disabled in config.yml.");
            return false;
        }

        if (mmoItemsHook == null || !mmoItemsHook.isAvailable()) {
            core.getLogger().warning("[BiomeCompass] MMOItems or MythicLib is missing/disabled. Skipping module.");
            return false;
        }

        return true;
    }

    @Override
    public void onEnable(VyrimCore plugin) {
        enable();
    }

    @Override
    public void onDisable() {
        disable();
    }

    /**
     * Activates the Biome Compass module, registering listeners and abilities.
     */
    public void enable() {
        if (enabled) {
            return;
        }

        this.locatorService = new BiomeLocatorService(core);
        this.gui = new BiomeCompassGUI(core, locatorService);
        this.ability = new BiomeCompassAbility(this, gui);

        // Register GUI click listener
        Bukkit.getPluginManager().registerEvents(gui, core);

        // Register custom MMOItems ability (ID: BIOME_LOCATOR)
        if (mmoItemsHook != null) {
            mmoItemsHook.registerSkill(ability);
        }

        this.enabled = true;
        core.getLogger().info("[BiomeCompass] Biome Compass module successfully enabled.");
    }

    /**
     * Deactivates the module, releasing all listeners, open GUIs, and pending tasks.
     */
    public void disable() {
        if (!enabled) {
            return;
        }
        this.enabled = false;

        // Unregister GUI event listeners
        if (gui != null) {
            HandlerList.unregisterAll(gui);
        }

        // Close open GUI inventories to prevent stuck menus
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BiomeCompassHolder) {
                player.closeInventory();
            }
        }

        // Shutdown async search tasks
        if (locatorService != null) {
            locatorService.shutdown();
        }

        // Unregister custom ability
        if (mmoItemsHook != null && ability != null) {
            mmoItemsHook.unregisterSkill(ability);
        }

        this.locatorService = null;
        this.gui = null;
        this.ability = null;

        core.getLogger().info("[BiomeCompass] Biome Compass module disabled and cleaned up.");
    }

    /**
     * Returns whether this module is currently active.
     */
    public boolean isEnabled() {
        return enabled;
    }

    public BiomeLocatorService getLocatorService() {
        return locatorService;
    }

    public BiomeCompassGUI getGui() {
        return gui;
    }

    public BiomeCompassAbility getAbility() {
        return ability;
    }
}

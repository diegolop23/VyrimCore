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
    public static final String PERMISSION_BYPASS = "vyrimcore.bypass.biomecompass";

    private final VyrimCore core;
    private final MMOItemsHook mmoItemsHook;
    private final java.util.Map<java.util.UUID, Long> cooldowns = new java.util.concurrent.ConcurrentHashMap<>();

    private boolean enabled;
    private BiomeLocatorService locatorService;
    private BiomeCompassGUI gui;
    private BiomeCompassAbility ability;
    private org.bukkit.event.Listener quitListener;
    private org.bukkit.scheduler.BukkitTask cleanupTask;

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
        if (core == null || core.getConfig() == null) {
            return false;
        }
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
     * Activates the Biome Compass module, registering listeners, cooldown tasks, and abilities.
     */
    public void enable() {
        if (enabled) {
            return;
        }

        this.locatorService = new BiomeLocatorService(core);
        this.gui = new BiomeCompassGUI(core, locatorService, this);
        this.ability = new BiomeCompassAbility(this, gui);

        // Register GUI click listener
        Bukkit.getPluginManager().registerEvents(gui, core);

        // Register player disconnect listener to prune cooldown state
        this.quitListener = new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                cooldowns.remove(event.getPlayer().getUniqueId());
            }
        };
        Bukkit.getPluginManager().registerEvents(quitListener, core);

        // Schedule periodic asynchronous cleanup of expired cooldown entries (every 60 seconds)
        this.cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(core, this::cleanupExpiredCooldowns, 1200L, 1200L);

        // Register custom MMOItems ability (ID: BIOME_LOCATOR)
        if (mmoItemsHook != null) {
            mmoItemsHook.registerSkill(ability);
        }

        this.enabled = true;
        core.getLogger().info("[BiomeCompass] Biome Compass module successfully enabled.");
    }

    /**
     * Deactivates the module, releasing all listeners, open GUIs, pending tasks, and abilities.
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

        // Unregister disconnect listener
        if (quitListener != null) {
            HandlerList.unregisterAll(quitListener);
            quitListener = null;
        }

        // Cancel periodic cooldown pruning task
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
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

    /**
     * Cooldown duration in seconds configured in config.yml.
     */
    public int getCooldownSeconds() {
        if (core == null || core.getConfig() == null) {
            return 30;
        }
        return core.getConfig().getInt("modules.biome_compass.cooldown", 30);
    }

    /**
     * Raw message template configured for cooldown feedback.
     */
    public String getCooldownMessageTemplate() {
        if (core == null || core.getConfig() == null) {
            return "&cYou must wait &e%time%s &cbefore searching for another biome!";
        }
        return core.getConfig().getString("modules.biome_compass.messages.cooldown",
                "&cYou must wait &e%time%s &cbefore searching for another biome!");
    }

    /**
     * Checks if a player is currently on cooldown.
     *
     * @param uuid the player's unique identifier
     * @return true if currently on cooldown
     */
    public boolean isOnCooldown(java.util.UUID uuid) {
        if (uuid == null) {
            return false;
        }
        Long expireTime = cooldowns.get(uuid);
        if (expireTime == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expireTime) {
            cooldowns.remove(uuid);
            return false;
        }
        return true;
    }

    /**
     * Calculates remaining cooldown time in ceiling seconds.
     *
     * @param uuid the player's unique identifier
     * @return remaining seconds, or 0 if expired/not on cooldown
     */
    public long getRemainingCooldownSeconds(java.util.UUID uuid) {
        if (uuid == null) {
            return 0;
        }
        Long expireTime = cooldowns.get(uuid);
        if (expireTime == null) {
            return 0;
        }
        long remainingMillis = expireTime - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            cooldowns.remove(uuid);
            return 0;
        }
        return (remainingMillis + 999) / 1000;
    }

    /**
     * Sets the player's cooldown timestamp according to the configured duration.
     *
     * @param uuid the player's unique identifier
     */
    public void setCooldown(java.util.UUID uuid) {
        if (uuid == null) {
            return;
        }
        int seconds = getCooldownSeconds();
        if (seconds > 0) {
            cooldowns.put(uuid, System.currentTimeMillis() + (seconds * 1000L));
        }
    }

    /**
     * Clears cooldown for the given player.
     */
    public void clearCooldown(java.util.UUID uuid) {
        if (uuid != null) {
            cooldowns.remove(uuid);
        }
    }

    /**
     * Prunes all expired cooldown entries from memory.
     */
    public void cleanupExpiredCooldowns() {
        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    /**
     * Formats the configured cooldown message, replacing %time% and %seconds% placeholders
     * with the remaining seconds, deserialized with color code support.
     *
     * @param remainingSeconds remaining cooldown time
     * @return formatted adventure Component
     */
    public net.kyori.adventure.text.Component formatCooldownMessage(long remainingSeconds) {
        String template = getCooldownMessageTemplate();
        String formatted = template
                .replace("%time%", String.valueOf(remainingSeconds))
                .replace("%seconds%", String.valueOf(remainingSeconds));
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(formatted);
    }
}

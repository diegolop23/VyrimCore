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

    public static final String DEFAULT_MODE_NEAREST_NAME = "&aMode: Nearest";
    public static final String DEFAULT_MODE_NEAREST_LORE = "&7Always returns the closest match,\n&7even the patch you're standing in.";
    public static final String DEFAULT_MODE_IGNORE_NAME = "&6Mode: Ignore Current Biome";
    public static final String DEFAULT_MODE_IGNORE_LORE = "&7Skips the patch you're standing in\n&7and searches for the next-nearest one.";

    private final VyrimCore core;
    private final MMOItemsHook mmoItemsHook;
    private final java.util.Map<java.util.UUID, Long> lastSearchTimes = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Boolean> ignoreCurrentBiomeMode = new java.util.concurrent.ConcurrentHashMap<>();
    private final BiomeTierResolver tierResolver = new BiomeTierResolver();

    private boolean enabled;
    private BiomeLocatorService locatorService;
    private BiomeCompassGUI gui;
    private BiomeCompassAbility ability;
    private org.bukkit.event.Listener quitListener;
    private org.bukkit.scheduler.BukkitTask cleanupTask;

    private int cooldownSeconds = 30;
    private int searchRadius = 6400;
    private boolean playSounds = true;
    private int sameBiomeMinDistance = 150;
    private int sameBiomeProbeCount = 8;
    private String messageCooldown = "<red>You must wait <yellow>%seconds%s</yellow> before scanning again!</red>";
    private String messageScanning = "<gray>Locating closest <aqua>%biome%</aqua>...</gray>";
    private String messageFound = "<green>Compass tuned to <aqua>%biome%</aqua> (~%distance%m away)!</green>";
    private String messageNotFound = "<red>No %biome% found within range.</red>";
    private String messageTooCloseToBorder = "<red>Cannot search: You are too close to the world border!</red>";
    private String messageOutsideBorder = "<red>Closest %biome% is outside the world border.</red>";
    private String modeNearestName = DEFAULT_MODE_NEAREST_NAME;
    private String modeNearestLore = DEFAULT_MODE_NEAREST_LORE;
    private String modeIgnoreName = DEFAULT_MODE_IGNORE_NAME;
    private String modeIgnoreLore = DEFAULT_MODE_IGNORE_LORE;

    public BiomeCompassModule(VyrimCore core, MMOItemsHook mmoItemsHook) {
        this.core = core;
        this.mmoItemsHook = mmoItemsHook;
        loadConfiguration();
    }

    @Override
    public String name() {
        return MODULE_NAME;
    }

    @Override
    public String configKey() {
        return "biome_compass";
    }

    @Override
    public boolean isAvailable(VyrimCore core) {
        if (core == null || core.getConfig() == null) {
            return false;
        }
        boolean configEnabled = isConfigEnabled(core);
        if (!configEnabled) {
            core.getLogger().info("[BiomeCompass] Module is disabled in config.yml.");
            return false;
        }

        if (mmoItemsHook == null || !mmoItemsHook.isMythicLibAvailable()) {
            core.getLogger().warning("[BiomeCompass] MythicLib is missing or disabled. Skipping module.");
            return false;
        }

        if (mmoItemsHook.isMMOItemsPending()) {
            core.getLogger().info("[BiomeCompass] MMOItems is loading after VyrimCore; module will activate once MMOItems enables.");
            return false;
        }

        if (!mmoItemsHook.isFullyAvailable()) {
            core.getLogger().warning("[BiomeCompass] MMOItems is missing or disabled. Skipping module.");
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

        loadConfiguration();

        this.locatorService = new BiomeLocatorService(core);
        this.gui = new BiomeCompassGUI(core, locatorService, this);

        // Register GUI click listener
        Bukkit.getPluginManager().registerEvents(gui, core);

        // Register player disconnect listener to prune cooldown state and session modes
        this.quitListener = new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                lastSearchTimes.remove(event.getPlayer().getUniqueId());
                ignoreCurrentBiomeMode.remove(event.getPlayer().getUniqueId());
            }
        };
        Bukkit.getPluginManager().registerEvents(quitListener, core);

        // Schedule periodic asynchronous cleanup of expired cooldown entries (every 60 seconds)
        this.cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(core, this::cleanupExpiredCooldowns, 1200L, 1200L);

        // Bind GUI to the ability and ensure registered
        if (this.ability == null) {
            this.ability = new BiomeCompassAbility(this, gui);
        } else {
            this.ability.bind(this, gui);
        }

        if (mmoItemsHook != null) {
            mmoItemsHook.registerSkill(ability);
        }

        this.enabled = true;
        core.getLogger().info("[BiomeCompass] Biome Compass module successfully enabled.");
    }

    /**
     * Returns or lazily creates the BiomeCompassAbility instance.
     */
    public BiomeCompassAbility getOrCreateAbility() {
        if (this.ability == null) {
            this.ability = new BiomeCompassAbility(this, this.gui);
        }
        return this.ability;
    }

    /**
     * Deactivates the module, releasing all listeners, open GUIs, pending tasks, and abilities.
     */
    public void disable() {
        if (!enabled) {
            return;
        }
        this.enabled = false;

        // Clear cooldown cache and session modes on disable
        lastSearchTimes.clear();
        ignoreCurrentBiomeMode.clear();

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
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player != null && player.getOpenInventory() != null && player.getOpenInventory().getTopInventory() != null
                        && player.getOpenInventory().getTopInventory().getHolder() instanceof BiomeCompassHolder) {
                    player.closeInventory();
                }
            }
        } catch (Throwable ignored) {
        }

        // Shutdown async search tasks and defensively sweep lingering search tags from online players
        if (locatorService != null) {
            locatorService.sweepAllSearchTags();
            locatorService.shutdown();
        }

        // Unbind custom ability reference
        if (ability != null) {
            ability.bind(null, null);
        }

        this.locatorService = null;
        this.gui = null;
        this.ability = null;

        if (core != null && core.getLogger() != null) {
            core.getLogger().info("[BiomeCompass] Biome Compass module disabled and cleaned up.");
        }
    }

    @Override
    public void reload(VyrimCore core) {
        loadConfiguration();
        if (core != null && core.getLogger() != null) {
            core.getLogger().info("[BiomeCompass] Reloaded configuration (radius: "
                    + searchRadius + ", cooldown: " + cooldownSeconds + "s).");
        }
    }

    public void loadConfiguration() {
        if (core == null || core.getConfig() == null) {
            this.cooldownSeconds = 30;
            this.searchRadius = 6400;
            this.playSounds = true;
            this.messageCooldown = "<red>You must wait <yellow>%seconds%s</yellow> before scanning again!</red>";
            this.messageScanning = "<gray>Locating closest <aqua>%biome%</aqua>...</gray>";
            this.messageFound = "<green>Compass tuned to <aqua>%biome%</aqua> (~%distance%m away)!</green>";
            this.messageNotFound = "<red>No %biome% found within range.</red>";
            this.messageTooCloseToBorder = "<red>Cannot search: You are too close to the world border!</red>";
            this.messageOutsideBorder = "<red>Closest %biome% is outside the world border.</red>";
            this.sameBiomeMinDistance = 150;
            this.sameBiomeProbeCount = 8;
            this.modeNearestName = DEFAULT_MODE_NEAREST_NAME;
            this.modeNearestLore = DEFAULT_MODE_NEAREST_LORE;
            this.modeIgnoreName = DEFAULT_MODE_IGNORE_NAME;
            this.modeIgnoreLore = DEFAULT_MODE_IGNORE_LORE;
            this.tierResolver.loadConfiguration(null);
            return;
        }

        var config = core.getConfig();
        this.tierResolver.loadConfiguration(config);
        this.cooldownSeconds = config.getInt("modules.biome_compass.cooldown", 30);
        if (config.contains("modules.biome_compass.radius")) {
            this.searchRadius = config.getInt("modules.biome_compass.radius", 6400);
        } else {
            this.searchRadius = config.getInt("modules.biome_compass.search_radius", 6400);
        }
        this.playSounds = config.getBoolean("modules.biome_compass.play_sounds", true);
        this.sameBiomeMinDistance = Math.max(0, config.getInt("modules.biome_compass.same_biome_min_distance", 150));
        this.sameBiomeProbeCount = Math.max(1, config.getInt("modules.biome_compass.same_biome_probe_count", 8));

        this.messageCooldown = getStringOrDefault(config, "modules.biome_compass.messages.cooldown",
                "<red>You must wait <yellow>%seconds%s</yellow> before scanning again!</red>");
        this.messageScanning = getStringOrDefault(config, "modules.biome_compass.messages.scanning",
                "<gray>Locating closest <aqua>%biome%</aqua>...</gray>");
        this.messageFound = getStringOrDefault(config, "modules.biome_compass.messages.found",
                "<green>Compass tuned to <aqua>%biome%</aqua> (~%distance%m away)!</green>");
        this.messageNotFound = getStringOrDefault(config, "modules.biome_compass.messages.not_found",
                "<red>No %biome% found within range.</red>");
        this.messageTooCloseToBorder = getStringOrDefault(config, "modules.biome_compass.messages.too_close_to_border",
                "<red>Cannot search: You are too close to the world border!</red>");
        this.messageOutsideBorder = getStringOrDefault(config, "modules.biome_compass.messages.outside_border",
                "<red>Closest %biome% is outside the world border.</red>");
        this.modeNearestName = getStringOrDefault(config, "modules.biome_compass.messages.mode_nearest_name", DEFAULT_MODE_NEAREST_NAME);
        this.modeNearestLore = getStringOrDefault(config, "modules.biome_compass.messages.mode_nearest_lore", DEFAULT_MODE_NEAREST_LORE);
        this.modeIgnoreName = getStringOrDefault(config, "modules.biome_compass.messages.mode_ignore_name", DEFAULT_MODE_IGNORE_NAME);
        this.modeIgnoreLore = getStringOrDefault(config, "modules.biome_compass.messages.mode_ignore_lore", DEFAULT_MODE_IGNORE_LORE);
    }

    private static String getStringOrDefault(org.bukkit.configuration.file.FileConfiguration config, String path, String def) {
        String val = config.getString(path);
        if (val == null || val.isEmpty()) {
            val = config.getString(path, def);
        }
        return (val != null && !val.isEmpty()) ? val : def;
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

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public int getSearchRadius() {
        return searchRadius;
    }

    public boolean isPlaySounds() {
        return playSounds;
    }

    public BiomeTierResolver getTierResolver() {
        return tierResolver;
    }

    public int resolveItemTier(org.bukkit.inventory.ItemStack item) {
        return tierResolver.resolveItemTier(item);
    }

    public int resolveBiomeTier(org.bukkit.NamespacedKey biomeKey) {
        return tierResolver.resolveBiomeTier(biomeKey);
    }

    public boolean isShowLockedBiomes() {
        return tierResolver.isShowLockedBiomes();
    }

    public String getLockedBiomeLore() {
        return tierResolver.getLockedBiomeLore();
    }

    public String getCooldownMessageTemplate() {
        return messageCooldown;
    }

    public String getScanningMessageTemplate() {
        return messageScanning;
    }

    public String getFoundMessageTemplate() {
        return messageFound;
    }

    public String getNotFoundMessageTemplate() {
        return messageNotFound;
    }

    public String getTooCloseToBorderMessageTemplate() {
        return messageTooCloseToBorder;
    }

    public String getOutsideBorderMessageTemplate() {
        return messageOutsideBorder;
    }

    public boolean isIgnoreCurrentBiomeMode(java.util.UUID uuid) {
        return ignoreCurrentBiomeMode.getOrDefault(uuid, false); // default = "Nearest"
    }

    public void toggleIgnoreCurrentBiomeMode(java.util.UUID uuid) {
        ignoreCurrentBiomeMode.merge(uuid, true, (old, val) -> !old);
    }

    public int getSameBiomeMinDistance() {
        return sameBiomeMinDistance;
    }

    public int getSameBiomeProbeCount() {
        return sameBiomeProbeCount;
    }

    public String getModeNearestName() {
        return modeNearestName;
    }

    public String getModeNearestLore() {
        return modeNearestLore;
    }

    public String getModeIgnoreName() {
        return modeIgnoreName;
    }

    public String getModeIgnoreLore() {
        return modeIgnoreLore;
    }

    /**
     * Calculates remaining cooldown time in ceiling seconds.
     * Formula: (lastTime + cooldownMillis - now) / 1000
     *
     * @param uuid the player's unique identifier
     * @return remaining seconds, or 0 if expired/not on cooldown
     */
    public long getRemainingCooldownSeconds(java.util.UUID uuid) {
        if (uuid == null) {
            return 0;
        }
        Long lastTime = lastSearchTimes.get(uuid);
        if (lastTime == null) {
            return 0;
        }
        long cooldownMillis = getCooldownSeconds() * 1000L;
        long now = System.currentTimeMillis();
        long remainingMillis = (lastTime + cooldownMillis) - now;
        if (remainingMillis <= 0) {
            lastSearchTimes.remove(uuid);
            return 0;
        }
        return (long) Math.ceil(remainingMillis / 1000.0);
    }

    /**
     * Checks if a player is currently on cooldown.
     *
     * @param uuid the player's unique identifier
     * @return true if currently on cooldown
     */
    public boolean isOnCooldown(java.util.UUID uuid) {
        return getRemainingCooldownSeconds(uuid) > 0;
    }

    /**
     * Updates the player's cooldown timestamp to the current epoch millis.
     *
     * @param uuid the player's unique identifier
     */
    public void updateSearchTimestamp(java.util.UUID uuid) {
        if (uuid == null) {
            return;
        }
        if (getCooldownSeconds() > 0) {
            lastSearchTimes.put(uuid, System.currentTimeMillis());
        }
    }

    /**
     * Sets the player's cooldown timestamp to now (for backwards compatibility).
     */
    public void setCooldown(java.util.UUID uuid) {
        updateSearchTimestamp(uuid);
    }

    /**
     * Clears cooldown for the given player.
     */
    public void clearCooldown(java.util.UUID uuid) {
        if (uuid != null) {
            lastSearchTimes.remove(uuid);
        }
    }

    /**
     * Clears all cooldowns in memory.
     */
    public void clearCooldowns() {
        lastSearchTimes.clear();
    }

    /**
     * Prunes all expired cooldown entries from memory.
     */
    public void cleanupExpiredCooldowns() {
        long now = System.currentTimeMillis();
        long cooldownMillis = getCooldownSeconds() * 1000L;
        lastSearchTimes.entrySet().removeIf(entry -> (now - entry.getValue()) >= cooldownMillis);
    }

    /**
     * Formats the configured cooldown message, replacing %seconds% and %time% placeholders
     * with the remaining seconds.
     *
     * @param remainingSeconds remaining cooldown time
     * @return formatted adventure Component
     */
    public net.kyori.adventure.text.Component formatCooldownMessage(long remainingSeconds) {
        String template = getCooldownMessageTemplate();
        String formatted = template
                .replace("%seconds%", String.valueOf(remainingSeconds))
                .replace("%time%", String.valueOf(remainingSeconds));
        return BiomeLocatorService.parseMessage(formatted);
    }
}

package net.vyrim.core.module.advancements;

import com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI;
import net.vyrim.core.VyrimCore;
import net.vyrim.core.hook.MMOItemsHook;
import net.vyrim.core.module.Module;
import net.vyrim.core.module.advancements.listener.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Module responsible for custom advancements, lifecycle management, and reward dispatch.
 */
public class AdvancementsModule implements Module {

    public static final String MODULE_NAME = "Advancements";

    private final VyrimCore core;
    private final AdvancementRewardService rewardService;
    private final AdvancementProgressStore progressStore;
    private final AdvancementTriggerService triggerService;

    private boolean enabled;
    private AdvancementLoader loader;
    private FileConfiguration advancementConfig;
    private File configFile;

    private final List<Listener> registeredListeners = new ArrayList<>();
    private AdvancementStatisticListener statisticListener;

    public AdvancementsModule(VyrimCore core) {
        this(core, null, null);
    }

    public AdvancementsModule(VyrimCore core, AdvancementProgressStore progressStore, AdvancementTriggerService triggerService) {
        this.core = core;
        this.rewardService = new AdvancementRewardService(core, this::getLoader);
        this.progressStore = progressStore != null ? progressStore : new AdvancementProgressStore(core);
        this.triggerService = triggerService != null ? triggerService : new AdvancementTriggerService(core, this::getLoader, rewardService, this.progressStore);
    }

    @Override
    public String name() {
        return MODULE_NAME;
    }

    @Override
    public String configKey() {
        return "advancements";
    }

    @Override
    public boolean isAvailable(VyrimCore core) {
        if (core == null || core.getConfig() == null) {
            return false;
        }

        if (!isConfigEnabled(core)) {
            core.getLogger().info("[Advancements] Module is disabled in config.yml.");
            return false;
        }

        try {
            if (Bukkit.getPluginManager() == null || !Bukkit.getPluginManager().isPluginEnabled("UltimateAdvancementAPI")) {
                core.getLogger().warning("[Advancements] UltimateAdvancementAPI is missing or disabled. Skipping module.");
                return false;
            }
            UltimateAdvancementAPI.getInstance(core);
        } catch (NoClassDefFoundError | Exception ex) {
            core.getLogger().warning("[Advancements] UltimateAdvancementAPI is not available or failed to initialize: " + ex.getMessage());
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

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Activates the module, loading advancements.yml, initializing tabs, and registering listeners.
     */
    public void enable() {
        if (enabled) {
            return;
        }

        if (!isAvailable(core)) {
            return;
        }

        try {
            loadConfigFile();
            UltimateAdvancementAPI api = UltimateAdvancementAPI.getInstance(core);
            this.loader = new AdvancementLoader(core, api);
            loader.loadTabs(advancementConfig);

            // Initialize progress store and load existing counters
            progressStore.init();
            progressStore.start(100L); // 5s periodic batch flush

            // Build reverse trigger index
            triggerService.buildIndex();

            registerListeners();

            this.enabled = true;
            core.getLogger().info("[Advancements] Advancements module successfully enabled.");
        } catch (Throwable t) {
            core.getLogger().log(Level.SEVERE, "[Advancements] Failed to enable Advancements module: " + t.getMessage(), t);
            disable();
        }
    }

    /**
     * Deactivates the module, disposing of active tabs, flushing progress, and unregistering listeners.
     */
    public void disable() {
        if (!enabled && loader == null) {
            return;
        }
        this.enabled = false;

        unregisterListeners();

        if (progressStore != null) {
            try {
                progressStore.close();
            } catch (Throwable t) {
                if (core != null && core.getLogger() != null) {
                    core.getLogger().warning("[Advancements] Error closing progress store: " + t.getMessage());
                }
            }
        }

        if (loader != null) {
            try {
                loader.unloadTabs();
            } catch (Throwable t) {
                if (core != null && core.getLogger() != null) {
                    core.getLogger().warning("[Advancements] Error unloading tabs during disable: " + t.getMessage());
                }
            }
            loader = null;
        }

        if (core != null && core.getLogger() != null) {
            core.getLogger().info("[Advancements] Advancements module disabled and cleaned up.");
        }
    }

    /**
     * Reloads configuration from disk and reconstructs tabs via the loader.
     */
    public void reload() {
        reload(core);
    }

    @Override
    public void reload(VyrimCore core) {
        if (!isConfigEnabled(core)) {
            disable();
            core.getLogger().info("[Advancements] Advancements module disabled on reload (disabled in config.yml).");
            return;
        }

        if (!isAvailable(core)) {
            disable();
            return;
        }

        try {
            if (loader != null) {
                loader.unloadTabs();
            }

            loadConfigFile();

            UltimateAdvancementAPI api = UltimateAdvancementAPI.getInstance(core);
            this.loader = new AdvancementLoader(core, api);
            loader.loadTabs(advancementConfig);

            // Rebuild trigger reverse index with new configuration
            triggerService.buildIndex();

            this.enabled = true;
            core.getLogger().info("[Advancements] Advancements configuration and tabs reloaded successfully.");
        } catch (Throwable t) {
            core.getLogger().log(Level.SEVERE, "[Advancements] Failed to reload Advancements module: " + t.getMessage(), t);
        }
    }

    private void loadConfigFile() {
        this.configFile = new File(core.getDataFolder(), "advancements.yml");
        if (!configFile.exists()) {
            core.saveResource("advancements.yml", false);
        }
        this.advancementConfig = YamlConfiguration.loadConfiguration(configFile);
    }

    void registerListeners() {
        if (Bukkit.getPluginManager() == null || core == null) {
            return;
        }

        unregisterListeners();

        this.statisticListener = new AdvancementStatisticListener(core, triggerService);
        statisticListener.startPeriodicTask(3600L); // 3 minutes

        MMOItemsHook mmoItemsHook = core.getMMOItemsHook();

        registeredListeners.add(new AdvancementJoinListener(core, triggerService, progressStore, this::getLoader));
        registeredListeners.add(new AdvancementBiomeListener(triggerService));
        registeredListeners.add(new AdvancementEntityDeathListener(triggerService));
        registeredListeners.add(new AdvancementBlockBreakListener(triggerService));
        registeredListeners.add(new AdvancementBlockPlaceListener(triggerService));
        registeredListeners.add(new AdvancementItemConsumeListener(triggerService, mmoItemsHook));
        registeredListeners.add(new AdvancementItemPickupListener(triggerService, mmoItemsHook));
        registeredListeners.add(new AdvancementCraftListener(triggerService, mmoItemsHook));
        registeredListeners.add(statisticListener);

        // Soft-dependent registration of MMOItems trigger listeners
        if (mmoItemsHook != null && mmoItemsHook.isFullyAvailable()) {
            registeredListeners.add(new AdvancementMMOItemCraftListener(mmoItemsHook, triggerService));
            registeredListeners.add(new AdvancementMMOItemPickupListener(mmoItemsHook, triggerService));
            registeredListeners.add(new AdvancementMMOItemConsumeListener(mmoItemsHook, triggerService));
            core.getLogger().info("[Advancements] MMOItems detected. Activated MMOItems trigger listeners (MMOITEM_CRAFT, MMOITEM_PICKUP, MMOITEM_CONSUME).");
        } else {
            core.getLogger().info("[Advancements] MMOItems is not installed or enabled. MMOItems triggers (MMOITEM_CRAFT, MMOITEM_PICKUP, MMOITEM_CONSUME) are inactive.");
        }

        for (Listener listener : registeredListeners) {
            Bukkit.getPluginManager().registerEvents(listener, core);
        }
    }

    List<Listener> getRegisteredListeners() {
        return Collections.unmodifiableList(registeredListeners);
    }

    private void unregisterListeners() {
        if (statisticListener != null) {
            statisticListener.stop();
            statisticListener = null;
        }

        for (Listener listener : registeredListeners) {
            try {
                HandlerList.unregisterAll(listener);
            } catch (Throwable ignored) {
            }
        }
        registeredListeners.clear();
    }

    public AdvancementRewardService getRewardService() {
        return rewardService;
    }

    public AdvancementLoader getLoader() {
        return loader;
    }

    public AdvancementProgressStore getProgressStore() {
        return progressStore;
    }

    public AdvancementTriggerService getTriggerService() {
        return triggerService;
    }

    public FileConfiguration getAdvancementConfig() {
        return advancementConfig;
    }
}

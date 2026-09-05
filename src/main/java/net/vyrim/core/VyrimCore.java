package net.vyrim.core;

import net.vyrim.core.command.VyrimCoreCommand;
import net.vyrim.core.hook.LuckPermsHook;
import net.vyrim.core.hook.MMOItemsHook;
import net.vyrim.core.module.ModuleManager;
import net.vyrim.core.module.biomecompass.BiomeCompassModule;
import net.vyrim.core.storage.StorageManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class VyrimCore extends JavaPlugin {
    private static VyrimCore instance;
    private StorageManager storageManager;
    private ModuleManager moduleManager;
    private MMOItemsHook mmoItemsHook;
    private LuckPermsHook luckPermsHook;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.storageManager = new StorageManager(this);
        storageManager.connect();

        this.mmoItemsHook = new MMOItemsHook(this);
        this.luckPermsHook = new LuckPermsHook(this);
        this.moduleManager = new ModuleManager(this);

        BiomeCompassModule biomeCompass = new BiomeCompassModule(this, mmoItemsHook);
        moduleManager.register(biomeCompass);

        // Register the skill immediately — MythicLib is guaranteed up, MMOItems isn't up yet, that's fine.
        mmoItemsHook.registerSkill(biomeCompass.getOrCreateAbility());

        moduleManager.enableAll(); // BiomeCompassModule.isAvailable() checks MMOItems -> false right now, module stays off, that's expected

        // Register commands
        PluginCommand vyrimcoreCmd = getCommand("vyrimcore");
        if (vyrimcoreCmd != null) {
            VyrimCoreCommand cmd = new VyrimCoreCommand(this);
            vyrimcoreCmd.setExecutor(cmd);
            vyrimcoreCmd.setTabCompleter(cmd);
        }
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableAll();
        }
        if (mmoItemsHook != null) {
            mmoItemsHook.close();
        }
        if (luckPermsHook != null) {
            luckPermsHook.close();
        }
        if (storageManager != null) {
            storageManager.close();
        }
    }

    /**
     * Safely reloads main configuration, re-evaluates hooks, and reloads modules.
     */
    public void reloadCore() {
        reloadConfig();
        if (mmoItemsHook != null) {
            mmoItemsHook.checkAvailability();
            mmoItemsHook.reRegisterAll();
        }
        if (luckPermsHook != null) {
            luckPermsHook.checkAvailability();
        }
        if (moduleManager != null) {
            moduleManager.reloadAll();
        }
    }

    public static VyrimCore getInstance() {
        return instance;
    }

    public StorageManager storage() {
        return storageManager;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public ModuleManager modules() {
        return moduleManager;
    }

    public MMOItemsHook getMMOItemsHook() {
        return mmoItemsHook;
    }

    public MMOItemsHook mmoItems() {
        return mmoItemsHook;
    }

    public LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }

    public LuckPermsHook luckPerms() {
        return luckPermsHook;
    }
}

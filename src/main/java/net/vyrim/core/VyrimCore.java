package net.vyrim.core;

import net.vyrim.core.hook.MMOItemsHook;
import net.vyrim.core.module.ModuleManager;
import net.vyrim.core.module.biomecompass.BiomeCompassModule;
import net.vyrim.core.storage.StorageManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class VyrimCore extends JavaPlugin {
    private static VyrimCore instance;
    private StorageManager storageManager;
    private ModuleManager moduleManager;
    private MMOItemsHook mmoItemsHook;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.storageManager = new StorageManager(this);
        storageManager.connect();

        this.mmoItemsHook = new MMOItemsHook(this);
        this.moduleManager = new ModuleManager(this);

        // Register core modules
        moduleManager.register(new BiomeCompassModule(this, mmoItemsHook));

        // Enable all registered modules
        moduleManager.enableAll();
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableAll();
        }
        if (storageManager != null) {
            storageManager.close();
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
}

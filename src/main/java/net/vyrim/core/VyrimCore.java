package net.vyrim.core;

import org.bukkit.plugin.java.JavaPlugin;
import net.vyrim.core.storage.StorageManager;

public final class VyrimCore extends JavaPlugin {
    private static VyrimCore instance;
    private StorageManager storageManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.storageManager = new StorageManager(this);
        storageManager.connect();
    }

    @Override
    public void onDisable() {
        if (storageManager != null) {
            storageManager.close();
        }
    }

    public StorageManager storage() {
        return storageManager;
    }
}

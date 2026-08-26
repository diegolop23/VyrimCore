package net.vyrim.core.module;

import net.vyrim.core.VyrimCore;

public interface Module {
    String name();

    /**
     * Override to check for soft-dependencies (e.g. LuckPerms, PlaceholderAPI)
     * before this module is enabled. Return false to skip it safely.
     */
    default boolean isAvailable(VyrimCore core) {
        return true;
    }
    void onEnable(VyrimCore plugin);
    void onDisable();
}

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

    /**
     * Whether the module is currently active.
     */
    default boolean isEnabled() {
        return false;
    }

    /**
     * Reloads the module. Disables the module if currently enabled,
     * checks availability against updated configuration, and re-enables if eligible.
     *
     * @param core the plugin instance
     */
    default void reload(VyrimCore core) {
        if (isEnabled()) {
            onDisable();
        }
        if (isAvailable(core)) {
            onEnable(core);
        }
    }
}

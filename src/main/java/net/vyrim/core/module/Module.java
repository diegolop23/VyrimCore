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
     * The configuration path key for this module, e.g. "biome_compass".
     * Defaults to the snake_case representation of name().
     */
    default String configKey() {
        return name().replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }

    /**
     * Checks whether the module is enabled in config.yml under modules.<configKey>.enabled.
     */
    default boolean isConfigEnabled(VyrimCore core) {
        if (core == null || core.getConfig() == null) {
            return true;
        }
        return core.getConfig().getBoolean("modules." + configKey() + ".enabled", true);
    }

    default void enable(VyrimCore core) {
        onEnable(core);
    }

    default void disable() {
        onDisable();
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

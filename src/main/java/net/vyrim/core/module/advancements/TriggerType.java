package net.vyrim.core.module.advancements;

import java.util.Locale;

/**
 * Supported trigger types for custom advancements.
 */
public enum TriggerType {
    JOIN_SERVER,
    DISCOVER_BIOME,
    KILL_ENTITY,
    BREAK_BLOCK,
    PLACE_BLOCK,
    CRAFT_ITEM,
    CONSUME_ITEM,
    PICKUP_ITEM,
    STATISTIC,

    // Phase 3 MMOItems triggers
    MMOITEM_CRAFT,
    MMOITEM_PICKUP,
    MMOITEM_CONSUME,

    UNKNOWN;

    /**
     * Resolves a TriggerType by its name, case-insensitively.
     *
     * @param name the trigger name
     * @return the matching TriggerType, or UNKNOWN if null/unrecognized
     */
    public static TriggerType fromString(String name) {
        if (name == null || name.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}

package net.vyrim.core.module.biomecompass;

import io.lumine.mythic.lib.api.item.NBTItem;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves item and biome tiers from configuration for the Nature Compass system.
 * Caches tier lookups for O(1) evaluation during GUI rendering and pagination.
 */
public class BiomeTierResolver {

    public static final int DEFAULT_TIER = 1;
    public static final String DEFAULT_LOCKED_LORE = "&c🔒 Requires a higher-tier Nature Compass";

    private final Map<String, Integer> biomeTierCache = new HashMap<>();
    private final Map<String, Integer> itemTierCache = new HashMap<>();
    private boolean showLockedBiomes = true;
    private String lockedBiomeLore = DEFAULT_LOCKED_LORE;

    public BiomeTierResolver() {
    }

    /**
     * Loads or reloads tier mappings and settings from the given configuration section.
     * Accepts either the root configuration or the "modules.biome_compass" section.
     *
     * @param config the configuration source
     */
    public void loadConfiguration(ConfigurationSection config) {
        biomeTierCache.clear();
        itemTierCache.clear();
        showLockedBiomes = true;
        lockedBiomeLore = DEFAULT_LOCKED_LORE;

        if (config == null) {
            return;
        }

        ConfigurationSection section = config.getConfigurationSection("modules.biome_compass");
        if (section == null) {
            section = config;
        }

        this.showLockedBiomes = section.getBoolean("show_locked_biomes", true);
        this.lockedBiomeLore = section.getString("locked_biome_lore", DEFAULT_LOCKED_LORE);

        // Parse biome tiers: tier number -> list of biome path names
        ConfigurationSection biomeSection = section.getConfigurationSection("biome_tiers");
        if (biomeSection != null) {
            for (String key : biomeSection.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(key);
                    List<String> biomes = biomeSection.getStringList(key);
                    for (String biome : biomes) {
                        if (biome != null && !biome.isBlank()) {
                            biomeTierCache.put(biome.trim().toLowerCase(), tier);
                        }
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Parse item tiers: "Type.Id" -> tier number
        ConfigurationSection itemSection = section.getConfigurationSection("item_tiers");
        if (itemSection != null) {
            for (String key : itemSection.getKeys(true)) {
                if (!itemSection.isConfigurationSection(key)) {
                    int tier = itemSection.getInt(key, DEFAULT_TIER);
                    itemTierCache.put(key.trim().toUpperCase(), tier);
                }
            }
            for (String key : itemSection.getKeys(false)) {
                if (!itemSection.isConfigurationSection(key)) {
                    int tier = itemSection.getInt(key, DEFAULT_TIER);
                    itemTierCache.put(key.trim().toUpperCase(), tier);
                }
            }
        }
    }

    /**
     * Resolves the unlocked tier for a given ItemStack.
     * Reads the MMOItems Type and ID via MythicLib/MMOItems NBTItem API.
     * Defaults to tier 1 if the item is null, has no meta, is unrecognized, or on any error.
     *
     * @param item the item stack
     * @return resolved tier integer (>= 1)
     */
    public int resolveItemTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return DEFAULT_TIER;
        }
        try {
            NBTItem nbt = NBTItem.get(item);
            if (nbt == null || !nbt.hasType()) {
                return DEFAULT_TIER;
            }
            String type = nbt.getType();
            String id = nbt.getString("MMOITEMS_ITEM_ID");
            return resolveItemTier(type, id);
        } catch (Throwable ignored) {
            return DEFAULT_TIER;
        }
    }

    /**
     * Resolves the unlocked tier for a given MMOItems Type and ID string.
     *
     * @param type the MMOItems type name (e.g. "COMPASS")
     * @param id   the MMOItems item id (e.g. "NATURE_COMPASS_T2")
     * @return resolved tier integer (>= 1)
     */
    public int resolveItemTier(String type, String id) {
        if (type == null || id == null || type.isBlank() || id.isBlank()) {
            return DEFAULT_TIER;
        }
        String lookupKey = (type.trim() + "." + id.trim()).toUpperCase();
        return itemTierCache.getOrDefault(lookupKey, DEFAULT_TIER);
    }

    /**
     * Resolves the tier required to view/select a biome.
     * Uses the cached biome path for O(1) lookup.
     *
     * @param biomeKey the namespaced key of the biome
     * @return tier integer (defaults to 1 if unlisted)
     */
    public int resolveBiomeTier(NamespacedKey biomeKey) {
        if (biomeKey == null) {
            return DEFAULT_TIER;
        }
        String path = biomeKey.getKey().toLowerCase();
        return biomeTierCache.getOrDefault(path, DEFAULT_TIER);
    }

    public boolean isShowLockedBiomes() {
        return showLockedBiomes;
    }

    public String getLockedBiomeLore() {
        return lockedBiomeLore;
    }

    public Map<String, Integer> getBiomeTiers() {
        return Collections.unmodifiableMap(biomeTierCache);
    }

    public Map<String, Integer> getItemTiers() {
        return Collections.unmodifiableMap(itemTierCache);
    }
}

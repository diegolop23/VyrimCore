package net.vyrim.core.module.advancements;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Resolves icon strings into {@link ItemStack} representations.
 * Supports vanilla {@link Material} names as well as custom namespaced
 * model references (e.g., {@code vyrim:advancement/dragon_head}) mapped to
 * the {@code minecraft:item_model} component with a configurable fallback base item.
 */
public final class AdvancementIconResolver {

    public static final Material DEFAULT_FALLBACK_BASE_ITEM = Material.PAPER;

    private AdvancementIconResolver() {
        // Utility class
    }

    /**
     * Resolves a raw icon string into an ItemStack using the specified fallback item.
     *
     * @param rawValue the raw string from YAML (e.g. "DIAMOND_SWORD" or "vyrim:advancement/dragon_head")
     * @param fallbackBaseItem the base item Material for custom models or invalid icons
     * @return the resolved ItemStack
     */
    public static ItemStack resolveIcon(String rawValue, Material fallbackBaseItem) {
        return resolveIcon(rawValue, fallbackBaseItem, null, ItemStack::new);
    }

    /**
     * Resolves a raw icon string into an ItemStack using the specified fallback item and logger.
     *
     * @param rawValue the raw string from YAML
     * @param fallbackBaseItem the base item Material for custom models or invalid icons
     * @param logger logger to output warnings if formatting or material resolution fails
     * @return the resolved ItemStack
     */
    public static ItemStack resolveIcon(String rawValue, Material fallbackBaseItem, Logger logger) {
        return resolveIcon(rawValue, fallbackBaseItem, logger, ItemStack::new);
    }

    /**
     * Resolves a raw icon string into an ItemStack using the specified fallback item, logger,
     * and a custom item factory (useful for unit tests or custom item factories).
     *
     * @param rawValue the raw string from YAML
     * @param fallbackBaseItem the base item Material for custom models or invalid icons
     * @param logger logger to output warnings if formatting or material resolution fails
     * @param itemFactory function to construct an ItemStack for a given Material
     * @return the resolved ItemStack
     */
    public static ItemStack resolveIcon(
            String rawValue,
            Material fallbackBaseItem,
            Logger logger,
            Function<Material, ItemStack> itemFactory
    ) {
        Function<Material, ItemStack> factory = (itemFactory != null) ? itemFactory : ItemStack::new;
        Material effectiveFallback = (fallbackBaseItem != null) ? fallbackBaseItem : DEFAULT_FALLBACK_BASE_ITEM;

        if (rawValue == null || rawValue.isBlank()) {
            if (logger != null) {
                logger.warning("[Advancements] Icon value is missing or blank. Defaulting to fallback base item '"
                        + effectiveFallback.name() + "'.");
            }
            return factory.apply(effectiveFallback);
        }

        String trimmed = rawValue.trim();

        // 1. If there is no colon, treat as a vanilla Material enum name
        if (!trimmed.contains(":")) {
            Material material = Material.matchMaterial(trimmed);
            if (material != null) {
                return factory.apply(material);
            }

            if (logger != null) {
                logger.warning("[Advancements] Invalid icon '" + trimmed
                        + "': not a valid Material enum and does not follow 'namespace:path' format. Defaulting to '"
                        + effectiveFallback.name() + "'.");
            }
            return factory.apply(effectiveFallback);
        }

        // 2. Contains colon: treat as namespace:path item model reference
        int colonIndex = trimmed.indexOf(':');
        String namespace = trimmed.substring(0, colonIndex).trim();
        String path = trimmed.substring(colonIndex + 1).trim();

        if (namespace.isEmpty() || path.isEmpty()) {
            if (logger != null) {
                logger.warning("[Advancements] Malformed namespaced icon '" + trimmed
                        + "': namespace and path cannot be empty. Defaulting to '" + effectiveFallback.name() + "'.");
            }
            return factory.apply(effectiveFallback);
        }

        NamespacedKey modelKey;
        try {
            modelKey = new NamespacedKey(namespace.toLowerCase(Locale.ROOT), path.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            if (logger != null) {
                logger.warning("[Advancements] Invalid namespaced icon key '" + trimmed + "': "
                        + ex.getMessage() + ". Defaulting to '" + effectiveFallback.name() + "'.");
            }
            return factory.apply(effectiveFallback);
        }

        ItemStack item = factory.apply(effectiveFallback);
        if (item != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setItemModel(modelKey);
                item.setItemMeta(meta);
            }
        }

        return item;
    }

    /**
     * Resolves a raw fallback material name string into a {@link Material}.
     * Defaults to {@link #DEFAULT_FALLBACK_BASE_ITEM} (PAPER) if null, blank, or invalid.
     *
     * @param rawFallback the fallback Material name from YAML
     * @param logger logger for warnings
     * @return the resolved Material, never null
     */
    public static Material resolveFallbackMaterial(String rawFallback, Logger logger) {
        if (rawFallback == null || rawFallback.isBlank()) {
            return DEFAULT_FALLBACK_BASE_ITEM;
        }

        String trimmed = rawFallback.trim();
        Material matched = Material.matchMaterial(trimmed);
        if (matched != null) {
            return matched;
        }

        if (logger != null) {
            logger.warning("[Advancements] Invalid icon_fallback Material '" + trimmed
                    + "'. Defaulting to '" + DEFAULT_FALLBACK_BASE_ITEM.name() + "'.");
        }
        return DEFAULT_FALLBACK_BASE_ITEM;
    }
}

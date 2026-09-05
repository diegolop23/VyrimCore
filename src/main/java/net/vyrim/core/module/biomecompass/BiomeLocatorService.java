package net.vyrim.core.module.biomecompass;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.LodestoneTracker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.vyrim.core.VyrimCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BiomeSearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Executes asynchronous biome searches and calibrates compass items.
 */
public class BiomeLocatorService {

    public static final String PDC_TARGET_BIOME_KEY_NAME = "compass_target_biome";
    public static final String PDC_TARGET_X_KEY_NAME = "compass_target_x";
    public static final String PDC_TARGET_Y_KEY_NAME = "compass_target_y";
    public static final String PDC_TARGET_Z_KEY_NAME = "compass_target_z";
    public static final String PDC_TARGET_WORLD_KEY_NAME = "compass_target_world";
    public static final String PDC_TARGET_DIST_KEY_NAME = "compass_target_dist";

    public static final String DEFAULT_SCANNING_MESSAGE = "<gray>Locating closest <aqua>%biome%</aqua>...</gray>";
    public static final String DEFAULT_FOUND_MESSAGE = "<green>Compass tuned to <aqua>%biome%</aqua> (~%distance%m away)!</green>";
    public static final String DEFAULT_NOT_FOUND_MESSAGE = "<red>No %biome% found within range.</red>";
    public static final String DEFAULT_TOO_CLOSE_TO_BORDER_MESSAGE = "<red>Cannot search: You are too close to the world border!</red>";
    public static final String DEFAULT_OUTSIDE_BORDER_MESSAGE = "<red>Closest %biome% is outside the world border.</red>";

    private final VyrimCore core;
    private final NamespacedKey pdcTargetBiome;
    private final NamespacedKey pdcTargetX;
    private final NamespacedKey pdcTargetY;
    private final NamespacedKey pdcTargetZ;
    private final NamespacedKey pdcTargetWorld;
    private final NamespacedKey pdcTargetDist;

    private final Set<CompletableFuture<?>> activeFutures = ConcurrentHashMap.newKeySet();

    public BiomeLocatorService(VyrimCore core) {
        this.core = core;
        this.pdcTargetBiome = createKey(PDC_TARGET_BIOME_KEY_NAME);
        this.pdcTargetX = createKey(PDC_TARGET_X_KEY_NAME);
        this.pdcTargetY = createKey(PDC_TARGET_Y_KEY_NAME);
        this.pdcTargetZ = createKey(PDC_TARGET_Z_KEY_NAME);
        this.pdcTargetWorld = createKey(PDC_TARGET_WORLD_KEY_NAME);
        this.pdcTargetDist = createKey(PDC_TARGET_DIST_KEY_NAME);
    }

    private NamespacedKey createKey(String key) {
        try {
            if (core != null && core.getName() != null) {
                return new NamespacedKey(core, key);
            }
        } catch (Throwable ignored) {
        }
        return NamespacedKey.fromString("vyrimcore:" + key);
    }

    public int getSearchRadius() {
        if (core == null || core.getConfig() == null) {
            return 6400;
        }
        return core.getConfig().getInt("modules.biome_compass.radius",
                core.getConfig().getInt("modules.biome_compass.search_radius", 6400));
    }

    public boolean isPlaySounds() {
        if (core == null || core.getConfig() == null) {
            return true;
        }
        return core.getConfig().getBoolean("modules.biome_compass.play_sounds", true);
    }

    /**
     * Parses formatted component supporting both MiniMessage tags (<color>) and legacy codes (&c).
     */
    public static Component parseMessage(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        if (input.contains("&")) {
            return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(input);
        }
        try {
            return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(input);
        } catch (Throwable ignored) {
            return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(input);
        }
    }

    private String getMessage(String key, String def) {
        if (core == null || core.getConfig() == null) {
            return def;
        }
        String val = core.getConfig().getString("modules.biome_compass.messages." + key, def);
        return val != null ? val : def;
    }

    /**
     * Clamps the search radius so that a square search area of side 2 * radius centered on origin
     * remains entirely within the world border.
     */
    private int clampRadiusToBorder(Location origin, int desiredRadius) {
        if (origin == null || origin.getWorld() == null) {
            return desiredRadius;
        }
        WorldBorder border = origin.getWorld().getWorldBorder();
        if (border == null) {
            return desiredRadius;
        }

        Location center = border.getCenter();
        double size = border.getSize();
        double halfSize = size / 2.0;

        double minX = center.getX() - halfSize;
        double maxX = center.getX() + halfSize;
        double minZ = center.getZ() - halfSize;
        double maxZ = center.getZ() + halfSize;

        double distWest = origin.getX() - minX;
        double distEast = maxX - origin.getX();
        double distNorth = origin.getZ() - minZ;
        double distSouth = maxZ - origin.getZ();

        double minDist = Math.min(Math.min(distWest, distEast), Math.min(distNorth, distSouth));
        int allowableRadius = (int) Math.floor(minDist);
        return Math.min(desiredRadius, allowableRadius);
    }

    /**
     * Dispatches an asynchronous biome location search.
     */
    public void locateBiome(Player player, Biome biome, NamespacedKey biomeKey) {
        locateBiome(player, biome, biomeKey, EquipmentSlot.HAND, player.getInventory().getHeldItemSlot());
    }

    /**
     * Dispatches an asynchronous biome location search for a specific item hand and slot.
     */
    public void locateBiome(Player player, Biome biome, NamespacedKey biomeKey, EquipmentSlot hand, int slot) {
        UUID playerUuid = player.getUniqueId();
        Location playerLoc = player.getLocation().clone();
        World world = playerLoc.getWorld();
        int desiredRadius = getSearchRadius();
        int searchRadius = clampRadiusToBorder(playerLoc, desiredRadius);
        String friendlyName = formatBiomeName(biomeKey);

        if (searchRadius <= 0) {
            String tooCloseTemplate = getMessage("too_close_to_border", DEFAULT_TOO_CLOSE_TO_BORDER_MESSAGE);
            player.sendMessage(parseMessage(tooCloseTemplate.replace("%biome%", friendlyName)));
            if (isPlaySounds()) {
                player.playSound(playerLoc, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
            return;
        }

        String scanningTemplate = getMessage("scanning", DEFAULT_SCANNING_MESSAGE);
        player.sendMessage(parseMessage(scanningTemplate.replace("%biome%", friendlyName)));

        if (isPlaySounds()) {
            player.playSound(playerLoc, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        }

        CompletableFuture<BiomeSearchResult> future = CompletableFuture.supplyAsync(() -> {
            if (world == null) return null;
            // Vanilla-speed sampling: horizontalInterval = 32, verticalInterval = 64
            return world.locateNearestBiome(playerLoc, searchRadius, 32, 64, biome);
        });

        activeFutures.add(future);

        future.whenComplete((result, ex) -> {
            activeFutures.remove(future);
            Bukkit.getScheduler().runTask(core, () -> {
                if (ex != null) {
                    core.getLogger().log(Level.WARNING, "Failed to locate biome " + biomeKey + ": " + ex.getMessage(), ex);
                    Player p = Bukkit.getPlayer(playerUuid);
                    if (p != null && p.isOnline()) {
                        p.sendMessage(Component.text("❌ An error occurred while searching for the biome.", NamedTextColor.RED));
                    }
                    return;
                }
                handleSearchResult(playerUuid, biomeKey, friendlyName, playerLoc, result, searchRadius, hand, slot);
            });
        });
    }

    private void handleSearchResult(UUID playerUuid, NamespacedKey biomeKey, String friendlyName,
                                    Location playerLoc, BiomeSearchResult searchResult, int searchRadius,
                                    EquipmentSlot hand, int slot) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        if (searchResult == null || searchResult.getLocation() == null) {
            String notFoundTemplate = getMessage("not_found", DEFAULT_NOT_FOUND_MESSAGE);
            player.sendMessage(parseMessage(notFoundTemplate.replace("%biome%", friendlyName)));

            if (isPlaySounds()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
            return;
        }

        Location target = searchResult.getLocation().clone();
        if (target.getWorld() == null && playerLoc.getWorld() != null) {
            target.setWorld(playerLoc.getWorld());
        }

        World targetWorld = target.getWorld();
        if (targetWorld != null && targetWorld.getWorldBorder() != null && !targetWorld.getWorldBorder().isInside(target)) {
            String outsideBorderTemplate = getMessage("outside_border", DEFAULT_OUTSIDE_BORDER_MESSAGE);
            double distance = playerLoc.distance(target);
            long blockDist = Math.round(distance);
            String formatted = outsideBorderTemplate
                    .replace("%biome%", friendlyName)
                    .replace("%distance%", String.format("%,d", blockDist))
                    .replace("%x%", String.valueOf(target.getBlockX()))
                    .replace("%z%", String.valueOf(target.getBlockZ()));
            player.sendMessage(parseMessage(formatted));

            if (isPlaySounds()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
            return;
        }

        ItemStack compass = findTargetItem(player, hand, slot);
        if (compass == null || compass.getType().isAir()) {
            player.sendMessage(Component.text("❌ Could not calibrate: You are no longer holding a compass!", NamedTextColor.RED));
            if (isPlaySounds()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
            return;
        }

        double distance = playerLoc.distance(target);
        long blockDist = Math.round(distance);

        // Calibrate lodestone needle and update lore/PDC via modern editMeta
        compass.editMeta(CompassMeta.class, compassMeta -> {
            compassMeta.setLodestone(target);
            compassMeta.setLodestoneTracked(false);

            PersistentDataContainer pdc = compassMeta.getPersistentDataContainer();
            pdc.set(pdcTargetBiome, PersistentDataType.STRING, biomeKey.toString());
            pdc.set(pdcTargetX, PersistentDataType.INTEGER, target.getBlockX());
            pdc.set(pdcTargetY, PersistentDataType.INTEGER, target.getBlockY());
            pdc.set(pdcTargetZ, PersistentDataType.INTEGER, target.getBlockZ());
            pdc.set(pdcTargetWorld, PersistentDataType.STRING, target.getWorld() != null ? target.getWorld().getName() : "");
            pdc.set(pdcTargetDist, PersistentDataType.LONG, blockDist);

            updateCompassLore(compassMeta, friendlyName, target, blockDist);
        });

        // Set modern Paper 1.21+ minecraft:lodestone_tracker Data Component
        try {
            compass.setData(DataComponentTypes.LODESTONE_TRACKER, LodestoneTracker.lodestoneTracker(target, false));
        } catch (Throwable ignored) {
            // DataComponentTypes fallback handled by CompassMeta
        }

        // Ensure the updated item is saved to the player's inventory slot
        setTargetItem(player, hand, slot, compass);
        player.updateInventory();

        if (isPlaySounds()) {
            player.playSound(player.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
        }

        String foundTemplate = getMessage("found", DEFAULT_FOUND_MESSAGE);
        String formattedFound = foundTemplate
                .replace("%biome%", friendlyName)
                .replace("%distance%", String.format("%,d", blockDist))
                .replace("%x%", String.valueOf(target.getBlockX()))
                .replace("%z%", String.valueOf(target.getBlockZ()));
        player.sendMessage(parseMessage(formattedFound));
    }


    public static void updateCompassLore(CompassMeta meta, String friendlyName, Location target, long distance) {
        List<Component> currentLore = meta.lore();
        List<Component> newLore = new ArrayList<>();

        if (currentLore != null) {
            for (Component line : currentLore) {
                String plain = PlainTextComponentSerializer.plainText().serialize(line);
                if (plain.startsWith("Target Biome:") || plain.startsWith("Coordinates:")
                        || plain.startsWith("Distance:") || plain.equals("--- Biome Tracking ---")
                        || plain.startsWith("Tracking:")) {
                    continue;
                }
                newLore.add(line);
            }
            while (!newLore.isEmpty() && PlainTextComponentSerializer.plainText().serialize(newLore.getLast()).isBlank()) {
                newLore.removeLast();
            }
        }

        if (!newLore.isEmpty()) {
            newLore.add(Component.empty());
        }

        newLore.add(Component.text("--- Biome Tracking ---", NamedTextColor.DARK_AQUA, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        newLore.add(Component.text("Target Biome: ", NamedTextColor.GRAY)
                .append(Component.text(friendlyName, NamedTextColor.GOLD, TextDecoration.BOLD))
                .decoration(TextDecoration.ITALIC, false));
        newLore.add(Component.text("Coordinates: ", NamedTextColor.GRAY)
                .append(Component.text("X: " + target.getBlockX() + ", Z: " + target.getBlockZ(), NamedTextColor.AQUA))
                .decoration(TextDecoration.ITALIC, false));
        newLore.add(Component.text("Distance: ", NamedTextColor.GRAY)
                .append(Component.text("~" + String.format("%,d", distance) + " blocks", NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(newLore);
    }

    private ItemStack findTargetItem(Player player, EquipmentSlot hand, int slot) {
        if (hand == EquipmentSlot.OFF_HAND || slot == 40) {
            ItemStack off = player.getInventory().getItemInOffHand();
            if (isCompass(off)) return off;
        }
        if (slot >= 0 && slot < 9) {
            ItemStack slotItem = player.getInventory().getItem(slot);
            if (isCompass(slotItem)) return slotItem;
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        if (isCompass(main)) return main;
        ItemStack off = player.getInventory().getItemInOffHand();
        if (isCompass(off)) return off;
        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isCompass(item)) return item;
        }
        return null;
    }

    private void setTargetItem(Player player, EquipmentSlot hand, int slot, ItemStack item) {
        if (hand == EquipmentSlot.OFF_HAND || slot == 40) {
            player.getInventory().setItemInOffHand(item);
        } else if (slot >= 0 && slot < 9) {
            player.getInventory().setItem(slot, item);
        } else {
            player.getInventory().setItemInMainHand(item);
        }
    }

    private boolean isCompass(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return item.getType() == Material.COMPASS || item.getItemMeta() instanceof CompassMeta;
    }

    public static String formatBiomeName(NamespacedKey key) {
        if (key == null) {
            return "Unknown";
        }
        String path = key.getKey();
        String[] words = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (words[i].isEmpty()) continue;
            sb.append(Character.toUpperCase(words[i].charAt(0)));
            if (words[i].length() > 1) {
                sb.append(words[i].substring(1).toLowerCase());
            }
            if (i < words.length - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    /**
     * Cancels all active async search tasks during module shutdown.
     */
    public void shutdown() {
        for (CompletableFuture<?> future : activeFutures) {
            future.cancel(true);
        }
        activeFutures.clear();
    }
}

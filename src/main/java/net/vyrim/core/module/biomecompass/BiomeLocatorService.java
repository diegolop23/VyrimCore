package net.vyrim.core.module.biomecompass;

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
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

    private final VyrimCore core;
    private final NamespacedKey pdcTargetBiome;
    private final NamespacedKey pdcTargetX;
    private final NamespacedKey pdcTargetY;
    private final NamespacedKey pdcTargetZ;
    private final NamespacedKey pdcTargetWorld;
    private final NamespacedKey pdcTargetDist;

    private final Set<Integer> runningTaskIds = ConcurrentHashMap.newKeySet();

    public BiomeLocatorService(VyrimCore core) {
        this.core = core;
        this.pdcTargetBiome = new NamespacedKey(core, PDC_TARGET_BIOME_KEY_NAME);
        this.pdcTargetX = new NamespacedKey(core, PDC_TARGET_X_KEY_NAME);
        this.pdcTargetY = new NamespacedKey(core, PDC_TARGET_Y_KEY_NAME);
        this.pdcTargetZ = new NamespacedKey(core, PDC_TARGET_Z_KEY_NAME);
        this.pdcTargetWorld = new NamespacedKey(core, PDC_TARGET_WORLD_KEY_NAME);
        this.pdcTargetDist = new NamespacedKey(core, PDC_TARGET_DIST_KEY_NAME);
    }

    public int getSearchRadius() {
        return core.getConfig().getInt("modules.biome_compass.search_radius", 6400);
    }

    public boolean isPlaySounds() {
        return core.getConfig().getBoolean("modules.biome_compass.play_sounds", true);
    }

    /**
     * Dispatches an asynchronous biome location search.
     *
     * @param player   the player searching for a biome
     * @param biome    the target Biome
     * @param biomeKey the NamespacedKey identifying the biome
     */
    public void locateBiome(Player player, Biome biome, NamespacedKey biomeKey) {
        UUID playerUuid = player.getUniqueId();
        Location playerLoc = player.getLocation().clone();
        World world = playerLoc.getWorld();
        int searchRadius = getSearchRadius();
        String friendlyName = formatBiomeName(biomeKey);

        player.sendMessage(Component.text()
                .append(Component.text("🧭 ", NamedTextColor.GOLD))
                .append(Component.text("Locating nearest ", NamedTextColor.GRAY))
                .append(Component.text(friendlyName, NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" (within " + searchRadius + " blocks)...", NamedTextColor.GRAY))
                .build());

        if (isPlaySounds()) {
            player.playSound(playerLoc, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        }

        BukkitTask[] taskRef = new BukkitTask[1];
        taskRef[0] = Bukkit.getScheduler().runTaskAsynchronously(core, () -> {
            try {
                Location target = world != null ? world.locateNearestBiome(playerLoc, biome, searchRadius) : null;

                Bukkit.getScheduler().runTask(core, () -> {
                    runningTaskIds.remove(taskRef[0].getTaskId());
                    handleSearchResult(playerUuid, biomeKey, friendlyName, playerLoc, target, searchRadius);
                });
            } catch (Exception ex) {
                core.getLogger().log(Level.WARNING, "Failed to locate biome " + biomeKey + ": " + ex.getMessage(), ex);
                Bukkit.getScheduler().runTask(core, () -> {
                    runningTaskIds.remove(taskRef[0].getTaskId());
                    Player p = Bukkit.getPlayer(playerUuid);
                    if (p != null && p.isOnline()) {
                        p.sendMessage(Component.text("❌ An error occurred while searching for the biome.", NamedTextColor.RED));
                    }
                });
            }
        });

        runningTaskIds.add(taskRef[0].getTaskId());
    }

    private void handleSearchResult(UUID playerUuid, NamespacedKey biomeKey, String friendlyName,
                                    Location playerLoc, Location target, int searchRadius) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        if (target == null) {
            player.sendMessage(Component.text()
                    .append(Component.text("❌ ", NamedTextColor.RED))
                    .append(Component.text("No ", NamedTextColor.GRAY))
                    .append(Component.text(friendlyName, NamedTextColor.GOLD))
                    .append(Component.text(" was found within " + searchRadius + " blocks.", NamedTextColor.GRAY))
                    .build());

            if (isPlaySounds()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
            return;
        }

        ItemStack compass = findCompassItem(player);
        if (compass == null || !(compass.getItemMeta() instanceof CompassMeta compassMeta)) {
            player.sendMessage(Component.text("❌ Could not calibrate: You are no longer holding a compass!", NamedTextColor.RED));
            if (isPlaySounds()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
            return;
        }

        // Calibrate lodestone needle (untracked points straight to coordinates)
        compassMeta.setLodestone(target);
        compassMeta.setLodestoneTracked(false);

        double distance = playerLoc.distance(target);
        long blockDist = Math.round(distance);

        // State persistence in PDC
        PersistentDataContainer pdc = compassMeta.getPersistentDataContainer();
        pdc.set(pdcTargetBiome, PersistentDataType.STRING, biomeKey.toString());
        pdc.set(pdcTargetX, PersistentDataType.INTEGER, target.getBlockX());
        pdc.set(pdcTargetY, PersistentDataType.INTEGER, target.getBlockY());
        pdc.set(pdcTargetZ, PersistentDataType.INTEGER, target.getBlockZ());
        pdc.set(pdcTargetWorld, PersistentDataType.STRING, target.getWorld() != null ? target.getWorld().getName() : "");
        pdc.set(pdcTargetDist, PersistentDataType.LONG, blockDist);

        // Update lore while preserving any existing MMOItems lore
        updateCompassLore(compassMeta, friendlyName, target, blockDist);
        compass.setItemMeta(compassMeta);

        if (isPlaySounds()) {
            player.playSound(player.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
        }

        player.sendMessage(Component.text()
                .append(Component.text("✔ Compass calibrated to ", NamedTextColor.GREEN))
                .append(Component.text(friendlyName, NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("! (~" + String.format("%,d", blockDist) + " blocks away at X: "
                        + target.getBlockX() + ", Z: " + target.getBlockZ() + ")", NamedTextColor.GRAY))
                .build());
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

    private ItemStack findCompassItem(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (isCompass(main)) {
            return main;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (isCompass(off)) {
            return off;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isCompass(item)) {
                return item;
            }
        }
        return null;
    }

    private boolean isCompass(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return item.getType() == Material.COMPASS || meta instanceof CompassMeta;
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
        for (int taskId : runningTaskIds) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        runningTaskIds.clear();
    }
}

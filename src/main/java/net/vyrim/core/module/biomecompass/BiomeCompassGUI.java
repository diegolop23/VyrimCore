package net.vyrim.core.module.biomecompass;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.tag.Tag;
import io.papermc.paper.registry.tag.TagKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.vyrim.core.VyrimCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Dynamic paginated GUI displaying all biomes registered in the server instance,
 * filtered according to the player's current dimension environment.
 */
public class BiomeCompassGUI implements Listener {

    public static final int GRID_COLS = 9;
    public static final int GRID_ROWS = 6;
    public static final int CONTENT_COLS = 7; // columns 1-7 (0 and 8 are border)
    public static final int CONTENT_ROWS = 4; // rows 1-4 (row 0 and 5 are border)
    public static final int PAGE_SIZE = CONTENT_COLS * CONTENT_ROWS; // 28
    public static final int INVENTORY_SIZE = GRID_ROWS * GRID_COLS;  // 54

    private static final int NAV_PREV_SLOT = 48;
    private static final int NAV_INFO_SLOT = 49;
    private static final int NAV_NEXT_SLOT = 50;

    private static final String ACTION_PREV_PAGE = "PREV_PAGE";
    private static final String ACTION_NEXT_PAGE = "NEXT_PAGE";
    private static final String ACTION_INFO = "INFO";

    private static final Set<String> NETHER_FALLBACK = Set.of(
            "nether_wastes", "crimson_forest", "warped_forest", "soul_sand_valley", "basalt_deltas"
    );

    private static final Set<String> END_FALLBACK = Set.of(
            "the_end", "small_end_islands", "end_midlands", "end_highlands", "end_barrens", "the_void"
    );

    private final VyrimCore core;
    private final BiomeLocatorService locatorService;
    private final BiomeCompassModule module;
    private final NamespacedKey pdcBiomeKey;
    private final NamespacedKey pdcActionKey;

    public BiomeCompassGUI(VyrimCore core, BiomeLocatorService locatorService) {
        this(core, locatorService, null);
    }

    public BiomeCompassGUI(VyrimCore core, BiomeLocatorService locatorService, BiomeCompassModule module) {
        this.core = core;
        this.locatorService = locatorService;
        this.module = module;
        this.pdcBiomeKey = new NamespacedKey(core, "gui_biome_key");
        this.pdcActionKey = new NamespacedKey(core, "gui_action_key");
    }

    public static Registry<Biome> getBiomeRegistry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
    }

    /**
     * Opens the biome selection GUI for the player at page 0 for main hand.
     */
    public void open(Player player) {
        openPage(player, 0, EquipmentSlot.HAND, player.getInventory().getHeldItemSlot());
    }

    /**
     * Opens the biome selection GUI for the player at page 0 for the specified hand and slot.
     */
    public void open(Player player, EquipmentSlot hand, int inventorySlot) {
        openPage(player, 0, hand, inventorySlot);
    }

    /**
     * Opens the biome selection GUI for the player at the given page for main hand.
     */
    public void openPage(Player player, int page) {
        openPage(player, page, EquipmentSlot.HAND, player.getInventory().getHeldItemSlot());
    }

    /**
     * Opens the biome selection GUI for the player at the given page for the specified hand and slot.
     */
    public void openPage(Player player, int page, EquipmentSlot hand, int inventorySlot) {
        World.Environment environment = player.getWorld().getEnvironment();
        List<Biome> biomes = getBiomesForEnvironment(environment);

        int totalPages = Math.max(1, (int) Math.ceil((double) biomes.size() / PAGE_SIZE));
        int clampedPage = Math.max(0, Math.min(page, totalPages - 1));

        BiomeCompassHolder holder = new BiomeCompassHolder(player.getUniqueId(), environment, clampedPage, hand, inventorySlot);
        holder.setTotalPages(totalPages);

        Component title = Component.text("Biome Selector (Page " + (clampedPage + 1) + "/" + totalPages + ")",
                NamedTextColor.DARK_GRAY);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, title);
        holder.setInventory(inventory);

        int fromIndex = clampedPage * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, biomes.size());
        List<Biome> pageBiomes = biomes.subList(fromIndex, toIndex);

        String envName = formatEnvironmentName(environment);

        for (int i = 0; i < pageBiomes.size(); i++) {
            Biome biome = pageBiomes.get(i);
            inventory.setItem(contentIndexToSlot(i), createBiomeIcon(biome, envName));
        }

        renderNavigationControls(inventory, clampedPage, totalPages, biomes.size());
        player.openInventory(inventory);
    }

    /**
     * Queries RegistryAccess dynamically and filters by dimension.
     */
    public List<Biome> getBiomesForEnvironment(World.Environment environment) {
        Registry<Biome> biomeRegistry = getBiomeRegistry();
        List<Biome> result = new ArrayList<>();
        for (Biome biome : biomeRegistry) {
            if (isBiomeInEnvironment(biome, environment, biomeRegistry)) {
                result.add(biome);
            }
        }
        result.sort(Comparator.comparing(b -> BiomeLocatorService.formatBiomeName(b.getKey())));
        return result;
    }

    /**
     * Determines whether a biome belongs to the specified dimension environment.
     */
    public static boolean isBiomeInEnvironment(Biome biome, World.Environment env) {
        Registry<Biome> registry = null;
        try {
            registry = getBiomeRegistry();
        } catch (Throwable ignored) {
        }
        return isBiomeInEnvironment(biome, env, registry);
    }

    /**
     * Determines whether a biome belongs to the specified dimension environment using the provided registry.
     */
    public static boolean isBiomeInEnvironment(Biome biome, World.Environment env, Registry<Biome> biomeRegistry) {
        if (biome == null || biome.getKey() == null) {
            return false;
        }
        NamespacedKey key = biome.getKey();
        String path = key.getKey().toLowerCase();

        // Check Paper registry tags if registry is available
        if (biomeRegistry != null) {
            try {
                if (env == World.Environment.NETHER) {
                    Tag<Biome> tag = biomeRegistry.getTag(TagKey.create(RegistryKey.BIOME, Key.key("minecraft:is_nether")));
                    if (tag != null && tag.contains(TypedKey.create(RegistryKey.BIOME, key))) {
                        return true;
                    }
                } else if (env == World.Environment.THE_END) {
                    Tag<Biome> tag = biomeRegistry.getTag(TagKey.create(RegistryKey.BIOME, Key.key("minecraft:is_end")));
                    if (tag != null && tag.contains(TypedKey.create(RegistryKey.BIOME, key))) {
                        return true;
                    }
                } else if (env == World.Environment.NORMAL) {
                    Tag<Biome> tag = biomeRegistry.getTag(TagKey.create(RegistryKey.BIOME, Key.key("minecraft:is_overworld")));
                    if (tag != null && tag.contains(TypedKey.create(RegistryKey.BIOME, key))) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
                // Fall through to fallback sets if tags are not loaded/supported in environment
            }
        }

        boolean isNether = NETHER_FALLBACK.contains(path) || path.contains("nether");
        boolean isEnd = END_FALLBACK.contains(path) || path.contains("end");

        if (env == World.Environment.NETHER) {
            return isNether;
        }
        if (env == World.Environment.THE_END) {
            return isEnd;
        }
        if (env == World.Environment.NORMAL) {
            return !isNether && !isEnd && !path.equals("custom");
        }
        return true;
    }

    private ItemStack createBiomeIcon(Biome biome, String envName) {
        NamespacedKey key = biome.getKey();
        Material material = resolveBiomeMaterial(key, envName);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String friendlyName = BiomeLocatorService.formatBiomeName(key);
        meta.displayName(Component.text(friendlyName, NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Environment: ", NamedTextColor.GRAY)
                .append(Component.text(envName, NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("ID: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(key.toString(), NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("▶ Click to calibrate compass", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        // Identifying key stored in PDC rather than parsing display name
        meta.getPersistentDataContainer().set(pdcBiomeKey, PersistentDataType.STRING, key.toString());
        item.setItemMeta(meta);
        return item;
    }

    private void renderNavigationControls(Inventory inventory, int page, int totalPages, int totalBiomes) {
        ItemStack filler = createFillerItem();

        // Full border: top row, bottom row, and side columns on the middle rows
        for (int col = 0; col < GRID_COLS; col++) {
            inventory.setItem(col, filler);                              // row 0
            inventory.setItem((GRID_ROWS - 1) * GRID_COLS + col, filler); // row 5
        }
        for (int row = 1; row < GRID_ROWS - 1; row++) {
            inventory.setItem(row * GRID_COLS, filler);                  // col 0
            inventory.setItem(row * GRID_COLS + (GRID_COLS - 1), filler);// col 8
        }

        // Previous Page
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta meta = prev.getItemMeta();
            meta.displayName(Component.text("« Previous Page", NamedTextColor.YELLOW, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Go to page " + page, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(pdcActionKey, PersistentDataType.STRING, ACTION_PREV_PAGE);
            prev.setItemMeta(meta);
            inventory.setItem(NAV_PREV_SLOT, prev);
        } else {
            inventory.setItem(NAV_PREV_SLOT, filler);
        }

        // Center Info
        ItemStack info = new ItemStack(Material.COMPASS);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("Page " + (page + 1) + " of " + totalPages,
                NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(List.of(
                Component.text("Total Biomes: " + totalBiomes, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        infoMeta.getPersistentDataContainer().set(pdcActionKey, PersistentDataType.STRING, ACTION_INFO);
        infoMeta.setItemModel(NamespacedKey.fromString("vyrim:utility/naturescompass"));
        info.setItemMeta(infoMeta);
        inventory.setItem(NAV_INFO_SLOT, info);

        // Next Page
        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta meta = next.getItemMeta();
            meta.displayName(Component.text("Next Page »", NamedTextColor.YELLOW, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Go to page " + (page + 2), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(pdcActionKey, PersistentDataType.STRING, ACTION_NEXT_PAGE);
            next.setItemMeta(meta);
            inventory.setItem(NAV_NEXT_SLOT, next);
        } else {
            inventory.setItem(NAV_NEXT_SLOT, filler);
        }
    }

    /** Maps a content index (0..PAGE_SIZE-1) to its actual inventory slot inside the bordered grid. */
    private int contentIndexToSlot(int index) {
        int row = index / CONTENT_COLS;      // 0..3
        int col = index % CONTENT_COLS;      // 0..6
        int actualRow = row + 1;             // skip border row 0
        int actualCol = col + 1;             // skip border col 0
        return actualRow * GRID_COLS + actualCol;
    }

    private ItemStack createFillerItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    public static Material resolveBiomeMaterial(NamespacedKey key, String envName) {
        String path = key != null ? key.getKey().toLowerCase() : "";
        if (path.contains("cherry")) return Material.CHERRY_SAPLING;
        if (path.contains("pale_garden")) return Material.PALE_OAK_SAPLING;
        if (path.contains("dark_forest")) return Material.DARK_OAK_SAPLING;
        if (path.contains("birch")) return Material.BIRCH_SAPLING;
        if (path.contains("bamboo")) return Material.BAMBOO;
        if (path.contains("jungle")) return Material.JUNGLE_SAPLING;
        if (path.contains("taiga")) return Material.SPRUCE_SAPLING;
        if (path.contains("snow") || path.contains("ice") || path.contains("frozen") || path.contains("grove")) return Material.SNOW_BLOCK;
        if (path.contains("badlands") || path.contains("eroded")) return Material.TERRACOTTA;
        if (path.contains("desert")) return Material.SAND;
        if (path.contains("mangrove")) return Material.MANGROVE_PROPAGULE;
        if (path.contains("swamp")) return Material.LILY_PAD;
        if (path.contains("mushroom")) return Material.RED_MUSHROOM_BLOCK;
        if (path.contains("dripstone")) return Material.DRIPSTONE_BLOCK;
        if (path.contains("lush")) return Material.MOSS_BLOCK;
        if (path.contains("deep_dark")) return Material.SCULK;
        if (path.contains("ocean") || path.contains("river") || path.contains("beach")) return Material.WATER_BUCKET;
        if (path.contains("meadow") || path.contains("flower")) return Material.CORNFLOWER;
        if (path.contains("sunflower")) return Material.SUNFLOWER;
        if (path.contains("savanna")) return Material.ACACIA_SAPLING;
        if (path.contains("crimson")) return Material.CRIMSON_NYLIUM;
        if (path.contains("warped")) return Material.WARPED_NYLIUM;
        if (path.contains("soul")) return Material.SOUL_SAND;
        if (path.contains("basalt")) return Material.BASALT;
        if (path.contains("nether")) return Material.NETHERRACK;
        if (path.contains("end_highlands") || path.contains("end_midlands")) return Material.CHORUS_FLOWER;
        if (path.contains("end")) return Material.END_STONE;
        if (path.contains("void")) return Material.OBSIDIAN;

        if ("Nether".equalsIgnoreCase(envName)) return Material.NETHERRACK;
        if ("The End".equalsIgnoreCase(envName)) return Material.END_STONE;
        return Material.GRASS_BLOCK;
    }

    public static String formatEnvironmentName(World.Environment environment) {
        if (environment == null) return "Unknown";
        return switch (environment) {
            case NETHER -> "Nether";
            case THE_END -> "The End";
            case CUSTOM -> "Custom";
            default -> "Overworld";
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BiomeCompassHolder holder)) {
            return;
        }

        // Strictly prevent item theft and slot manipulation
        event.setCancelled(true);

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta()) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();

        // Check navigation actions
        String action = pdc.get(pdcActionKey, PersistentDataType.STRING);
        if (ACTION_PREV_PAGE.equals(action)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            openPage(player, holder.getCurrentPage() - 1, holder.getHand(), holder.getInventorySlot());
            return;
        }
        if (ACTION_NEXT_PAGE.equals(action)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            openPage(player, holder.getCurrentPage() + 1, holder.getHand(), holder.getInventorySlot());
            return;
        }
        if (ACTION_INFO.equals(action)) {
            return;
        }

        // Check biome selection
        String biomeKeyStr = pdc.get(pdcBiomeKey, PersistentDataType.STRING);
        if (biomeKeyStr != null) {
            NamespacedKey biomeKey = NamespacedKey.fromString(biomeKeyStr);
            if (biomeKey == null) {
                return;
            }

            Biome biome = getBiomeRegistry().get(biomeKey);
            if (biome == null) {
                player.sendMessage(Component.text("❌ Selected biome is no longer valid.", NamedTextColor.RED));
                player.closeInventory();
                return;
            }

            // 1. Check if the player has vyrimcore.bypass.biomecompass via LuckPermsHook
            boolean bypass = core != null && core.getLuckPermsHook() != null
                    ? core.getLuckPermsHook().hasPermission(player, BiomeCompassModule.PERMISSION_BYPASS)
                    : player.hasPermission(BiomeCompassModule.PERMISSION_BYPASS);

            // 2. If bypassed: proceed immediately to async scan. If not: check cooldown
            if (!bypass && module != null) {
                // 3. Calculate remaining seconds (lastTime + cooldownMillis - now) / 1000
                long remaining = module.getRemainingCooldownSeconds(player.getUniqueId());
                // 4. If remaining > 0: cancel search, send formatted cooldown message, close inventory
                if (remaining > 0) {
                    player.sendMessage(module.formatCooldownMessage(remaining));
                    if (locatorService != null && locatorService.isPlaySounds()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                    }
                    player.closeInventory();
                    return;
                }
                // 5. If remaining <= 0: update cooldown timestamp to current time
                module.updateSearchTimestamp(player.getUniqueId());
            }

            player.closeInventory();
            locatorService.locateBiome(player, biome, biomeKey, holder.getHand(), holder.getInventorySlot());
        }
    }
}

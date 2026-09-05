package net.vyrim.core.module.advancements.listener;

import net.vyrim.core.module.advancements.AdvancementTriggerService;
import net.vyrim.core.module.advancements.TriggerType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Listener tracking player movement to detect biome discovery.
 * <p>
 * Highly optimized: ignores intra-block movements, caches discovered biomes per player,
 * queries vanilla adventuring_time criteria when available, and clears memory on quit.
 */
public class AdvancementBiomeListener implements Listener {

    private static final NamespacedKey ADVENTURING_TIME_KEY = NamespacedKey.minecraft("adventure/adventuring_time");

    private final AdvancementTriggerService triggerService;
    private final Function<Block, String> biomeResolver;
    private final Map<UUID, Set<String>> visitedBiomes = new ConcurrentHashMap<>();

    public AdvancementBiomeListener(AdvancementTriggerService triggerService) {
        this(triggerService, block -> {
            try {
                Biome b = block.getBiome();
                return b != null ? b.getKey().getKey().toUpperCase(Locale.ROOT) : null;
            } catch (Throwable t) {
                return null;
            }
        });
    }

    public AdvancementBiomeListener(AdvancementTriggerService triggerService, Function<Block, String> biomeResolver) {
        this.triggerService = Objects.requireNonNull(triggerService, "triggerService cannot be null");
        this.biomeResolver = Objects.requireNonNull(biomeResolver, "biomeResolver cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        // 1. Performance filter: return immediately if block coordinates did not change
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        String currentBiome = biomeResolver.apply(to.getBlock());
        if (currentBiome == null || currentBiome.isBlank()) {
            return;
        }

        Set<String> discovered = visitedBiomes.computeIfAbsent(player.getUniqueId(), k -> initDiscoveredBiomes(player));

        // 2. Only fire trigger if this biome has not been visited yet by the player
        if (discovered.add(currentBiome)) {
            triggerService.handle(player, TriggerType.DISCOVER_BIOME, currentBiome, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        visitedBiomes.remove(event.getPlayer().getUniqueId());
    }

    private Set<String> initDiscoveredBiomes(Player player) {
        Set<String> set = ConcurrentHashMap.newKeySet();

        // Check if vanilla Paper/Bukkit exposes already discovered biomes through vanilla adventuring_time advancement
        try {
            Advancement adv = Bukkit.getAdvancement(ADVENTURING_TIME_KEY);
            if (adv != null) {
                AdvancementProgress progress = player.getAdvancementProgress(adv);
                for (String criteria : progress.getAwardedCriteria()) {
                    String keyStr = criteria.contains(":") ? criteria.substring(criteria.indexOf(':') + 1) : criteria;
                    set.add(keyStr.toUpperCase(Locale.ROOT));
                }
            }
        } catch (Throwable ignored) {
        }

        return set;
    }

    /**
     * Package-private for testing.
     */
    Map<UUID, Set<String>> getVisitedBiomes() {
        return visitedBiomes;
    }
}

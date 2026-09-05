package net.vyrim.core.module.advancements;

import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import net.vyrim.core.VyrimCore;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Central service that all Bukkit listeners funnel through.
 * <p>
 * Maintains a reverse index of (TriggerType, target) -> List of advancement IDs,
 * queries UltimateAdvancementAPI progression state (one-time only enforcement),
 * manages cumulative multi-step counters via AdvancementProgressStore, and dispatches rewards.
 */
public class AdvancementTriggerService {

    public record TriggerKey(TriggerType type, String target) {
        public TriggerKey {
            Objects.requireNonNull(type, "type cannot be null");
        }
    }

    private final VyrimCore core;
    private final Supplier<AdvancementLoader> loaderSupplier;
    private final AdvancementRewardService rewardService;
    private final AdvancementProgressStore progressStore;

    private final Map<TriggerKey, List<String>> reverseIndex = new ConcurrentHashMap<>();
    private final Set<String> statisticAdvancementIds = ConcurrentHashMap.newKeySet();

    public AdvancementTriggerService(
            VyrimCore core,
            Supplier<AdvancementLoader> loaderSupplier,
            AdvancementRewardService rewardService,
            AdvancementProgressStore progressStore
    ) {
        this.core = core;
        this.loaderSupplier = Objects.requireNonNull(loaderSupplier, "loaderSupplier cannot be null");
        this.rewardService = Objects.requireNonNull(rewardService, "rewardService cannot be null");
        this.progressStore = Objects.requireNonNull(progressStore, "progressStore cannot be null");
    }

    /**
     * Builds or rebuilds the reverse index from currently loaded advancement trigger definitions.
     */
    public void buildIndex() {
        reverseIndex.clear();
        statisticAdvancementIds.clear();

        AdvancementLoader loader = loaderSupplier.get();
        if (loader == null) {
            return;
        }

        Map<String, AdvancementTriggerData> triggerMap = loader.getTriggerDataMap();
        for (Map.Entry<String, AdvancementTriggerData> entry : triggerMap.entrySet()) {
            String advId = entry.getKey();
            AdvancementTriggerData trigger = entry.getValue();
            if (trigger == null) {
                continue;
            }

            TriggerType type = TriggerType.fromString(trigger.type());
            if (type == TriggerType.UNKNOWN) {
                log(Level.WARNING, "[Advancements] Advancement '" + advId + "' has unknown trigger type: " + trigger.type());
                continue;
            }

            String target = normalizeTarget(trigger.target());
            TriggerKey key = new TriggerKey(type, target);

            reverseIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(advId);

            if (type == TriggerType.STATISTIC) {
                statisticAdvancementIds.add(advId);
            }
        }

        log(Level.INFO, "[Advancements] Built trigger index with " + triggerMap.size() + " triggers ("
                + statisticAdvancementIds.size() + " statistic-based).");
    }

    /**
     * Main entry point called by Bukkit event listeners when an event fires.
     *
     * @param player the player who performed the action
     * @param type   the trigger type
     * @param target the target qualifier (e.g. material, entity type), or null for wildcard/no-target
     * @param amount the event's contribution amount (e.g. 1 for block break, stack size for pickup)
     */
    public void handle(Player player, TriggerType type, String target, int amount) {
        if (player == null || type == null || amount <= 0) {
            return;
        }

        AdvancementLoader loader = loaderSupplier.get();
        if (loader == null) {
            return;
        }

        String normTarget = normalizeTarget(target);

        // Find candidate advancements: exact target match + wildcard (null target) match
        Set<String> candidateIds = new LinkedHashSet<>();
        List<String> exactMatches = reverseIndex.get(new TriggerKey(type, normTarget));
        if (exactMatches != null) {
            candidateIds.addAll(exactMatches);
        }

        if (normTarget != null) {
            List<String> wildcardMatches = reverseIndex.get(new TriggerKey(type, null));
            if (wildcardMatches != null) {
                candidateIds.addAll(wildcardMatches);
            }
        }

        if (candidateIds.isEmpty()) {
            return;
        }

        for (String advId : candidateIds) {
            processAdvancement(player, advId, amount, loader);
        }
    }

    private void processAdvancement(Player player, String advId, int amount, AdvancementLoader loader) {
        Advancement adv = loader.getAdvancement(advId);
        if (adv == null) {
            return;
        }

        // 1. One-time-only check via UltimateAdvancementAPI: skip immediately if already unlocked
        try {
            if (adv.isGranted(player)) {
                return;
            }
        } catch (Throwable t) {
            // Player data may not be loaded yet in UltimateAdvancementAPI
            return;
        }

        AdvancementTriggerData trigger = loader.getTriggerDataMap().get(advId);
        if (trigger == null) {
            return;
        }

        int targetAmount = trigger.amount();

        // 2. Instant unlock when threshold is 1 (or <= 0)
        if (targetAmount <= 1) {
            unlockAdvancement(player, adv, advId);
            return;
        }

        // 3. Multi-step cumulative tracking when threshold > 1
        int currentProgress = progressStore.incrementAndGet(player.getUniqueId(), advId, amount);

        if (currentProgress >= targetAmount) {
            unlockAdvancement(player, adv, advId);
            progressStore.reset(player.getUniqueId(), advId);
        } else {
            // Update visual progression bar in advancement tab
            try {
                adv.setProgression(player, currentProgress, false);
            } catch (Throwable ignored) {
            }
        }
    }

    private void unlockAdvancement(Player player, Advancement adv, String advId) {
        try {
            adv.grant(player);
        } catch (Throwable t) {
            log(Level.WARNING, "[Advancements] Failed to grant advancement '" + advId + "' to "
                    + player.getName() + ": " + t.getMessage(), t);
        }

        // Dispatch console command and/or grant permission
        rewardService.grantRewards(player, advId);
    }

    /**
     * Evaluates all STATISTIC-type triggers for the given player.
     * Documented Trade-off: Statistic-based triggers are evaluated during natural event
     * interactions, at quit checkpoints, and via a slow periodic checkpoint task (every few minutes),
     * rather than polling every tick or on PlayerStatisticIncrementEvent. This ensures eventually-consistent
     * advancement unlocks with zero tick-rate performance penalty.
     *
     * @param player the player to check statistics for
     */
    public void checkStatistics(Player player) {
        if (player == null || statisticAdvancementIds.isEmpty()) {
            return;
        }

        AdvancementLoader loader = loaderSupplier.get();
        if (loader == null) {
            return;
        }

        for (String advId : statisticAdvancementIds) {
            Advancement adv = loader.getAdvancement(advId);
            if (adv == null) {
                continue;
            }

            try {
                if (adv.isGranted(player)) {
                    continue;
                }
            } catch (Throwable t) {
                continue;
            }

            AdvancementTriggerData trigger = loader.getTriggerDataMap().get(advId);
            if (trigger == null) {
                continue;
            }

            int statValue = resolveStatisticValue(player, trigger.target());
            int threshold = trigger.amount();

            if (statValue >= threshold) {
                unlockAdvancement(player, adv, advId);
                progressStore.reset(player.getUniqueId(), advId);
            } else if (threshold > 1 && statValue > 0) {
                try {
                    adv.setProgression(player, statValue, false);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * Resolves the current numeric value of a Minecraft statistic for the player.
     * Supports untyped statistics (e.g. "JUMP"), block/item statistics (e.g. "MINE_BLOCK:STONE"),
     * and entity statistics (e.g. "KILL_ENTITY:ZOMBIE").
     */
    public int resolveStatisticValue(Player player, String target) {
        if (player == null || target == null || target.isBlank()) {
            return 0;
        }

        String raw = target.trim().toUpperCase(Locale.ROOT);
        String statName;
        String qualifier = null;

        int colonIdx = raw.indexOf(':');
        if (colonIdx != -1) {
            statName = raw.substring(0, colonIdx).trim();
            qualifier = raw.substring(colonIdx + 1).trim();
        } else {
            statName = raw;
        }

        Statistic stat;
        try {
            stat = Statistic.valueOf(statName);
        } catch (IllegalArgumentException e) {
            return 0;
        }

        try {
            if (qualifier != null && !qualifier.isBlank()) {
                if (stat.getType() == Statistic.Type.BLOCK || stat.getType() == Statistic.Type.ITEM) {
                    Material mat = Material.matchMaterial(qualifier);
                    if (mat != null) {
                        return player.getStatistic(stat, mat);
                    }
                } else if (stat.getType() == Statistic.Type.ENTITY) {
                    EntityType entityType = EntityType.valueOf(qualifier);
                    return player.getStatistic(stat, entityType);
                }
            }
            return player.getStatistic(stat);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * Package-private getter for testing reverse index mappings.
     */
    Map<TriggerKey, List<String>> getReverseIndex() {
        return Collections.unmodifiableMap(reverseIndex);
    }

    private String normalizeTarget(String target) {
        if (target == null || target.isBlank() || target.equalsIgnoreCase("none") || target.equals("*")) {
            return null;
        }
        return target.trim().toUpperCase(Locale.ROOT);
    }

    private void log(Level level, String msg) {
        log(level, msg, null);
    }

    private void log(Level level, String msg, Throwable t) {
        if (core != null && core.getLogger() != null) {
            if (t != null) {
                core.getLogger().log(level, msg, t);
            } else {
                core.getLogger().log(level, msg);
            }
        }
    }
}

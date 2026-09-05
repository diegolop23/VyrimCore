package net.vyrim.core.module.advancements.listener;

import net.vyrim.core.VyrimCore;
import net.vyrim.core.module.advancements.AdvancementTriggerService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

/**
 * Listener and scheduler managing STATISTIC triggers at checkpoint intervals.
 * <p>
 * Architectural Trade-off:
 * Instead of listening to high-frequency events every tick (such as PlayerStatisticIncrementEvent,
 * which fires repeatedly for distance walked, sprinted, or sneak time), statistic-based triggers
 * are evaluated at server checkpoints:
 *   1. When a player disconnects (PlayerQuitEvent),
 *   2. When natural game events occur (block breaking, entity killing, crafting),
 *   3. On a slow periodic checkpoint task (every 3 minutes across online players).
 * This makes statistic triggers eventually consistent rather than instantaneous per tick,
 * completely eliminating any server tick-rate / TPS overhead.
 */
public class AdvancementStatisticListener implements Listener {

    private final VyrimCore core;
    private final AdvancementTriggerService triggerService;
    private BukkitTask checkpointTask;

    public AdvancementStatisticListener(VyrimCore core, AdvancementTriggerService triggerService) {
        this.core = core;
        this.triggerService = Objects.requireNonNull(triggerService, "triggerService cannot be null");
    }

    /**
     * Starts the periodic checkpoint task.
     *
     * @param intervalTicks ticks between checkpoint evaluations (e.g. 3600L = 3 minutes)
     */
    public void startPeriodicTask(long intervalTicks) {
        stop();
        if (core != null && Bukkit.getServer() != null && Bukkit.getScheduler() != null) {
            this.checkpointTask = Bukkit.getScheduler().runTaskTimer(core, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.isOnline()) {
                        triggerService.checkStatistics(player);
                    }
                }
            }, intervalTicks, intervalTicks);
        }
    }

    /**
     * Cancels the periodic checkpoint task.
     */
    public void stop() {
        if (checkpointTask != null) {
            try {
                checkpointTask.cancel();
            } catch (Throwable ignored) {
            }
            checkpointTask = null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            triggerService.checkStatistics(player);
        }
    }
}

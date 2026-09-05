package net.vyrim.core.module.advancements.listener;

import com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI;
import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.events.PlayerLoadingCompletedEvent;
import net.vyrim.core.VyrimCore;
import net.vyrim.core.module.advancements.AdvancementLoader;
import net.vyrim.core.module.advancements.AdvancementProgressStore;
import net.vyrim.core.module.advancements.AdvancementTriggerService;
import net.vyrim.core.module.advancements.TriggerType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Listener handling player joins, firing JOIN_SERVER triggers,
 * and restoring active in-progress counters to client advancement views.
 */
public class AdvancementJoinListener implements Listener {

    private final VyrimCore core;
    private final AdvancementTriggerService triggerService;
    private final AdvancementProgressStore progressStore;
    private final Supplier<AdvancementLoader> loaderSupplier;

    public AdvancementJoinListener(
            VyrimCore core,
            AdvancementTriggerService triggerService,
            AdvancementProgressStore progressStore,
            Supplier<AdvancementLoader> loaderSupplier
    ) {
        this.core = core;
        this.triggerService = Objects.requireNonNull(triggerService, "triggerService cannot be null");
        this.progressStore = Objects.requireNonNull(progressStore, "progressStore cannot be null");
        this.loaderSupplier = Objects.requireNonNull(loaderSupplier, "loaderSupplier cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        processJoin(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLoadingCompleted(PlayerLoadingCompletedEvent event) {
        Player player = event.getPlayer();
        processJoin(player);
    }

    private void processJoin(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UltimateAdvancementAPI api = getApi();
        if (api != null && !api.isLoaded(player)) {
            // Player advancement data not yet loaded by UltimateAdvancementAPI;
            // PlayerLoadingCompletedEvent will fire once ready.
            return;
        }

        // 1. Fire JOIN_SERVER trigger
        triggerService.handle(player, TriggerType.JOIN_SERVER, null, 1);

        // 2. Restore in-progress visual progression for active multi-step advancements
        restoreProgress(player);

        // 3. Evaluate any active statistics at join checkpoint
        triggerService.checkStatistics(player);
    }

    private void restoreProgress(Player player) {
        AdvancementLoader loader = loaderSupplier.get();
        if (loader == null) {
            return;
        }

        for (Advancement adv : loader.getAdvancementMap().values()) {
            try {
                if (!adv.isGranted(player)) {
                    String advId = adv.getKey().getKey();
                    int progress = progressStore.getProgress(player.getUniqueId(), advId);
                    if (progress > 0) {
                        adv.setProgression(player, progress, false);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private UltimateAdvancementAPI getApi() {
        if (core == null) {
            return null;
        }
        try {
            return UltimateAdvancementAPI.getInstance(core);
        } catch (Throwable t) {
            return null;
        }
    }
}

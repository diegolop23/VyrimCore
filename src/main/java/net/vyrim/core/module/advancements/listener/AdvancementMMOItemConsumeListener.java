package net.vyrim.core.module.advancements.listener;

import net.Indyuce.mmoitems.api.event.item.ConsumableConsumedEvent;
import net.vyrim.core.hook.MMOItemsHook;
import net.vyrim.core.module.advancements.AdvancementTriggerService;
import net.vyrim.core.module.advancements.TriggerType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener handling item consumption for MMOITEM_CONSUME triggers.
 * <p>
 * Supports consumption via:
 * 1. MMOItems custom consumables (ConsumableConsumedEvent) for both right-click consumable
 *    items and vanilla-eaten consumables that MMOItems processes.
 * 2. Vanilla item consumption fallback (PlayerItemConsumeEvent) for any MMOItems food
 *    that may not fire ConsumableConsumedEvent.
 * <p>
 * Implements tick-level de-duplication to ensure a single consumption action never
 * double-triggers MMOITEM_CONSUME.
 */
public class AdvancementMMOItemConsumeListener implements Listener {

    private final MMOItemsHook mmoItemsHook;
    private final AdvancementTriggerService triggerService;
    private final Map<UUID, Long> lastConsumedTick = new ConcurrentHashMap<>();

    public AdvancementMMOItemConsumeListener(MMOItemsHook mmoItemsHook, AdvancementTriggerService triggerService) {
        this.mmoItemsHook = Objects.requireNonNull(mmoItemsHook, "mmoItemsHook cannot be null");
        this.triggerService = Objects.requireNonNull(triggerService, "triggerService cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMMOItemConsumed(ConsumableConsumedEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        String mmoItemId = null;
        if (event.getMMOItem() != null && event.getMMOItem().getType() != null && event.getMMOItem().getId() != null) {
            mmoItemId = event.getMMOItem().getType().getId().toUpperCase(Locale.ROOT)
                    + ":" + event.getMMOItem().getId().toUpperCase(Locale.ROOT);
        } else if (event.getUseItem() != null && event.getUseItem().getItem() != null) {
            mmoItemId = mmoItemsHook.resolveMMOItemId(event.getUseItem().getItem());
        }

        if (mmoItemId == null) {
            return;
        }

        lastConsumedTick.put(player.getUniqueId(), getCurrentTick());
        triggerService.handle(player, TriggerType.MMOITEM_CONSUME, mmoItemId, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (player == null || event.getItem() == null) {
            return;
        }

        Long lastTick = lastConsumedTick.get(player.getUniqueId());
        long currentTick = getCurrentTick();
        if (lastTick != null && lastTick == currentTick) {
            // Already handled via ConsumableConsumedEvent in this tick; skip fallback
            return;
        }

        String mmoItemId = mmoItemsHook.resolveMMOItemId(event.getItem());
        if (mmoItemId == null) {
            return;
        }

        lastConsumedTick.put(player.getUniqueId(), currentTick);
        triggerService.handle(player, TriggerType.MMOITEM_CONSUME, mmoItemId, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastConsumedTick.remove(event.getPlayer().getUniqueId());
    }

    private long getCurrentTick() {
        try {
            return Bukkit.getCurrentTick();
        } catch (Throwable t) {
            return System.currentTimeMillis();
        }
    }
}

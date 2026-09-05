package net.vyrim.core.module.advancements.listener;

import net.vyrim.core.module.advancements.AdvancementTriggerService;
import net.vyrim.core.module.advancements.TriggerType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;

import java.util.Objects;

/**
 * Listener handling item consumption for CONSUME_ITEM triggers.
 */
public class AdvancementItemConsumeListener implements Listener {

    private final AdvancementTriggerService triggerService;
    private final net.vyrim.core.hook.MMOItemsHook mmoItemsHook;

    public AdvancementItemConsumeListener(AdvancementTriggerService triggerService) {
        this(triggerService, null);
    }

    public AdvancementItemConsumeListener(AdvancementTriggerService triggerService, net.vyrim.core.hook.MMOItemsHook mmoItemsHook) {
        this.triggerService = Objects.requireNonNull(triggerService, "triggerService cannot be null");
        this.mmoItemsHook = mmoItemsHook;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (player == null || event.getItem() == null) {
            return;
        }

        // Step 4 filter: Exclude custom MMOItems from native material CONSUME_ITEM triggers.
        // This ensures players must consume genuine vanilla items to satisfy vanilla material advancements,
        // preventing accidental dual-progression when consuming MMOItems that use a vanilla material base.
        if (mmoItemsHook != null && mmoItemsHook.isMMOItem(event.getItem())) {
            return;
        }

        Material material = event.getItem().getType();
        triggerService.handle(player, TriggerType.CONSUME_ITEM, material.name(), 1);
    }
}

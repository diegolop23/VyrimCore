package net.vyrim.core.module.advancements.listener;

import net.vyrim.core.module.advancements.AdvancementTriggerService;
import net.vyrim.core.module.advancements.TriggerType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Objects;

/**
 * Listener handling block breaks for BREAK_BLOCK triggers.
 */
public class AdvancementBlockBreakListener implements Listener {

    private final AdvancementTriggerService triggerService;

    public AdvancementBlockBreakListener(AdvancementTriggerService triggerService) {
        this.triggerService = Objects.requireNonNull(triggerService, "triggerService cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        Material material = event.getBlock().getType();
        triggerService.handle(player, TriggerType.BREAK_BLOCK, material.name(), 1);
    }
}

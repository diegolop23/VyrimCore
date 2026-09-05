package net.vyrim.core.module.advancements.listener;

import net.vyrim.core.module.advancements.AdvancementTriggerService;
import net.vyrim.core.module.advancements.TriggerType;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Objects;

/**
 * Listener handling entity kills by players for KILL_ENTITY triggers.
 */
public class AdvancementEntityDeathListener implements Listener {

    private final AdvancementTriggerService triggerService;

    public AdvancementEntityDeathListener(AdvancementTriggerService triggerService) {
        this.triggerService = Objects.requireNonNull(triggerService, "triggerService cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        // Only trigger when killed by a player (ignore environmental deaths or mob kills)
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        EntityType entityType = event.getEntityType();
        triggerService.handle(killer, TriggerType.KILL_ENTITY, entityType.name(), 1);
    }
}

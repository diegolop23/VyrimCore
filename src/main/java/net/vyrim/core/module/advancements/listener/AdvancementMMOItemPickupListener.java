package net.vyrim.core.module.advancements.listener;

import net.vyrim.core.hook.MMOItemsHook;
import net.vyrim.core.module.advancements.AdvancementTriggerService;
import net.vyrim.core.module.advancements.TriggerType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Listener handling item pickups for MMOITEM_PICKUP triggers,
 * contributing the full picked-up MMOItem stack size as the trigger delta.
 */
public class AdvancementMMOItemPickupListener implements Listener {

    private final MMOItemsHook mmoItemsHook;
    private final AdvancementTriggerService triggerService;

    public AdvancementMMOItemPickupListener(MMOItemsHook mmoItemsHook, AdvancementTriggerService triggerService) {
        this.mmoItemsHook = Objects.requireNonNull(mmoItemsHook, "mmoItemsHook cannot be null");
        this.triggerService = Objects.requireNonNull(triggerService, "triggerService cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        ItemStack item = event.getItem().getItemStack();
        if (item == null || isAir(item.getType())) {
            return;
        }

        String mmoItemId = mmoItemsHook.resolveMMOItemId(item);
        if (mmoItemId == null) {
            return;
        }

        int amount = Math.max(1, item.getAmount());
        triggerService.handle(player, TriggerType.MMOITEM_PICKUP, mmoItemId, amount);
    }

    private static boolean isAir(org.bukkit.Material material) {
        return material == null || material == org.bukkit.Material.AIR || material == org.bukkit.Material.CAVE_AIR || material == org.bukkit.Material.VOID_AIR;
    }
}

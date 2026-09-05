package net.vyrim.core.module.advancements.listener;

import net.vyrim.core.module.advancements.AdvancementTriggerService;
import net.vyrim.core.module.advancements.TriggerType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Listener handling item pickups for PICKUP_ITEM triggers,
 * contributing the full picked-up stack size as the trigger delta.
 */
public class AdvancementItemPickupListener implements Listener {

    private final AdvancementTriggerService triggerService;
    private final net.vyrim.core.hook.MMOItemsHook mmoItemsHook;

    public AdvancementItemPickupListener(AdvancementTriggerService triggerService) {
        this(triggerService, null);
    }

    public AdvancementItemPickupListener(AdvancementTriggerService triggerService, net.vyrim.core.hook.MMOItemsHook mmoItemsHook) {
        this.triggerService = Objects.requireNonNull(triggerService, "triggerService cannot be null");
        this.mmoItemsHook = mmoItemsHook;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        ItemStack itemStack = event.getItem().getItemStack();
        if (itemStack == null || isAir(itemStack.getType())) {
            return;
        }

        // Step 4 filter: Exclude custom MMOItems from native material PICKUP_ITEM triggers.
        // This ensures players must collect genuine vanilla items to satisfy vanilla material advancements,
        // preventing accidental dual-progression when picking up MMOItems that use a vanilla material base.
        if (mmoItemsHook != null && mmoItemsHook.isMMOItem(itemStack)) {
            return;
        }

        Material material = itemStack.getType();
        int amount = Math.max(1, itemStack.getAmount());

        triggerService.handle(player, TriggerType.PICKUP_ITEM, material.name(), amount);
    }

    private static boolean isAir(Material material) {
        return material == null || material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }
}

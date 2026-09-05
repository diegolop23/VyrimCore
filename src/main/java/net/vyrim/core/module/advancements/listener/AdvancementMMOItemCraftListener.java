package net.vyrim.core.module.advancements.listener;

import net.Indyuce.mmoitems.api.event.CraftMMOItemEvent;
import net.Indyuce.mmoitems.api.event.PlayerUseCraftingStationEvent;
import net.vyrim.core.hook.MMOItemsHook;
import net.vyrim.core.module.advancements.AdvancementTriggerService;
import net.vyrim.core.module.advancements.TriggerType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Listener handling MMOItems crafting for MMOITEM_CRAFT triggers.
 * <p>
 * Supports crafting via:
 * 1. Standard workbench / crafting grid recipes producing an MMOItem (CraftItemEvent)
 * 2. MMOItems Crafting Stations for instant recipes and finished queue claims (PlayerUseCraftingStationEvent)
 * 3. MMOItems custom recipe events (CraftMMOItemEvent)
 */
public class AdvancementMMOItemCraftListener implements Listener {

    private final MMOItemsHook mmoItemsHook;
    private final AdvancementTriggerService triggerService;

    public AdvancementMMOItemCraftListener(MMOItemsHook mmoItemsHook, AdvancementTriggerService triggerService) {
        this.mmoItemsHook = Objects.requireNonNull(mmoItemsHook, "mmoItemsHook cannot be null");
        this.triggerService = Objects.requireNonNull(triggerService, "triggerService cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack result = event.getRecipe() != null ? event.getRecipe().getResult() : event.getCurrentItem();
        if (result == null || isAir(result.getType())) {
            return;
        }

        String mmoItemId = mmoItemsHook.resolveMMOItemId(result);
        if (mmoItemId == null) {
            return;
        }

        int craftedAmount = AdvancementCraftListener.calculateCraftedAmount(event, result, player);
        triggerService.handle(player, TriggerType.MMOITEM_CRAFT, mmoItemId, craftedAmount);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftingStation(PlayerUseCraftingStationEvent event) {
        Player player = event.getPlayer();
        if (player == null || !event.hasResult() || event.getResult() == null) {
            return;
        }

        PlayerUseCraftingStationEvent.StationAction action = event.getInteraction();
        if (action != PlayerUseCraftingStationEvent.StationAction.INSTANT_RECIPE
                && action != PlayerUseCraftingStationEvent.StationAction.CRAFTING_QUEUE) {
            return;
        }

        ItemStack result = event.getResult();
        String mmoItemId = mmoItemsHook.resolveMMOItemId(result);
        if (mmoItemId == null) {
            return;
        }

        int amount = Math.max(1, result.getAmount());
        triggerService.handle(player, TriggerType.MMOITEM_CRAFT, mmoItemId, amount);
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftMMOItem(CraftMMOItemEvent event) {
        Player player = event.getPlayer();
        if (player == null || event.getResult() == null) {
            return;
        }

        ItemStack result = event.getResult();
        String mmoItemId = mmoItemsHook.resolveMMOItemId(result);
        if (mmoItemId == null) {
            return;
        }

        int amount = Math.max(1, result.getAmount());
        triggerService.handle(player, TriggerType.MMOITEM_CRAFT, mmoItemId, amount);
    }

    private static boolean isAir(org.bukkit.Material material) {
        return material == null || material == org.bukkit.Material.AIR || material == org.bukkit.Material.CAVE_AIR || material == org.bukkit.Material.VOID_AIR;
    }
}

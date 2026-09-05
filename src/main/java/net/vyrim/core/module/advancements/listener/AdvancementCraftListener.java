package net.vyrim.core.module.advancements.listener;

import net.vyrim.core.module.advancements.AdvancementTriggerService;
import net.vyrim.core.module.advancements.TriggerType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Objects;

/**
 * Listener handling item crafting for CRAFT_ITEM triggers.
 * <p>
 * Uses CraftItemEvent (not PrepareItemCraftEvent, which only previews recipes)
 * and accurately computes the crafted stack delta for both normal and shift-clicks.
 */
public class AdvancementCraftListener implements Listener {

    private final AdvancementTriggerService triggerService;
    private final net.vyrim.core.hook.MMOItemsHook mmoItemsHook;

    public AdvancementCraftListener(AdvancementTriggerService triggerService) {
        this(triggerService, null);
    }

    public AdvancementCraftListener(AdvancementTriggerService triggerService, net.vyrim.core.hook.MMOItemsHook mmoItemsHook) {
        this.triggerService = Objects.requireNonNull(triggerService, "triggerService cannot be null");
        this.mmoItemsHook = mmoItemsHook;
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

        // Step 4 filter: Exclude custom MMOItems from native material CRAFT_ITEM triggers.
        // This ensures players must craft genuine vanilla items to satisfy vanilla material advancements,
        // preventing accidental dual-progression when crafting MMOItems that use a vanilla material base.
        if (mmoItemsHook != null && mmoItemsHook.isMMOItem(result)) {
            return;
        }

        Material material = result.getType();
        int craftedAmount = calculateCraftedAmount(event, result, player);

        triggerService.handle(player, TriggerType.CRAFT_ITEM, material.name(), craftedAmount);
    }

    public static int calculateCraftedAmount(CraftItemEvent event, ItemStack recipeResult, Player player) {
        int resultPerCraft = Math.max(1, recipeResult.getAmount());

        if (!event.isShiftClick()) {
            return resultPerCraft;
        }

        // Shift-click: determine how many crafts can be produced from current ingredient matrix
        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        int minIngredient = Integer.MAX_VALUE;

        for (ItemStack item : matrix) {
            if (item != null && !isAir(item.getType())) {
                minIngredient = Math.min(minIngredient, item.getAmount());
            }
        }

        if (minIngredient == Integer.MAX_VALUE || minIngredient <= 0) {
            return resultPerCraft;
        }

        // Determine inventory capacity for the crafted item
        PlayerInventory playerInv = player.getInventory();
        int maxCanHold = 0;
        int maxStackSize = recipeResult.getMaxStackSize();

        // 36 main inventory storage slots (0-35)
        for (int i = 0; i < 36; i++) {
            ItemStack slot = playerInv.getItem(i);
            if (slot == null || isAir(slot.getType())) {
                maxCanHold += maxStackSize;
            } else if (slot.getType() == recipeResult.getType()) {
                maxCanHold += Math.max(0, maxStackSize - slot.getAmount());
            }
        }

        int maxCraftsByInventory = maxCanHold / resultPerCraft;
        int actualCrafts = Math.min(minIngredient, Math.max(1, maxCraftsByInventory));

        return actualCrafts * resultPerCraft;
    }

    private static boolean isAir(Material material) {
        return material == null || material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }
}

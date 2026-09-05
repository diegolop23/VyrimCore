package net.vyrim.core.module.advancements;

import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.event.CraftMMOItemEvent;
import net.Indyuce.mmoitems.api.event.PlayerUseCraftingStationEvent;
import net.Indyuce.mmoitems.api.event.item.ConsumableConsumedEvent;
import net.Indyuce.mmoitems.api.item.mmoitem.VolatileMMOItem;
import net.vyrim.core.VyrimCore;
import net.vyrim.core.hook.MMOItemsHook;
import net.vyrim.core.module.advancements.listener.*;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdvancementMMOItemsListenersTest {

    private VyrimCore mockCore;
    private MMOItemsHook mockMMOItemsHook;
    private AdvancementTriggerService mockTriggerService;
    private Player mockPlayer;
    private UUID playerUuid;

    @BeforeEach
    void setUp() {
        mockCore = mock(VyrimCore.class);
        when(mockCore.getLogger()).thenReturn(Logger.getLogger("AdvancementMMOItemsListenersTest"));

        mockMMOItemsHook = mock(MMOItemsHook.class);
        when(mockCore.getMMOItemsHook()).thenReturn(mockMMOItemsHook);
        when(mockCore.mmoItems()).thenReturn(mockMMOItemsHook);

        mockTriggerService = mock(AdvancementTriggerService.class);
        mockPlayer = mock(Player.class);
        playerUuid = UUID.randomUUID();
        when(mockPlayer.getUniqueId()).thenReturn(playerUuid);
        when(mockPlayer.getName()).thenReturn("VyrimWarrior");
        when(mockPlayer.isOnline()).thenReturn(true);
    }

    // =========================================================================
    // 1. AdvancementMMOItemCraftListener Tests
    // =========================================================================

    @Test
    @DisplayName("AdvancementMMOItemCraftListener: CraftItemEvent fires MMOITEM_CRAFT with resolved TYPE:ID")
    void testCraftItemEventWithMMOItem() {
        AdvancementMMOItemCraftListener listener = new AdvancementMMOItemCraftListener(mockMMOItemsHook, mockTriggerService);

        ItemStack mockResult = mock(ItemStack.class);
        when(mockResult.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(mockResult.getAmount()).thenReturn(1);

        Recipe mockRecipe = mock(Recipe.class);
        when(mockRecipe.getResult()).thenReturn(mockResult);

        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.getWhoClicked()).thenReturn(mockPlayer);
        when(event.isShiftClick()).thenReturn(false);
        when(event.getRecipe()).thenReturn(mockRecipe);

        when(mockMMOItemsHook.resolveMMOItemId(mockResult)).thenReturn("SWORD:EXCALIBUR");

        listener.onCraftItem(event);

        verify(mockTriggerService, times(1)).handle(mockPlayer, TriggerType.MMOITEM_CRAFT, "SWORD:EXCALIBUR", 1);
    }

    @Test
    @DisplayName("AdvancementMMOItemCraftListener: CraftItemEvent with vanilla item does NOT fire MMOITEM_CRAFT")
    void testCraftItemEventWithVanillaItemIgnored() {
        AdvancementMMOItemCraftListener listener = new AdvancementMMOItemCraftListener(mockMMOItemsHook, mockTriggerService);

        ItemStack mockResult = mock(ItemStack.class);
        when(mockResult.getType()).thenReturn(Material.IRON_SWORD);
        when(mockResult.getAmount()).thenReturn(1);

        Recipe mockRecipe = mock(Recipe.class);
        when(mockRecipe.getResult()).thenReturn(mockResult);

        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.getWhoClicked()).thenReturn(mockPlayer);
        when(event.getRecipe()).thenReturn(mockRecipe);

        when(mockMMOItemsHook.resolveMMOItemId(mockResult)).thenReturn(null);

        listener.onCraftItem(event);

        verify(mockTriggerService, never()).handle(any(), eq(TriggerType.MMOITEM_CRAFT), any(), anyInt());
    }

    @Test
    @DisplayName("AdvancementMMOItemCraftListener: PlayerUseCraftingStationEvent INSTANT_RECIPE fires MMOITEM_CRAFT")
    void testCraftingStationInstantRecipe() {
        AdvancementMMOItemCraftListener listener = new AdvancementMMOItemCraftListener(mockMMOItemsHook, mockTriggerService);

        ItemStack mockResult = mock(ItemStack.class);
        when(mockResult.getType()).thenReturn(Material.BOW);
        when(mockResult.getAmount()).thenReturn(1);

        PlayerUseCraftingStationEvent event = mock(PlayerUseCraftingStationEvent.class);
        when(event.getPlayer()).thenReturn(mockPlayer);
        when(event.hasResult()).thenReturn(true);
        when(event.getResult()).thenReturn(mockResult);
        when(event.getInteraction()).thenReturn(PlayerUseCraftingStationEvent.StationAction.INSTANT_RECIPE);

        when(mockMMOItemsHook.resolveMMOItemId(mockResult)).thenReturn("BOW:ARTEMIS");

        listener.onCraftingStation(event);

        verify(mockTriggerService, times(1)).handle(mockPlayer, TriggerType.MMOITEM_CRAFT, "BOW:ARTEMIS", 1);
    }

    @Test
    @DisplayName("AdvancementMMOItemCraftListener: PlayerUseCraftingStationEvent CRAFTING_QUEUE claim fires MMOITEM_CRAFT")
    void testCraftingStationQueueClaim() {
        AdvancementMMOItemCraftListener listener = new AdvancementMMOItemCraftListener(mockMMOItemsHook, mockTriggerService);

        ItemStack mockResult = mock(ItemStack.class);
        when(mockResult.getType()).thenReturn(Material.NETHERITE_HELMET);
        when(mockResult.getAmount()).thenReturn(1);

        PlayerUseCraftingStationEvent event = mock(PlayerUseCraftingStationEvent.class);
        when(event.getPlayer()).thenReturn(mockPlayer);
        when(event.hasResult()).thenReturn(true);
        when(event.getResult()).thenReturn(mockResult);
        when(event.getInteraction()).thenReturn(PlayerUseCraftingStationEvent.StationAction.CRAFTING_QUEUE);

        when(mockMMOItemsHook.resolveMMOItemId(mockResult)).thenReturn("ARMOR:DRAGON_HELMET");

        listener.onCraftingStation(event);

        verify(mockTriggerService, times(1)).handle(mockPlayer, TriggerType.MMOITEM_CRAFT, "ARMOR:DRAGON_HELMET", 1);
    }

    @Test
    @DisplayName("AdvancementMMOItemCraftListener: PlayerUseCraftingStationEvent CANCEL_QUEUE is ignored")
    void testCraftingStationCancelQueueIgnored() {
        AdvancementMMOItemCraftListener listener = new AdvancementMMOItemCraftListener(mockMMOItemsHook, mockTriggerService);

        ItemStack mockResult = mock(ItemStack.class);
        PlayerUseCraftingStationEvent event = mock(PlayerUseCraftingStationEvent.class);
        when(event.getPlayer()).thenReturn(mockPlayer);
        when(event.hasResult()).thenReturn(true);
        when(event.getResult()).thenReturn(mockResult);
        when(event.getInteraction()).thenReturn(PlayerUseCraftingStationEvent.StationAction.CANCEL_QUEUE);

        listener.onCraftingStation(event);

        verify(mockTriggerService, never()).handle(any(), any(), any(), anyInt());
    }

    @Test
    @SuppressWarnings("deprecation")
    @DisplayName("AdvancementMMOItemCraftListener: CraftMMOItemEvent fires MMOITEM_CRAFT")
    void testCraftMMOItemEvent() {
        AdvancementMMOItemCraftListener listener = new AdvancementMMOItemCraftListener(mockMMOItemsHook, mockTriggerService);

        ItemStack mockResult = mock(ItemStack.class);
        when(mockResult.getAmount()).thenReturn(2);

        CraftMMOItemEvent event = mock(CraftMMOItemEvent.class);
        when(event.getPlayer()).thenReturn(mockPlayer);
        when(event.getResult()).thenReturn(mockResult);

        when(mockMMOItemsHook.resolveMMOItemId(mockResult)).thenReturn("SWORD:KATANA");

        listener.onCraftMMOItem(event);

        verify(mockTriggerService, times(1)).handle(mockPlayer, TriggerType.MMOITEM_CRAFT, "SWORD:KATANA", 2);
    }

    // =========================================================================
    // 2. AdvancementMMOItemPickupListener Tests
    // =========================================================================

    @Test
    @DisplayName("AdvancementMMOItemPickupListener: EntityPickupItemEvent fires MMOITEM_PICKUP with stack size")
    void testPickupMMOItem() {
        AdvancementMMOItemPickupListener listener = new AdvancementMMOItemPickupListener(mockMMOItemsHook, mockTriggerService);

        ItemStack mockStack = mock(ItemStack.class);
        when(mockStack.getType()).thenReturn(Material.EMERALD);
        when(mockStack.getAmount()).thenReturn(10);

        Item mockItemEntity = mock(Item.class);
        when(mockItemEntity.getItemStack()).thenReturn(mockStack);

        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(mockPlayer);
        when(event.getItem()).thenReturn(mockItemEntity);

        when(mockMMOItemsHook.resolveMMOItemId(mockStack)).thenReturn("GEM_STONE:RUBY");

        listener.onEntityPickupItem(event);

        verify(mockTriggerService, times(1)).handle(mockPlayer, TriggerType.MMOITEM_PICKUP, "GEM_STONE:RUBY", 10);
    }

    @Test
    @DisplayName("AdvancementMMOItemPickupListener: Non-player entity pickup is ignored")
    void testPickupNonPlayerIgnored() {
        AdvancementMMOItemPickupListener listener = new AdvancementMMOItemPickupListener(mockMMOItemsHook, mockTriggerService);

        LivingEntity nonPlayer = mock(LivingEntity.class);
        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(nonPlayer);

        listener.onEntityPickupItem(event);

        verify(mockTriggerService, never()).handle(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("AdvancementMMOItemPickupListener: Vanilla item pickup does NOT fire MMOITEM_PICKUP")
    void testPickupVanillaItemIgnored() {
        AdvancementMMOItemPickupListener listener = new AdvancementMMOItemPickupListener(mockMMOItemsHook, mockTriggerService);

        ItemStack mockStack = mock(ItemStack.class);
        when(mockStack.getType()).thenReturn(Material.IRON_INGOT);
        when(mockStack.getAmount()).thenReturn(5);

        Item mockItemEntity = mock(Item.class);
        when(mockItemEntity.getItemStack()).thenReturn(mockStack);

        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(mockPlayer);
        when(event.getItem()).thenReturn(mockItemEntity);

        when(mockMMOItemsHook.resolveMMOItemId(mockStack)).thenReturn(null);

        listener.onEntityPickupItem(event);

        verify(mockTriggerService, never()).handle(any(), eq(TriggerType.MMOITEM_PICKUP), any(), anyInt());
    }

    // =========================================================================
    // 3. AdvancementMMOItemConsumeListener Tests
    // =========================================================================

    @Test
    @DisplayName("AdvancementMMOItemConsumeListener: ConsumableConsumedEvent fires MMOITEM_CONSUME")
    void testConsumableConsumedEvent() {
        AdvancementMMOItemConsumeListener listener = new AdvancementMMOItemConsumeListener(mockMMOItemsHook, mockTriggerService);

        VolatileMMOItem mockVolatile = mock(VolatileMMOItem.class);
        Type mockType = mock(Type.class);
        when(mockType.getId()).thenReturn("CONSUMABLE");
        when(mockVolatile.getType()).thenReturn(mockType);
        when(mockVolatile.getId()).thenReturn("GRAND_HEALING_ELIXIR");

        ConsumableConsumedEvent event = mock(ConsumableConsumedEvent.class);
        when(event.getPlayer()).thenReturn(mockPlayer);
        when(event.getMMOItem()).thenReturn(mockVolatile);

        listener.onMMOItemConsumed(event);

        verify(mockTriggerService, times(1)).handle(mockPlayer, TriggerType.MMOITEM_CONSUME, "CONSUMABLE:GRAND_HEALING_ELIXIR", 1);
    }

    @Test
    @DisplayName("AdvancementMMOItemConsumeListener: PlayerItemConsumeEvent fallback fires when not previously handled")
    void testPlayerItemConsumeEventFallback() {
        AdvancementMMOItemConsumeListener listener = new AdvancementMMOItemConsumeListener(mockMMOItemsHook, mockTriggerService);

        ItemStack mockStack = mock(ItemStack.class);
        when(mockStack.getType()).thenReturn(Material.COOKED_BEEF);

        PlayerItemConsumeEvent event = mock(PlayerItemConsumeEvent.class);
        when(event.getPlayer()).thenReturn(mockPlayer);
        when(event.getItem()).thenReturn(mockStack);

        when(mockMMOItemsHook.resolveMMOItemId(mockStack)).thenReturn("FOOD:SUPER_STEAK");

        listener.onPlayerItemConsume(event);

        verify(mockTriggerService, times(1)).handle(mockPlayer, TriggerType.MMOITEM_CONSUME, "FOOD:SUPER_STEAK", 1);
    }

    @Test
    @DisplayName("AdvancementMMOItemConsumeListener: Tick-level de-duplication prevents double firing")
    void testConsumeDeDuplication() {
        AdvancementMMOItemConsumeListener listener = new AdvancementMMOItemConsumeListener(mockMMOItemsHook, mockTriggerService);

        VolatileMMOItem mockVolatile = mock(VolatileMMOItem.class);
        Type mockType = mock(Type.class);
        when(mockType.getId()).thenReturn("CONSUMABLE");
        when(mockVolatile.getType()).thenReturn(mockType);
        when(mockVolatile.getId()).thenReturn("MANA_POTION");

        ConsumableConsumedEvent consumableEvent = mock(ConsumableConsumedEvent.class);
        when(consumableEvent.getPlayer()).thenReturn(mockPlayer);
        when(consumableEvent.getMMOItem()).thenReturn(mockVolatile);

        ItemStack mockStack = mock(ItemStack.class);
        PlayerItemConsumeEvent vanillaEvent = mock(PlayerItemConsumeEvent.class);
        when(vanillaEvent.getPlayer()).thenReturn(mockPlayer);
        when(vanillaEvent.getItem()).thenReturn(mockStack);
        when(mockMMOItemsHook.resolveMMOItemId(mockStack)).thenReturn("CONSUMABLE:MANA_POTION");

        // First, ConsumableConsumedEvent fires
        listener.onMMOItemConsumed(consumableEvent);

        // Then PlayerItemConsumeEvent fires in the same tick for the same player
        listener.onPlayerItemConsume(vanillaEvent);

        // Verify MMOITEM_CONSUME only fired ONCE, not twice!
        verify(mockTriggerService, times(1)).handle(mockPlayer, TriggerType.MMOITEM_CONSUME, "CONSUMABLE:MANA_POTION", 1);
    }

    // =========================================================================
    // 4. Step 4 Native vs MMOItems Material Overlap Filtering Tests (Option A)
    // =========================================================================

    @Test
    @DisplayName("Step 4 Filter: AdvancementCraftListener skips native CRAFT_ITEM if item is an MMOItem")
    void testNativeCraftListenerFiltersMMOItem() {
        AdvancementCraftListener listener = new AdvancementCraftListener(mockTriggerService, mockMMOItemsHook);

        ItemStack mockResult = mock(ItemStack.class);
        when(mockResult.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(mockResult.getAmount()).thenReturn(1);

        Recipe mockRecipe = mock(Recipe.class);
        when(mockRecipe.getResult()).thenReturn(mockResult);

        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.getWhoClicked()).thenReturn(mockPlayer);
        when(event.getRecipe()).thenReturn(mockRecipe);

        when(mockMMOItemsHook.isMMOItem(mockResult)).thenReturn(true);

        listener.onCraftItem(event);

        // Crucial test: CRAFT_ITEM is NOT called for the base material DIAMOND_SWORD!
        verify(mockTriggerService, never()).handle(any(), eq(TriggerType.CRAFT_ITEM), any(), anyInt());
    }

    @Test
    @DisplayName("Step 4 Filter: AdvancementCraftListener allows pure vanilla craft to trigger CRAFT_ITEM")
    void testNativeCraftListenerAllowsVanillaCraft() {
        AdvancementCraftListener listener = new AdvancementCraftListener(mockTriggerService, mockMMOItemsHook);

        ItemStack mockResult = mock(ItemStack.class);
        when(mockResult.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(mockResult.getAmount()).thenReturn(1);

        Recipe mockRecipe = mock(Recipe.class);
        when(mockRecipe.getResult()).thenReturn(mockResult);

        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.getWhoClicked()).thenReturn(mockPlayer);
        when(event.isShiftClick()).thenReturn(false);
        when(event.getRecipe()).thenReturn(mockRecipe);

        when(mockMMOItemsHook.isMMOItem(mockResult)).thenReturn(false);

        listener.onCraftItem(event);

        verify(mockTriggerService, times(1)).handle(mockPlayer, TriggerType.CRAFT_ITEM, "DIAMOND_SWORD", 1);
    }

    @Test
    @DisplayName("Step 4 Filter: AdvancementItemPickupListener skips native PICKUP_ITEM if item is an MMOItem")
    void testNativePickupListenerFiltersMMOItem() {
        AdvancementItemPickupListener listener = new AdvancementItemPickupListener(mockTriggerService, mockMMOItemsHook);

        ItemStack mockStack = mock(ItemStack.class);
        when(mockStack.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(mockStack.getAmount()).thenReturn(1);

        Item mockItemEntity = mock(Item.class);
        when(mockItemEntity.getItemStack()).thenReturn(mockStack);

        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(mockPlayer);
        when(event.getItem()).thenReturn(mockItemEntity);

        when(mockMMOItemsHook.isMMOItem(mockStack)).thenReturn(true);

        listener.onEntityPickupItem(event);

        verify(mockTriggerService, never()).handle(any(), eq(TriggerType.PICKUP_ITEM), any(), anyInt());
    }

    @Test
    @DisplayName("Step 4 Filter: AdvancementItemConsumeListener skips native CONSUME_ITEM if item is an MMOItem")
    void testNativeConsumeListenerFiltersMMOItem() {
        AdvancementItemConsumeListener listener = new AdvancementItemConsumeListener(mockTriggerService, mockMMOItemsHook);

        ItemStack mockStack = mock(ItemStack.class);
        when(mockStack.getType()).thenReturn(Material.POTION);

        PlayerItemConsumeEvent event = mock(PlayerItemConsumeEvent.class);
        when(event.getPlayer()).thenReturn(mockPlayer);
        when(event.getItem()).thenReturn(mockStack);

        when(mockMMOItemsHook.isMMOItem(mockStack)).thenReturn(true);

        listener.onPlayerItemConsume(event);

        verify(mockTriggerService, never()).handle(any(), eq(TriggerType.CONSUME_ITEM), any(), anyInt());
    }
}

package net.vyrim.core.module.advancements;

import net.vyrim.core.VyrimCore;
import net.vyrim.core.module.advancements.listener.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdvancementListenersTest {

    private VyrimCore mockCore;
    private AdvancementTriggerService mockTriggerService;
    private Player mockPlayer;
    private UUID playerUuid;

    @BeforeEach
    void setUp() {
        mockCore = mock(VyrimCore.class);
        when(mockCore.getLogger()).thenReturn(Logger.getLogger("AdvancementListenersTest"));

        org.bukkit.Server mockServer = mock(org.bukkit.Server.class);
        try {
            java.lang.reflect.Field serverField = org.bukkit.Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, mockServer);
        } catch (Throwable ignored) {
        }

        mockTriggerService = mock(AdvancementTriggerService.class);
        mockPlayer = mock(Player.class);
        playerUuid = UUID.randomUUID();
        when(mockPlayer.getUniqueId()).thenReturn(playerUuid);
        when(mockPlayer.getName()).thenReturn("VyrimPlayer");
        when(mockPlayer.isOnline()).thenReturn(true);
    }

    @Test
    @DisplayName("AdvancementBiomeListener: ignores movement inside same block, only triggers on new biome")
    void testBiomeListenerMovementFilter() {
        World mockWorld = mock(World.class);

        Location loc1 = new Location(mockWorld, 10.1, 64.0, 20.1);
        Location loc2SameBlock = new Location(mockWorld, 10.8, 64.2, 20.9);
        Location loc3NewBlockPlains = new Location(mockWorld, 11.0, 64.0, 20.0);
        Location loc4AnotherBlockPlains = new Location(mockWorld, 12.0, 64.0, 20.0);

        Block blockPlains = mock(Block.class);
        when(mockWorld.getBlockAt(11, 64, 20)).thenReturn(blockPlains);
        when(loc3NewBlockPlains.getBlock()).thenReturn(blockPlains);
        when(loc4AnotherBlockPlains.getBlock()).thenReturn(blockPlains);

        AdvancementBiomeListener listener = new AdvancementBiomeListener(mockTriggerService, block -> "PLAINS");

        // 1. Movement within same block: should return immediately and not check biome
        PlayerMoveEvent eventSameBlock = new PlayerMoveEvent(mockPlayer, loc1, loc2SameBlock);
        listener.onPlayerMove(eventSameBlock);
        verify(mockTriggerService, never()).handle(any(), any(), any(), anyInt());

        // 2. Movement across block boundary into PLAINS
        PlayerMoveEvent eventNewBlock = new PlayerMoveEvent(mockPlayer, loc1, loc3NewBlockPlains);
        listener.onPlayerMove(eventNewBlock);
        verify(mockTriggerService, times(1)).handle(mockPlayer, TriggerType.DISCOVER_BIOME, "PLAINS", 1);

        // 3. Movement across block boundary within ALREADY DISCOVERED PLAINS
        PlayerMoveEvent eventAlreadyDiscovered = new PlayerMoveEvent(mockPlayer, loc3NewBlockPlains, loc4AnotherBlockPlains);
        listener.onPlayerMove(eventAlreadyDiscovered);
        // Verify triggerService was NOT called again! Still 1 total invocation.
        verify(mockTriggerService, times(1)).handle(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("AdvancementEntityDeathListener: only fires when killer is a Player")
    void testEntityDeathListener() {
        AdvancementEntityDeathListener listener = new AdvancementEntityDeathListener(mockTriggerService);

        LivingEntity mockEntity = mock(LivingEntity.class);
        EntityDeathEvent nonPlayerKillEvent = mock(EntityDeathEvent.class);
        when(nonPlayerKillEvent.getEntity()).thenReturn(mockEntity);
        when(mockEntity.getKiller()).thenReturn(null); // mob or environmental death

        listener.onEntityDeath(nonPlayerKillEvent);
        verify(mockTriggerService, never()).handle(any(), any(), any(), anyInt());

        when(mockEntity.getKiller()).thenReturn(mockPlayer);

        EntityDeathEvent playerKillEvent = mock(EntityDeathEvent.class);
        when(playerKillEvent.getEntity()).thenReturn(mockEntity);
        when(playerKillEvent.getEntityType()).thenReturn(org.bukkit.entity.EntityType.ZOMBIE);
        listener.onEntityDeath(playerKillEvent);

        verify(mockTriggerService, times(1)).handle(mockPlayer, TriggerType.KILL_ENTITY, "ZOMBIE", 1);
    }

    @Test
    @DisplayName("AdvancementBlockBreakListener: fires BREAK_BLOCK with block material")
    void testBlockBreakListener() {
        AdvancementBlockBreakListener listener = new AdvancementBlockBreakListener(mockTriggerService);

        Block mockBlock = mock(Block.class);
        when(mockBlock.getType()).thenReturn(Material.STONE);

        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getPlayer()).thenReturn(mockPlayer);
        when(event.getBlock()).thenReturn(mockBlock);

        listener.onBlockBreak(event);

        verify(mockTriggerService, times(1)).handle(mockPlayer, TriggerType.BREAK_BLOCK, "STONE", 1);
    }

    @Test
    @DisplayName("AdvancementBlockPlaceListener: fires PLACE_BLOCK with block material")
    void testBlockPlaceListener() {
        AdvancementBlockPlaceListener listener = new AdvancementBlockPlaceListener(mockTriggerService);

        Block mockBlock = mock(Block.class);
        when(mockBlock.getType()).thenReturn(Material.OAK_PLANKS);

        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getPlayer()).thenReturn(mockPlayer);
        when(event.getBlock()).thenReturn(mockBlock);

        listener.onBlockPlace(event);

        verify(mockTriggerService, times(1)).handle(mockPlayer, TriggerType.PLACE_BLOCK, "OAK_PLANKS", 1);
    }

    @Test
    @DisplayName("AdvancementItemConsumeListener: fires CONSUME_ITEM with item material")
    void testItemConsumeListener() {
        AdvancementItemConsumeListener listener = new AdvancementItemConsumeListener(mockTriggerService);

        ItemStack mockItem = mock(ItemStack.class);
        when(mockItem.getType()).thenReturn(Material.GOLDEN_APPLE);

        PlayerItemConsumeEvent event = mock(PlayerItemConsumeEvent.class);
        when(event.getPlayer()).thenReturn(mockPlayer);
        when(event.getItem()).thenReturn(mockItem);

        listener.onPlayerItemConsume(event);

        verify(mockTriggerService, times(1)).handle(mockPlayer, TriggerType.CONSUME_ITEM, "GOLDEN_APPLE", 1);
    }

    @Test
    @DisplayName("AdvancementItemPickupListener: passes full picked-up stack amount as delta")
    void testItemPickupListenerWithStackAmount() {
        AdvancementItemPickupListener listener = new AdvancementItemPickupListener(mockTriggerService);

        ItemStack mockStack = mock(ItemStack.class);
        when(mockStack.getType()).thenReturn(Material.OAK_LOG);
        when(mockStack.getAmount()).thenReturn(16);

        Item mockItemEntity = mock(Item.class);
        when(mockItemEntity.getItemStack()).thenReturn(mockStack);

        EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
        when(event.getEntity()).thenReturn(mockPlayer);
        when(event.getItem()).thenReturn(mockItemEntity);

        listener.onEntityPickupItem(event);

        // Crucial test: delta must be 16 (the stack size), NOT 1!
        verify(mockTriggerService, times(1)).handle(mockPlayer, TriggerType.PICKUP_ITEM, "OAK_LOG", 16);
    }

    @Test
    @DisplayName("AdvancementCraftListener: fires CRAFT_ITEM with recipe result")
    void testCraftItemListener() {
        AdvancementCraftListener listener = new AdvancementCraftListener(mockTriggerService);

        ItemStack mockResult = mock(ItemStack.class);
        when(mockResult.getType()).thenReturn(Material.IRON_PICKAXE);
        when(mockResult.getAmount()).thenReturn(1);

        Recipe mockRecipe = mock(Recipe.class);
        when(mockRecipe.getResult()).thenReturn(mockResult);

        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.getWhoClicked()).thenReturn(mockPlayer);
        when(event.isShiftClick()).thenReturn(false);
        when(event.getRecipe()).thenReturn(mockRecipe);

        listener.onCraftItem(event);

        verify(mockTriggerService, times(1)).handle(mockPlayer, TriggerType.CRAFT_ITEM, "IRON_PICKAXE", 1);
    }

    @Test
    @DisplayName("AdvancementCraftListener calculateCraftedAmount: handles shift-click with ingredient bounds")
    void testCraftListenerShiftClick() {
        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.isShiftClick()).thenReturn(true);

        CraftingInventory inv = mock(CraftingInventory.class);
        when(event.getInventory()).thenReturn(inv);

        ItemStack mockCoal = mock(ItemStack.class);
        when(mockCoal.getType()).thenReturn(Material.COAL);
        when(mockCoal.getAmount()).thenReturn(4);

        ItemStack mockStick = mock(ItemStack.class);
        when(mockStick.getType()).thenReturn(Material.STICK);
        when(mockStick.getAmount()).thenReturn(4);

        when(inv.getMatrix()).thenReturn(new ItemStack[]{mockCoal, mockStick});

        PlayerInventory playerInv = mock(PlayerInventory.class);
        when(mockPlayer.getInventory()).thenReturn(playerInv);
        when(playerInv.getItem(anyInt())).thenReturn(null);

        ItemStack mockTorchResult = mock(ItemStack.class);
        when(mockTorchResult.getType()).thenReturn(Material.TORCH);
        when(mockTorchResult.getAmount()).thenReturn(4);
        when(mockTorchResult.getMaxStackSize()).thenReturn(64);

        int crafted = AdvancementCraftListener.calculateCraftedAmount(event, mockTorchResult, mockPlayer);

        // 4 crafts * 4 torches each = 16 torches!
        assertEquals(16, crafted);
    }

    @Test
    @DisplayName("AdvancementStatisticListener: evaluates statistics on PlayerQuitEvent")
    void testStatisticListenerQuitCheckpoint() {
        AdvancementStatisticListener listener = new AdvancementStatisticListener(mockCore, mockTriggerService);

        PlayerQuitEvent quitEvent = mock(PlayerQuitEvent.class);
        when(quitEvent.getPlayer()).thenReturn(mockPlayer);

        listener.onPlayerQuit(quitEvent);

        verify(mockTriggerService, times(1)).checkStatistics(mockPlayer);
    }
}

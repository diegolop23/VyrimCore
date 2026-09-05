package net.vyrim.core.module.advancements;

import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import net.vyrim.core.VyrimCore;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdvancementTriggerServiceTest {

    private VyrimCore mockCore;
    private AdvancementLoader mockLoader;
    private AdvancementRewardService mockRewardService;
    private AdvancementProgressStore progressStore;
    private Connection connection;
    private Player mockPlayer;
    private UUID playerUuid;

    @BeforeEach
    void setUp() throws SQLException {
        mockCore = mock(VyrimCore.class);
        when(mockCore.getLogger()).thenReturn(Logger.getLogger("AdvancementTriggerServiceTest"));

        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        progressStore = new AdvancementProgressStore(mockCore, () -> connection);
        progressStore.init();

        mockLoader = mock(AdvancementLoader.class);
        mockRewardService = mock(AdvancementRewardService.class);

        mockPlayer = mock(Player.class);
        playerUuid = UUID.randomUUID();
        when(mockPlayer.getUniqueId()).thenReturn(playerUuid);
        when(mockPlayer.getName()).thenReturn("VyrimTester");
        when(mockPlayer.isOnline()).thenReturn(true);
    }

    @Test
    @DisplayName("buildIndex builds reverse index for exact and wildcard triggers")
    void testBuildIndex() {
        AdvancementTriggerData joinTrigger = new AdvancementTriggerData("JOIN_SERVER", null, 1);
        AdvancementTriggerData breakStone = new AdvancementTriggerData("BREAK_BLOCK", "STONE", 50);
        AdvancementTriggerData breakAny = new AdvancementTriggerData("BREAK_BLOCK", null, 10);
        AdvancementTriggerData statJump = new AdvancementTriggerData("STATISTIC", "JUMP", 100);

        when(mockLoader.getTriggerDataMap()).thenReturn(Map.of(
                "join_adv", joinTrigger,
                "mine_stone_adv", breakStone,
                "mine_any_adv", breakAny,
                "jump_adv", statJump
        ));

        AdvancementTriggerService service = new AdvancementTriggerService(
                mockCore, () -> mockLoader, mockRewardService, progressStore
        );
        service.buildIndex();

        Map<AdvancementTriggerService.TriggerKey, ?> index = service.getReverseIndex();

        assertTrue(index.containsKey(new AdvancementTriggerService.TriggerKey(TriggerType.JOIN_SERVER, null)));
        assertTrue(index.containsKey(new AdvancementTriggerService.TriggerKey(TriggerType.BREAK_BLOCK, "STONE")));
        assertTrue(index.containsKey(new AdvancementTriggerService.TriggerKey(TriggerType.BREAK_BLOCK, null)));
        assertTrue(index.containsKey(new AdvancementTriggerService.TriggerKey(TriggerType.STATISTIC, "JUMP")));
    }

    @Test
    @DisplayName("amount = 1 unlocks immediately via API, dispatches reward, and does not touch progress store")
    void testAmountOneInstantUnlock() {
        AdvancementTriggerData trigger = new AdvancementTriggerData("JOIN_SERVER", null, 1);
        Advancement mockAdv = mock(Advancement.class);

        when(mockAdv.isGranted(mockPlayer)).thenReturn(false);
        when(mockLoader.getTriggerDataMap()).thenReturn(Map.of("welcome", trigger));
        when(mockLoader.getAdvancement("welcome")).thenReturn(mockAdv);

        AdvancementTriggerService service = new AdvancementTriggerService(
                mockCore, () -> mockLoader, mockRewardService, progressStore
        );
        service.buildIndex();

        service.handle(mockPlayer, TriggerType.JOIN_SERVER, null, 1);

        verify(mockAdv, times(1)).grant(mockPlayer);
        verify(mockRewardService, times(1)).grantRewards(mockPlayer, "welcome");
        assertEquals(0, progressStore.getProgress(playerUuid, "welcome"));
    }

    @Test
    @DisplayName("Already unlocked advancement is skipped immediately and never re-grants rewards")
    void testAlreadyUnlockedIsSkipped() {
        AdvancementTriggerData trigger = new AdvancementTriggerData("BREAK_BLOCK", "STONE", 1);
        Advancement mockAdv = mock(Advancement.class);

        // Already unlocked in UltimateAdvancementAPI
        when(mockAdv.isGranted(mockPlayer)).thenReturn(true);
        when(mockLoader.getTriggerDataMap()).thenReturn(Map.of("mine_first_stone", trigger));
        when(mockLoader.getAdvancement("mine_first_stone")).thenReturn(mockAdv);

        AdvancementTriggerService service = new AdvancementTriggerService(
                mockCore, () -> mockLoader, mockRewardService, progressStore
        );
        service.buildIndex();

        service.handle(mockPlayer, TriggerType.BREAK_BLOCK, "STONE", 1);

        // Neither grant nor reward should be called
        verify(mockAdv, never()).grant(any());
        verify(mockRewardService, never()).grantRewards(any(Player.class), any(String.class));
        assertEquals(0, progressStore.getProgress(playerUuid, "mine_first_stone"));
    }

    @Test
    @DisplayName("amount > 1 accumulates progress, updates visual bar, and unlocks + resets at threshold")
    void testMultiStepCumulativeProgression() {
        AdvancementTriggerData trigger = new AdvancementTriggerData("BREAK_BLOCK", "STONE", 50);
        Advancement mockAdv = mock(Advancement.class);

        when(mockAdv.isGranted(mockPlayer)).thenReturn(false);
        when(mockLoader.getTriggerDataMap()).thenReturn(Map.of("mining_stone", trigger));
        when(mockLoader.getAdvancement("mining_stone")).thenReturn(mockAdv);

        AdvancementTriggerService service = new AdvancementTriggerService(
                mockCore, () -> mockLoader, mockRewardService, progressStore
        );
        service.buildIndex();

        // Step 1: Break 20 blocks
        service.handle(mockPlayer, TriggerType.BREAK_BLOCK, "STONE", 20);
        assertEquals(20, progressStore.getProgress(playerUuid, "mining_stone"));
        verify(mockAdv, times(1)).setProgression(mockPlayer, 20, false);
        verify(mockAdv, never()).grant(any());
        verify(mockRewardService, never()).grantRewards(any(Player.class), any(String.class));

        // Step 2: Break 29 more blocks (total 49/50)
        service.handle(mockPlayer, TriggerType.BREAK_BLOCK, "STONE", 29);
        assertEquals(49, progressStore.getProgress(playerUuid, "mining_stone"));
        verify(mockAdv, times(1)).setProgression(mockPlayer, 49, false);
        verify(mockAdv, never()).grant(any());
        verify(mockRewardService, never()).grantRewards(any(Player.class), any(String.class));

        // Step 3: Break 1 more block (reaches threshold 50!)
        service.handle(mockPlayer, TriggerType.BREAK_BLOCK, "STONE", 1);

        verify(mockAdv, times(1)).grant(mockPlayer);
        verify(mockRewardService, times(1)).grantRewards(mockPlayer, "mining_stone");

        // Progress counter must be reset upon completion!
        assertEquals(0, progressStore.getProgress(playerUuid, "mining_stone"));
    }

    @Test
    @DisplayName("checkStatistics evaluates untyped, block, and entity statistics correctly")
    void testCheckStatistics() {
        AdvancementTriggerData jumpTrigger = new AdvancementTriggerData("STATISTIC", "JUMP", 50);
        AdvancementTriggerData mineDiamondTrigger = new AdvancementTriggerData("STATISTIC", "MINE_BLOCK:DIAMOND_ORE", 10);
        AdvancementTriggerData killZombieTrigger = new AdvancementTriggerData("STATISTIC", "KILL_ENTITY:ZOMBIE", 5);

        Advancement jumpAdv = mock(Advancement.class);
        Advancement mineAdv = mock(Advancement.class);
        Advancement killAdv = mock(Advancement.class);

        when(jumpAdv.isGranted(mockPlayer)).thenReturn(false);
        when(mineAdv.isGranted(mockPlayer)).thenReturn(false);
        when(killAdv.isGranted(mockPlayer)).thenReturn(false);

        when(mockLoader.getTriggerDataMap()).thenReturn(Map.of(
                "jump_50", jumpTrigger,
                "mine_10_diamonds", mineDiamondTrigger,
                "kill_5_zombies", killZombieTrigger
        ));
        when(mockLoader.getAdvancement("jump_50")).thenReturn(jumpAdv);
        when(mockLoader.getAdvancement("mine_10_diamonds")).thenReturn(mineAdv);
        when(mockLoader.getAdvancement("kill_5_zombies")).thenReturn(killAdv);

        // Player statistics
        when(mockPlayer.getStatistic(Statistic.JUMP)).thenReturn(60); // 60 >= 50 -> unlocks!
        when(mockPlayer.getStatistic(Statistic.MINE_BLOCK, Material.DIAMOND_ORE)).thenReturn(4); // 4 < 10 -> updates bar
        when(mockPlayer.getStatistic(Statistic.KILL_ENTITY, EntityType.ZOMBIE)).thenReturn(5); // 5 >= 5 -> unlocks!

        AdvancementTriggerService service = new AdvancementTriggerService(
                mockCore, () -> mockLoader, mockRewardService, progressStore
        );
        service.buildIndex();

        service.checkStatistics(mockPlayer);

        verify(jumpAdv, times(1)).grant(mockPlayer);
        verify(mockRewardService, times(1)).grantRewards(mockPlayer, "jump_50");

        verify(mineAdv, times(1)).setProgression(mockPlayer, 4, false);
        verify(mineAdv, never()).grant(any());

        verify(killAdv, times(1)).grant(mockPlayer);
        verify(mockRewardService, times(1)).grantRewards(mockPlayer, "kill_5_zombies");
    }
}

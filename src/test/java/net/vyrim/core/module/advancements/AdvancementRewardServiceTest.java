package net.vyrim.core.module.advancements;

import net.vyrim.core.VyrimCore;
import net.vyrim.core.hook.LuckPermsHook;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdvancementRewardServiceTest {

    private VyrimCore mockCore;
    private LuckPermsHook mockLuckPerms;
    private AdvancementLoader mockLoader;
    private Logger mockLogger;
    private Player mockPlayer;

    @BeforeEach
    void setUp() {
        mockCore = mock(VyrimCore.class);
        mockLuckPerms = mock(LuckPermsHook.class);
        mockLoader = mock(AdvancementLoader.class);
        mockLogger = mock(Logger.class);
        mockPlayer = mock(Player.class);

        when(mockCore.getLogger()).thenReturn(mockLogger);
        when(mockCore.getLuckPermsHook()).thenReturn(mockLuckPerms);
        when(mockLuckPerms.isAvailable()).thenReturn(true);
        when(mockPlayer.getName()).thenReturn("TestPlayer");
    }

    @Test
    @DisplayName("grantRewards grants permission via LuckPermsHook when present")
    void testGrantPermissionReward() {
        AdvancementRewardService service = new AdvancementRewardService(mockCore, () -> mockLoader);
        AdvancementRewardData reward = new AdvancementRewardData(null, "vyrim.test.reward");

        service.grantRewards(mockPlayer, reward);

        verify(mockLuckPerms).grantPermission(mockPlayer, "vyrim.test.reward");
    }

    @Test
    @DisplayName("grantRewards looks up advancement reward from loader by ID")
    void testGrantRewardsById() {
        AdvancementRewardData reward = new AdvancementRewardData(null, "vyrim.advancement.vip");
        when(mockLoader.getRewardDataMap()).thenReturn(Map.of("vip_advancement", reward));

        AdvancementRewardService service = new AdvancementRewardService(mockCore, () -> mockLoader);
        service.grantRewards(mockPlayer, "vip_advancement");

        verify(mockLuckPerms).grantPermission(mockPlayer, "vyrim.advancement.vip");
    }

    @Test
    @DisplayName("grantRewards warns and does not crash when LuckPerms is unavailable")
    void testLuckPermsUnavailable() {
        when(mockLuckPerms.isAvailable()).thenReturn(false);

        AdvancementRewardService service = new AdvancementRewardService(mockCore, () -> mockLoader);
        AdvancementRewardData reward = new AdvancementRewardData(null, "vyrim.test.reward");

        assertDoesNotThrow(() -> service.grantRewards(mockPlayer, reward));
        verify(mockLogger).warning(contains("LuckPerms is not available"));
        verify(mockLuckPerms, never()).grantPermission(any(), any());
    }

    @Test
    @DisplayName("Null player or null reward data does not cause NPE")
    void testNullSafety() {
        AdvancementRewardService service = new AdvancementRewardService(mockCore, () -> mockLoader);

        assertDoesNotThrow(() -> service.grantRewards(null, (AdvancementRewardData) null));
        assertDoesNotThrow(() -> service.grantRewards(mockPlayer, (AdvancementRewardData) null));
        assertDoesNotThrow(() -> service.grantRewards(null, "test_id"));
        assertDoesNotThrow(() -> service.grantRewards(mockPlayer, "non_existent_id"));
    }
}

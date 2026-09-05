package net.vyrim.core.hook;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LuckPermsHookTest {

    @Test
    @DisplayName("Fallback to Bukkit Permissible when LuckPerms is absent")
    void testBukkitFallback() {
        LuckPermsHook hook = new LuckPermsHook(null);
        assertFalse(hook.isAvailable());

        Player mockPlayer = mock(Player.class);
        when(mockPlayer.hasPermission("vyrimcore.bypass.biomecompass")).thenReturn(true);
        when(mockPlayer.hasPermission("vyrimcore.other")).thenReturn(false);

        assertTrue(hook.hasPermission(mockPlayer, "vyrimcore.bypass.biomecompass"));
        assertFalse(hook.hasPermission(mockPlayer, "vyrimcore.other"));

        CommandSender mockSender = mock(CommandSender.class);
        when(mockSender.hasPermission("vyrimcore.command.reload")).thenReturn(true);
        assertTrue(hook.hasPermission(mockSender, "vyrimcore.command.reload"));
    }

    @Test
    @DisplayName("Gracefully handles null parameters")
    void testNullParameters() {
        LuckPermsHook hook = new LuckPermsHook(null);

        assertFalse(hook.hasPermission((Player) null, "some.perm"));
        assertFalse(hook.hasPermission((CommandSender) null, "some.perm"));

        Player mockPlayer = mock(Player.class);
        assertFalse(hook.hasPermission(mockPlayer, null));

        CommandSender mockSender = mock(CommandSender.class);
        assertFalse(hook.hasPermission(mockSender, null));
    }

    @Test
    @DisplayName("Close cleans up references safely")
    void testClose() {
        LuckPermsHook hook = new LuckPermsHook(null);
        hook.close();
        assertFalse(hook.isAvailable());
    }
}

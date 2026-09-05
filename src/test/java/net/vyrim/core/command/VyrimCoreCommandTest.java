package net.vyrim.core.command;

import net.kyori.adventure.text.Component;
import net.vyrim.core.VyrimCore;
import net.vyrim.core.hook.LuckPermsHook;
import net.vyrim.core.module.Module;
import net.vyrim.core.module.ModuleManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VyrimCoreCommandTest {

    private VyrimCore mockCore;
    private ModuleManager mockManager;
    private LuckPermsHook mockLuckPerms;
    private CommandSender mockSender;
    private Command mockCommand;
    private VyrimCoreCommand command;

    @BeforeEach
    void setUp() {
        mockCore = mock(VyrimCore.class);
        mockManager = mock(ModuleManager.class);
        mockLuckPerms = mock(LuckPermsHook.class);
        mockSender = mock(CommandSender.class);
        mockCommand = mock(Command.class);

        when(mockCore.getModuleManager()).thenReturn(mockManager);
        when(mockCore.getLuckPermsHook()).thenReturn(mockLuckPerms);

        command = new VyrimCoreCommand(mockCore);
    }

    @Test
    @DisplayName("Command rejects sender without permission")
    void testPermissionDenied() {
        when(mockLuckPerms.hasPermission(mockSender, VyrimCoreCommand.PERMISSION_ADMIN_RELOAD)).thenReturn(false);
        when(mockLuckPerms.hasPermission(mockSender, VyrimCoreCommand.PERMISSION_COMMAND_RELOAD)).thenReturn(false);

        boolean result = command.onCommand(mockSender, mockCommand, "vyrimcore", new String[]{"reload"});
        assertTrue(result);

        verify(mockCore, never()).reloadCore();
        verify(mockSender).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("Command sends usage when arguments are invalid or missing")
    void testUsageOnInvalidArgs() {
        when(mockLuckPerms.hasPermission(mockSender, VyrimCoreCommand.PERMISSION_ADMIN_RELOAD)).thenReturn(true);

        assertTrue(command.onCommand(mockSender, mockCommand, "vyrimcore", new String[]{}));
        assertTrue(command.onCommand(mockSender, mockCommand, "vyrimcore", new String[]{"status"}));

        verify(mockCore, never()).reloadCore();
        verify(mockSender, times(2)).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("/vyrimcore reload and /vyrimcore reload all trigger disk reload and full core reload")
    void testReloadAll() {
        when(mockLuckPerms.hasPermission(mockSender, VyrimCoreCommand.PERMISSION_ADMIN_RELOAD)).thenReturn(true);

        // /vyrimcore reload
        boolean result1 = command.onCommand(mockSender, mockCommand, "vyrimcore", new String[]{"reload"});
        assertTrue(result1);

        // /vyrimcore reload all
        boolean result2 = command.onCommand(mockSender, mockCommand, "vyrimcore", new String[]{"reload", "all"});
        assertTrue(result2);

        verify(mockCore, times(2)).reloadConfig();
        verify(mockCore, times(2)).reloadCore();
        verify(mockSender, times(2)).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("/vyrimcore reload <module> reloads matching module after disk refresh")
    void testReloadSpecificModule() {
        when(mockLuckPerms.hasPermission(mockSender, VyrimCoreCommand.PERMISSION_ADMIN_RELOAD)).thenReturn(true);
        Module mockModule = mock(Module.class);
        when(mockModule.name()).thenReturn("BiomeCompass");
        when(mockManager.getModule("BiomeCompass")).thenReturn(Optional.of(mockModule));

        boolean result = command.onCommand(mockSender, mockCommand, "vyrimcore", new String[]{"reload", "BiomeCompass"});
        assertTrue(result);

        verify(mockCore, times(1)).reloadConfig();
        verify(mockManager, times(1)).reload(mockModule);
        verify(mockSender).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("/vyrimcore reload <invalid> reports unrecognized module")
    void testReloadUnrecognizedModule() {
        when(mockLuckPerms.hasPermission(mockSender, VyrimCoreCommand.PERMISSION_ADMIN_RELOAD)).thenReturn(true);
        when(mockManager.getModule("UnknownModule")).thenReturn(Optional.empty());
        when(mockManager.getRegisteredModuleNames()).thenReturn(List.of("BiomeCompass"));

        boolean result = command.onCommand(mockSender, mockCommand, "vyrimcore", new String[]{"reload", "UnknownModule"});
        assertTrue(result);

        verify(mockCore, times(1)).reloadConfig();
        verify(mockManager, never()).reload(any(Module.class));
        verify(mockSender).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("Tab completion suggests 'reload' and 'all' + module names")
    void testTabCompletion() {
        when(mockLuckPerms.hasPermission(mockSender, VyrimCoreCommand.PERMISSION_ADMIN_RELOAD)).thenReturn(true);
        when(mockManager.getRegisteredModuleNames()).thenReturn(List.of("BiomeCompass", "Prefixes"));

        List<String> completions1 = command.onTabComplete(mockSender, mockCommand, "vyrimcore", new String[]{""});
        assertEquals(List.of("reload"), completions1);

        List<String> completions2 = command.onTabComplete(mockSender, mockCommand, "vyrimcore", new String[]{"rel"});
        assertEquals(List.of("reload"), completions2);

        List<String> completions3 = command.onTabComplete(mockSender, mockCommand, "vyrimcore", new String[]{"reload", ""});
        assertEquals(List.of("all", "BiomeCompass", "Prefixes"), completions3);

        List<String> completions4 = command.onTabComplete(mockSender, mockCommand, "vyrimcore", new String[]{"reload", "Bio"});
        assertEquals(List.of("BiomeCompass"), completions4);

        List<String> completions5 = command.onTabComplete(mockSender, mockCommand, "vyrimcore", new String[]{"reload", "al"});
        assertEquals(List.of("all"), completions5);

        // Without permission
        when(mockLuckPerms.hasPermission(mockSender, VyrimCoreCommand.PERMISSION_ADMIN_RELOAD)).thenReturn(false);
        when(mockLuckPerms.hasPermission(mockSender, VyrimCoreCommand.PERMISSION_COMMAND_RELOAD)).thenReturn(false);
        List<String> completionsNoPerm = command.onTabComplete(mockSender, mockCommand, "vyrimcore", new String[]{"reload", ""});
        assertTrue(completionsNoPerm.isEmpty());
    }
}

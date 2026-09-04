package net.vyrim.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.vyrim.core.VyrimCore;
import net.vyrim.core.module.Module;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Administrative command executor and tab completer for /vyrimcore.
 * Supports reloading configuration, hooks, and individual or all modules.
 */
public class VyrimCoreCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION_RELOAD = "vyrimcore.command.reload";

    private final VyrimCore core;

    public VyrimCoreCommand(VyrimCore core) {
        this.core = core;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Permission check using LuckPermsHook with fallback to Bukkit permissible
        if (core.getLuckPermsHook() != null && !core.getLuckPermsHook().hasPermission(sender, PERMISSION_RELOAD)) {
            sender.sendMessage(Component.text("❌ You do not have permission to execute this command.", NamedTextColor.RED));
            return true;
        } else if (core.getLuckPermsHook() == null && !sender.hasPermission(PERMISSION_RELOAD)) {
            sender.sendMessage(Component.text("❌ You do not have permission to execute this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(Component.text("Usage: /" + label + " reload [module]", NamedTextColor.RED));
            return true;
        }

        // /vyrimcore reload (reloads config, hooks, and all modules)
        if (args.length == 1) {
            core.reloadCore();
            sender.sendMessage(Component.text("✔ VyrimCore configuration, hooks, and all modules successfully reloaded.", NamedTextColor.GREEN));
            return true;
        }

        // /vyrimcore reload <module_name>
        String targetModuleName = args[1];
        Optional<Module> moduleOpt = core.getModuleManager().getModule(targetModuleName);

        if (moduleOpt.isEmpty()) {
            List<String> registeredNames = core.getModuleManager().getRegisteredModuleNames();
            String available = registeredNames.isEmpty() ? "none" : String.join(", ", registeredNames);
            sender.sendMessage(Component.text("❌ Unrecognized module: '" + targetModuleName + "'. Registered modules: " + available, NamedTextColor.RED));
            return true;
        }

        Module targetModule = moduleOpt.get();
        core.getModuleManager().reload(targetModule);
        sender.sendMessage(Component.text("✔ Module '" + targetModule.name() + "' successfully reloaded.", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (core.getLuckPermsHook() != null && !core.getLuckPermsHook().hasPermission(sender, PERMISSION_RELOAD)) {
            return Collections.emptyList();
        } else if (core.getLuckPermsHook() == null && !sender.hasPermission(PERMISSION_RELOAD)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], List.of("reload"), new ArrayList<>());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("reload")) {
            return StringUtil.copyPartialMatches(args[1], core.getModuleManager().getRegisteredModuleNames(), new ArrayList<>());
        }

        return Collections.emptyList();
    }
}

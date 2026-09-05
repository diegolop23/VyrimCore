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

    public static final String PERMISSION_ADMIN_RELOAD = "vyrimcore.admin.reload";
    public static final String PERMISSION_COMMAND_RELOAD = "vyrimcore.command.reload";

    private final VyrimCore core;

    public VyrimCoreCommand(VyrimCore core) {
        this.core = core;
    }

    private boolean hasPermission(CommandSender sender) {
        if (core == null) {
            return sender.hasPermission(PERMISSION_ADMIN_RELOAD) || sender.hasPermission(PERMISSION_COMMAND_RELOAD);
        }
        if (core.getLuckPermsHook() != null) {
            return core.getLuckPermsHook().hasPermission(sender, PERMISSION_ADMIN_RELOAD)
                    || core.getLuckPermsHook().hasPermission(sender, PERMISSION_COMMAND_RELOAD);
        }
        return sender.hasPermission(PERMISSION_ADMIN_RELOAD) || sender.hasPermission(PERMISSION_COMMAND_RELOAD);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage(Component.text("❌ You do not have permission to execute this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /" + label + " reload [all|<module>] or /" + label + " <module> reload", NamedTextColor.RED));
            return true;
        }

        // Disk Refresh: Always refresh config from disk at the very beginning of reload routine
        core.reloadConfig();

        // Check for: /vyrim <module> reload
        if (args.length == 2 && args[1].equalsIgnoreCase("reload")) {
            String targetModuleName = args[0];
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

        if (!args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(Component.text("Usage: /" + label + " reload [all|<module>] or /" + label + " <module> reload", NamedTextColor.RED));
            return true;
        }

        // /vyrimcore reload OR /vyrimcore reload all
        if (args.length == 1 || (args.length == 2 && args[1].equalsIgnoreCase("all"))) {
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
        if (!hasPermission(sender)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("reload");
            if (alias.equalsIgnoreCase("vyrim")) {
                options.addAll(core.getModuleManager().getRegisteredModuleNames());
            }
            return StringUtil.copyPartialMatches(args[0], options, new ArrayList<>());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("reload")) {
                List<String> options = new ArrayList<>();
                options.add("all");
                options.addAll(core.getModuleManager().getRegisteredModuleNames());
                return StringUtil.copyPartialMatches(args[1], options, new ArrayList<>());
            } else if (core.getModuleManager().getModule(args[0]).isPresent()) {
                return StringUtil.copyPartialMatches(args[1], List.of("reload"), new ArrayList<>());
            }
        }

        return Collections.emptyList();
    }
}

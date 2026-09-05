package net.vyrim.core.hook;

import net.vyrim.core.VyrimCore;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * Encapsulates LuckPerms API interactions for permission evaluation.
 * Safely falls back to Bukkit's native Permissible#hasPermission if LuckPerms
 * is not installed, disabled, or fails to resolve a player.
 */
public class LuckPermsHook {

    private final VyrimCore core;
    private boolean available;
    private LuckPermsDelegate delegate;

    public LuckPermsHook(VyrimCore core) {
        this.core = core;
        this.available = checkAvailability();
    }

    /**
     * Checks if LuckPerms is present and initializes the provider delegate safely.
     *
     * @return true if successfully hooked into LuckPerms
     */
    public boolean checkAvailability() {
        try {
            if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null || !Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
                this.delegate = null;
                this.available = false;
                return false;
            }
        } catch (Throwable t) {
            this.delegate = null;
            this.available = false;
            return false;
        }

        try {
            this.delegate = new LuckPermsDelegate();
            this.available = true;
            if (core != null && core.getLogger() != null) {
                core.getLogger().info("[LuckPermsHook] Successfully hooked into LuckPerms API.");
            }
            return true;
        } catch (Throwable t) {
            if (core != null && core.getLogger() != null) {
                core.getLogger().log(Level.WARNING, "[LuckPermsHook] Failed to initialize LuckPerms provider: " + t.getMessage(), t);
            }
            this.delegate = null;
            this.available = false;
            return false;
        }
    }

    /**
     * Returns whether the LuckPerms hook is active.
     */
    public boolean isAvailable() {
        return available && delegate != null;
    }

    /**
     * Queries whether a player holds a given permission node.
     * Evaluates against LuckPerms cached permission data when available,
     * falling back to Bukkit permissible checks.
     *
     * @param player     the player to check
     * @param permission the permission node
     * @return true if the player holds the permission, false otherwise
     */
    public boolean hasPermission(Player player, String permission) {
        if (player == null || permission == null) {
            return false;
        }

        if (isAvailable()) {
            try {
                return delegate.hasPermission(player, permission);
            } catch (Throwable t) {
                if (core != null && core.getLogger() != null) {
                    core.getLogger().warning("[LuckPermsHook] Failed evaluating LuckPerms permission (" + permission
                            + ") for " + player.getName() + ", falling back to Bukkit: " + t.getMessage());
                }
            }
        }

        return player.hasPermission(permission);
    }

    /**
     * Queries whether a command sender holds a given permission node.
     * Delegates to LuckPerms for players, and Bukkit native permissible for others.
     *
     * @param sender     the sender to check
     * @param permission the permission node
     * @return true if permitted, false otherwise
     */
    public boolean hasPermission(CommandSender sender, String permission) {
        if (sender == null || permission == null) {
            return false;
        }
        if (sender instanceof Player player) {
            return hasPermission(player, permission);
        }
        return sender.hasPermission(permission);
    }

    /**
     * Closes and clears references to the LuckPerms provider.
     */
    public void close() {
        this.delegate = null;
        this.available = false;
    }

    /**
     * Isolated delegate class preventing class verification and NoClassDefFoundError
     * when the LuckPerms plugin is absent at runtime.
     */
    private static class LuckPermsDelegate {
        private final net.luckperms.api.LuckPerms luckPerms;

        public LuckPermsDelegate() {
            this.luckPerms = net.luckperms.api.LuckPermsProvider.get();
        }

        public boolean hasPermission(Player player, String permission) {
            try {
                net.luckperms.api.platform.PlayerAdapter<Player> adapter = luckPerms.getPlayerAdapter(Player.class);
                net.luckperms.api.cacheddata.CachedPermissionData permissionData = adapter.getPermissionData(player);
                net.luckperms.api.util.Tristate tristate = permissionData.checkPermission(permission);
                if (tristate != net.luckperms.api.util.Tristate.UNDEFINED) {
                    return tristate.asBoolean();
                }
            } catch (Throwable ignored) {
            }

            try {
                net.luckperms.api.model.user.User user = luckPerms.getUserManager().getUser(player.getUniqueId());
                if (user != null) {
                    net.luckperms.api.query.QueryOptions queryOptions = luckPerms.getContextManager().getQueryOptions(player);
                    net.luckperms.api.util.Tristate tristate = user.getCachedData().getPermissionData(queryOptions).checkPermission(permission);
                    if (tristate != net.luckperms.api.util.Tristate.UNDEFINED) {
                        return tristate.asBoolean();
                    }
                }
            } catch (Throwable ignored) {
            }

            return player.hasPermission(permission);
        }
    }
}

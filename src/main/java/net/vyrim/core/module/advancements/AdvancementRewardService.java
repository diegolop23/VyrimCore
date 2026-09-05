package net.vyrim.core.module.advancements;

import net.vyrim.core.VyrimCore;
import net.vyrim.core.hook.LuckPermsHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Service responsible for dispatching console commands and granting permissions
 * upon advancement unlocks.
 */
public class AdvancementRewardService {

    private final VyrimCore core;
    private final Supplier<AdvancementLoader> loaderSupplier;

    public AdvancementRewardService(VyrimCore core, Supplier<AdvancementLoader> loaderSupplier) {
        this.core = Objects.requireNonNull(core, "VyrimCore cannot be null");
        this.loaderSupplier = loaderSupplier;
    }

    /**
     * Dispatches configured rewards for the specified advancement ID to the player.
     *
     * @param player        the player who unlocked the advancement
     * @param advancementId the ID of the unlocked advancement
     */
    public void grantRewards(Player player, String advancementId) {
        if (player == null || advancementId == null) {
            return;
        }

        AdvancementLoader loader = loaderSupplier != null ? loaderSupplier.get() : null;
        if (loader == null) {
            return;
        }

        AdvancementRewardData reward = loader.getRewardDataMap().get(advancementId);
        if (reward != null) {
            grantRewards(player, reward);
        }
    }

    /**
     * Dispatches the given reward data to the player.
     * Supports either command, permission, both, or neither.
     *
     * @param player the player to receive the reward
     * @param reward the reward data
     */
    public void grantRewards(Player player, AdvancementRewardData reward) {
        if (player == null || reward == null) {
            return;
        }

        // 1. Dispatch console command if present
        if (reward.hasCommand()) {
            String formattedCommand = reward.command().replace("%player%", player.getName());
            Runnable commandTask = () -> {
                try {
                    if (Bukkit.getServer() != null) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCommand);
                    }
                } catch (Throwable t) {
                    if (core.getLogger() != null) {
                        core.getLogger().warning("[Advancements] Failed executing reward command '"
                                + formattedCommand + "' for " + player.getName() + ": " + t.getMessage());
                    }
                }
            };

            if (Bukkit.getServer() != null) {
                if (Bukkit.isPrimaryThread()) {
                    commandTask.run();
                } else if (Bukkit.getScheduler() != null) {
                    Bukkit.getScheduler().runTask(core, commandTask);
                }
            }
        }

        // 2. Grant permission node via LuckPermsHook if present
        if (reward.hasPermission()) {
            String permission = reward.permission();
            LuckPermsHook luckPerms = core.getLuckPermsHook();
            if (luckPerms != null && luckPerms.isAvailable()) {
                boolean granted = luckPerms.grantPermission(player, permission);
                if (!granted && core.getLogger() != null) {
                    core.getLogger().warning("[Advancements] Failed to grant permission '"
                            + permission + "' to " + player.getName() + " via LuckPerms.");
                }
            } else {
                if (core.getLogger() != null) {
                    core.getLogger().warning("[Advancements] Cannot grant permission '" + permission
                            + "' to " + player.getName() + ": LuckPerms is not available.");
                }
            }
        }
    }
}

package net.vyrim.core.module.advancements;

/**
 * Immutable record representing the reward block of an advancement.
 *
 * @param command    the console command template to execute (%player% replaced), or null
 * @param permission the permission node to grant via LuckPerms, or null
 */
public record AdvancementRewardData(
        String command,
        String permission
) {
    public boolean hasCommand() {
        return command != null && !command.isBlank();
    }

    public boolean hasPermission() {
        return permission != null && !permission.isBlank();
    }
}

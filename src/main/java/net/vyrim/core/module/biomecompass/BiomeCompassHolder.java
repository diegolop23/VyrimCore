package net.vyrim.core.module.biomecompass;

import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Custom InventoryHolder representing the BiomeCompass GUI state.
 * Retains no hard reference to Player instances to prevent memory leaks.
 */
public class BiomeCompassHolder implements InventoryHolder {

    private final UUID playerUuid;
    private final World.Environment environment;
    private int currentPage;
    private int totalPages;
    private Inventory inventory;

    public BiomeCompassHolder(UUID playerUuid, World.Environment environment, int currentPage) {
        this.playerUuid = playerUuid;
        this.environment = environment;
        this.currentPage = currentPage;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public World.Environment getEnvironment() {
        return environment;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}

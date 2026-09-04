package net.vyrim.core.hook;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.skill.handler.SkillHandler;
import net.Indyuce.mmoitems.api.event.MMOItemsReloadEvent;
import net.vyrim.core.VyrimCore;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.PluginManager;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Handles soft-dependency detection, integration, and automatic re-registration
 * of abilities upon MMOItems and MythicLib reload events or admin commands.
 */
public class MMOItemsHook implements Listener {

    private final VyrimCore core;
    private final Set<SkillHandler<?>> registeredHandlers = ConcurrentHashMap.newKeySet();
    private boolean available;
    private Listener mmoItemsListener;

    public MMOItemsHook(VyrimCore core) {
        this.core = core;
        this.available = checkAvailability();

        // Register command and plugin reload listeners
        Bukkit.getPluginManager().registerEvents(this, core);

        // Register dedicated MMOItemsReloadEvent listener if MMOItems is present
        registerReloadListenerIfPossible();
    }

    /**
     * Checks if both MMOItems and MythicLib plugins are installed and enabled.
     *
     * @return true if both plugins are active
     */
    public boolean checkAvailability() {
        PluginManager pm = Bukkit.getPluginManager();
        boolean mmoitems = pm.isPluginEnabled("MMOItems");
        boolean mythiclib = pm.isPluginEnabled("MythicLib");
        this.available = mmoitems && mythiclib;
        return this.available;
    }

    /**
     * Returns true if MMOItems and MythicLib are currently available.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Registers a custom SkillHandler directly through MythicLib's skill registry.
     * Keeps track of registered handlers to allow instant re-registration on reload.
     *
     * @param skillHandler the MythicLib skill handler to register
     * @return true if successfully registered, false otherwise
     */
    public boolean registerSkill(SkillHandler<?> skillHandler) {
        if (skillHandler == null) {
            return false;
        }
        registeredHandlers.add(skillHandler);

        if (!isAvailable()) {
            core.getLogger().warning("[MMOItemsHook] Cannot register skill '" + skillHandler.getId()
                    + "': MMOItems or MythicLib is not enabled.");
            return false;
        }

        try {
            MythicLib.plugin.getSkills().registerSkillHandler(skillHandler);
            core.getLogger().info("[MMOItemsHook] Successfully registered ability directly in MythicLib: " + skillHandler.getId());
            return true;
        } catch (Exception ex) {
            core.getLogger().log(Level.SEVERE, "[MMOItemsHook] Failed to register ability: " + skillHandler.getId(), ex);
            return false;
        }
    }

    /**
     * Unregisters a custom SkillHandler from MythicLib's skill registry.
     *
     * @param skillHandler the skill handler to unregister
     */
    public void unregisterSkill(SkillHandler<?> skillHandler) {
        if (skillHandler != null) {
            registeredHandlers.remove(skillHandler);
        }
        if (!isAvailable() || skillHandler == null) {
            return;
        }

        try {
            MythicLib.plugin.getSkills().getHandlers().remove(skillHandler);
            core.getLogger().info("[MMOItemsHook] Unregistered ability: " + skillHandler.getId());
        } catch (Exception ignored) {
        }
    }

    /**
     * Re-registers all tracked custom SkillHandlers into MythicLib.
     */
    public void reRegisterAll() {
        if (!checkAvailability()) {
            return;
        }

        for (SkillHandler<?> handler : registeredHandlers) {
            try {
                MythicLib.plugin.getSkills().registerSkillHandler(handler);
                core.getLogger().info("[MMOItemsHook] Re-registered ability on reload: " + handler.getId());
            } catch (Exception ex) {
                core.getLogger().log(Level.SEVERE, "[MMOItemsHook] Failed to re-register ability on reload: " + handler.getId(), ex);
            }
        }
    }

    /**
     * Safely registers the MMOItemsReloadEvent listener without classloader errors
     * if MMOItems is not installed.
     */
    private void registerReloadListenerIfPossible() {
        if (mmoItemsListener == null && Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            try {
                this.mmoItemsListener = new MMOItemsReloadListener();
                Bukkit.getPluginManager().registerEvents(mmoItemsListener, core);
                core.getLogger().info("[MMOItemsHook] Registered MMOItemsReloadEvent listener.");
            } catch (Throwable t) {
                core.getLogger().warning("[MMOItemsHook] Could not register MMOItemsReloadListener: " + t.getMessage());
            }
        }
    }

    /**
     * Listens for player commands triggering plugin reloads.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        handleReloadCommand(event.getMessage());
    }

    /**
     * Listens for console commands triggering plugin reloads.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        handleReloadCommand(event.getCommand());
    }

    /**
     * Listens for plugin enablement (e.g. via PlugMan or hot reload).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        String name = event.getPlugin().getName();
        if ("MMOItems".equals(name) || "MythicLib".equals(name)) {
            checkAvailability();
            registerReloadListenerIfPossible();
            Bukkit.getScheduler().runTask(core, this::reRegisterAll);
        }
    }

    private void handleReloadCommand(String rawCommand) {
        if (rawCommand == null) {
            return;
        }
        String cmd = rawCommand.trim().toLowerCase();
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }

        if (cmd.equals("ml reload") || cmd.startsWith("ml reload ")
                || cmd.equals("mythiclib reload") || cmd.startsWith("mythiclib reload ")
                || cmd.equals("mi reload") || cmd.startsWith("mi reload ")
                || cmd.equals("mmoitems reload") || cmd.startsWith("mmoitems reload ")) {
            core.getLogger().info("[MMOItemsHook] Detected reload command ('" + rawCommand + "'). Scheduling ability re-registration...");
            // Run 1 tick later so MythicLib/MMOItems completes its reload and cache invalidation first
            Bukkit.getScheduler().runTask(core, this::reRegisterAll);
        }
    }

    /**
     * Unregisters all event listeners and clears state.
     */
    public void close() {
        if (mmoItemsListener != null) {
            HandlerList.unregisterAll(mmoItemsListener);
            mmoItemsListener = null;
        }
        HandlerList.unregisterAll(this);
    }

    /**
     * Separate listener class for MMOItemsReloadEvent to prevent NoClassDefFoundError
     * when MMOItems is absent at server startup.
     */
    private class MMOItemsReloadListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR)
        public void onMMOItemsReload(MMOItemsReloadEvent event) {
            core.getLogger().info("[MMOItemsHook] Detected MMOItemsReloadEvent. Re-registering abilities...");
            reRegisterAll();
        }
    }
}

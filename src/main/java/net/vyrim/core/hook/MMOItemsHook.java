package net.vyrim.core.hook;

import io.lumine.mythic.lib.skill.handler.SkillHandler;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.skill.RegisteredSkill;
import net.vyrim.core.VyrimCore;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;

import java.util.logging.Level;

/**
 * Handles soft-dependency detection and integration with MMOItems and MythicLib.
 */
public class MMOItemsHook {

    private final VyrimCore core;
    private boolean available;

    public MMOItemsHook(VyrimCore core) {
        this.core = core;
        this.available = checkAvailability();
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
     * Safely registers a custom SkillHandler as an MMOItems RegisteredSkill.
     *
     * @param skillHandler the MythicLib skill handler to register
     * @return true if successfully registered, false otherwise
     */
    public boolean registerSkill(SkillHandler<?> skillHandler) {
        if (!isAvailable()) {
            core.getLogger().warning("[MMOItemsHook] Cannot register skill '" + skillHandler.getId()
                    + "': MMOItems or MythicLib is not enabled.");
            return false;
        }

        try {
            RegisteredSkill registeredSkill = new RegisteredSkill(skillHandler);
            MMOItems.plugin.getSkills().registerSkill(registeredSkill);
            core.getLogger().info("[MMOItemsHook] Successfully registered ability: " + skillHandler.getId());
            return true;
        } catch (Exception ex) {
            core.getLogger().log(Level.SEVERE, "[MMOItemsHook] Failed to register ability: " + skillHandler.getId(), ex);
            return false;
        }
    }
}

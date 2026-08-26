package net.vyrim.core.module;

import net.vyrim.core.VyrimCore;

import java.util.ArrayList;
import java.util.List;

public final class ModuleManager {

    private final VyrimCore core;
    private final List<Module> registered = new ArrayList<>();
    private final List<Module> enabled = new ArrayList<>();

    public ModuleManager(VyrimCore core) {
        this.core = core;
    }

    public void register(Module module) {
        registered.add(module);
    }

    public void enableAll() {
        for (Module module : registered) {
            if (!module.isAvailable(core)) {
                core.getLogger().warning("[Modules] Skipping '" + module.name() + "': missing dependency.");
                continue;
            }
            try {
                module.onEnable(core);
                enabled.add(module);
                core.getLogger().info("[Modules] Enabled '" + module.name() + "'.");
            } catch (Exception ex) {
                core.getLogger().severe("[Modules] Failed to enable '" + module.name() + "': " + ex.getMessage());
            }
        }
    }

    public void disableAll() {
        for (Module module : enabled) {
            try {
                module.onDisable();
            } catch (Exception ex) {
                core.getLogger().severe("[Modules] Failed to disable '" + module.name() + "': " + ex.getMessage());
            }
        }
        enabled.clear();
    }
}
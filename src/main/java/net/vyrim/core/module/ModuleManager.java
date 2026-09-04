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

    public List<Module> getRegisteredModules() {
        return java.util.Collections.unmodifiableList(registered);
    }

    public List<Module> getEnabledModules() {
        return java.util.Collections.unmodifiableList(enabled);
    }

    public List<String> getRegisteredModuleNames() {
        List<String> names = new ArrayList<>();
        for (Module m : registered) {
            names.add(m.name());
        }
        return java.util.Collections.unmodifiableList(names);
    }

    public java.util.Optional<Module> getModule(String name) {
        if (name == null) {
            return java.util.Optional.empty();
        }
        for (Module module : registered) {
            if (module.name().equalsIgnoreCase(name)) {
                return java.util.Optional.of(module);
            }
        }
        return java.util.Optional.empty();
    }

    public void enableAll() {
        for (Module module : registered) {
            if (enabled.contains(module)) {
                continue;
            }
            if (!module.isAvailable(core)) {
                core.getLogger().warning("[Modules] Skipping '" + module.name() + "': missing dependency or disabled.");
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
        for (Module module : new ArrayList<>(enabled)) {
            try {
                module.onDisable();
            } catch (Exception ex) {
                core.getLogger().severe("[Modules] Failed to disable '" + module.name() + "': " + ex.getMessage());
            }
        }
        enabled.clear();
    }

    public void reload(Module module) {
        if (module == null) {
            return;
        }
        try {
            module.reload(core);
            if (module.isEnabled()) {
                if (!enabled.contains(module)) {
                    enabled.add(module);
                }
                core.getLogger().info("[Modules] Reloaded and enabled '" + module.name() + "'.");
            } else {
                enabled.remove(module);
                core.getLogger().info("[Modules] Reloaded and disabled '" + module.name() + "'.");
            }
        } catch (Exception ex) {
            core.getLogger().severe("[Modules] Failed to reload '" + module.name() + "': " + ex.getMessage());
        }
    }

    public boolean reload(String name) {
        java.util.Optional<Module> moduleOpt = getModule(name);
        if (moduleOpt.isPresent()) {
            reload(moduleOpt.get());
            return true;
        }
        return false;
    }

    public void reloadAll() {
        for (Module module : registered) {
            reload(module);
        }
        core.getLogger().info("[Modules] All registered modules reloaded.");
    }
}
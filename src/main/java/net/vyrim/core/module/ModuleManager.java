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
            if (module.isEnabled()) {
                if (!enabled.contains(module)) {
                    enabled.add(module);
                }
                continue;
            }
            if (!module.isConfigEnabled(core) || !module.isAvailable(core)) {
                core.getLogger().warning("[Modules] Skipping '" + module.name() + "': disabled in config or missing dependency.");
                continue;
            }
            try {
                module.enable(core);
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
                module.disable();
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
        boolean configEnabled = module.isConfigEnabled(core);
        boolean currentlyEnabled = module.isEnabled();

        try {
            if (configEnabled && !currentlyEnabled) {
                if (!module.isAvailable(core)) {
                    core.getLogger().warning("[Modules] Cannot enable '" + module.name() + "': missing dependency or unavailable.");
                    return;
                }
                module.enable(core);
                if (!enabled.contains(module)) {
                    enabled.add(module);
                }
                core.getLogger().info("[Modules] Enabled '" + module.name() + "' on reload.");
            } else if (!configEnabled && currentlyEnabled) {
                module.disable();
                enabled.remove(module);
                core.getLogger().info("[Modules] Disabled '" + module.name() + "' on reload (disabled in config).");
            } else if (configEnabled && currentlyEnabled) {
                module.reload(core);
                if (module.isEnabled()) {
                    if (!enabled.contains(module)) {
                        enabled.add(module);
                    }
                    core.getLogger().info("[Modules] Reloaded active configuration for '" + module.name() + "'.");
                } else {
                    enabled.remove(module);
                    core.getLogger().info("[Modules] Reloaded and disabled '" + module.name() + "'.");
                }
            } else {
                core.getLogger().info("[Modules] Module '" + module.name() + "' remains disabled.");
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
        core.getLogger().info("[Modules] All registered modules synchronized and reloaded.");
    }
}
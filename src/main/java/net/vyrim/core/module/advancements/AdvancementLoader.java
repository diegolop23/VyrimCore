package net.vyrim.core.module.advancements;

import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI;
import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.RootAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementFrameType;
import net.md_5.bungee.api.ChatColor;
import net.vyrim.core.VyrimCore;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The primary loader responsible for reading advancements YAML configuration,
 * building the advancement dependency graph, topologically sorting advancements,
 * computing grid coordinates, and creating UltimateAdvancementAPI objects.
 */
public class AdvancementLoader {

    public static final String DEFAULT_BACKGROUND = "textures/block/stone.png";

    private final VyrimCore core;
    private final UltimateAdvancementAPI api;

    private final Map<String, AdvancementTriggerData> triggerDataMap = new ConcurrentHashMap<>();
    private final Map<String, AdvancementRewardData> rewardDataMap = new ConcurrentHashMap<>();
    private final Map<String, AdvancementTab> registeredTabs = new HashMap<>();
    private final Map<String, Advancement> advancementMap = new ConcurrentHashMap<>();

    public AdvancementLoader(VyrimCore core, UltimateAdvancementAPI api) {
        this.core = Objects.requireNonNull(core, "VyrimCore cannot be null");
        this.api = Objects.requireNonNull(api, "UltimateAdvancementAPI cannot be null");
    }

    /**
     * Getter exposing raw trigger data by advancement ID.
     */
    public Map<String, AdvancementTriggerData> getTriggerDataMap() {
        return Collections.unmodifiableMap(triggerDataMap);
    }

    /**
     * Package-private getter exposing raw reward data by advancement ID.
     */
    Map<String, AdvancementRewardData> getRewardDataMap() {
        return Collections.unmodifiableMap(rewardDataMap);
    }

    /**
     * Gets a loaded Advancement by its ID.
     *
     * @param id the advancement ID
     * @return the Advancement, or null if not found
     */
    public Advancement getAdvancement(String id) {
        return advancementMap.get(id);
    }

    /**
     * Returns an unmodifiable map of all loaded Advancements.
     */
    public Map<String, Advancement> getAdvancementMap() {
        return Collections.unmodifiableMap(advancementMap);
    }

    /**
     * Returns the currently registered tabs.
     */
    public Map<String, AdvancementTab> getRegisteredTabs() {
        return Collections.unmodifiableMap(registeredTabs);
    }

    /**
     * Unregisters all active tabs from UltimateAdvancementAPI and clears in-memory maps.
     */
    public void unloadTabs() {
        for (String tabId : new ArrayList<>(registeredTabs.keySet())) {
            try {
                if (api.isAdvancementTabRegistered(tabId)) {
                    api.unregisterAdvancementTab(tabId);
                }
            } catch (Throwable t) {
                if (core.getLogger() != null) {
                    core.getLogger().log(Level.WARNING, "[Advancements] Error unregistering tab '" + tabId + "': " + t.getMessage(), t);
                }
            }
        }
        registeredTabs.clear();
        triggerDataMap.clear();
        rewardDataMap.clear();
        advancementMap.clear();
    }

    /**
     * Parses the given configuration and loads all tabs and advancements.
     *
     * @param config the YAML configuration (typically advancements.yml)
     */
    public void loadTabs(FileConfiguration config) {
        if (config == null) {
            return;
        }

        ConfigurationSection tabsSection = config.getConfigurationSection("tabs");
        if (tabsSection == null) {
            core.getLogger().warning("[Advancements] No 'tabs' section found in advancements configuration.");
            return;
        }

        for (String tabId : tabsSection.getKeys(false)) {
            ConfigurationSection tabSection = tabsSection.getConfigurationSection(tabId);
            if (tabSection == null) {
                continue;
            }

            try {
                loadTab(tabId, tabSection);
            } catch (Throwable t) {
                core.getLogger().log(Level.SEVERE, "[Advancements] Unexpected error loading tab '" + tabId + "': " + t.getMessage(), t);
            }
        }
    }

    private void loadTab(String tabId, ConfigurationSection tabSection) {
        String backgroundTexture = tabSection.getString("background", DEFAULT_BACKGROUND);
        if (backgroundTexture == null || backgroundTexture.isBlank()) {
            backgroundTexture = DEFAULT_BACKGROUND;
        }

        String tabIcon = tabSection.getString("icon", null);
        String tabIconFallback = tabSection.getString("icon_fallback", null);

        // Support either tabs.<tabId>.advancements or direct children under tabs.<tabId>
        ConfigurationSection advsSection = tabSection.getConfigurationSection("advancements");
        if (advsSection == null) {
            advsSection = tabSection;
        }

        Map<String, RawAdvancementDefinition> definitions = new LinkedHashMap<>();
        for (String advKey : advsSection.getKeys(false)) {
            // Ignore reserved tab metadata keys
            if (advKey.equalsIgnoreCase("background") || advKey.equalsIgnoreCase("title")
                    || advKey.equalsIgnoreCase("icon") || advKey.equalsIgnoreCase("icon_fallback")) {
                continue;
            }
            ConfigurationSection advSec = advsSection.getConfigurationSection(advKey);
            if (advSec == null) {
                continue;
            }

            RawAdvancementDefinition def = parseDefinition(advKey, advSec, tabIcon, tabIconFallback);
            definitions.put(advKey, def);
        }

        if (definitions.isEmpty()) {
            core.getLogger().warning("[Advancements] Tab '" + tabId + "' has no valid advancements defined. Skipping.");
            return;
        }

        // 1. Validate parent references (unknown parent check)
        for (RawAdvancementDefinition def : definitions.values()) {
            if (def.parentId() != null && !def.parentId().isBlank()) {
                if (!definitions.containsKey(def.parentId())) {
                    core.getLogger().severe("[Advancements] Tab '" + tabId + "' skipped: Advancement '"
                            + def.id() + "' references unknown parent '" + def.parentId() + "'.");
                    return;
                }
            }
        }

        // 2. Validate root advancement (must have exactly one root)
        List<String> rootIds = new ArrayList<>();
        for (RawAdvancementDefinition def : definitions.values()) {
            if (def.parentId() == null || def.parentId().isBlank()) {
                rootIds.add(def.id());
            }
        }

        if (rootIds.isEmpty()) {
            core.getLogger().severe("[Advancements] Tab '" + tabId
                    + "' skipped: No root advancement found (every advancement specifies a parent, cycle likely).");
            return;
        }

        if (rootIds.size() > 1) {
            core.getLogger().severe("[Advancements] Tab '" + tabId
                    + "' skipped: Multiple root advancements found: " + rootIds + ". Each tab must have exactly one root.");
            return;
        }

        // 3. Detect dependency cycles
        for (RawAdvancementDefinition def : definitions.values()) {
            Set<String> visited = new HashSet<>();
            String current = def.id();
            while (current != null) {
                if (!visited.add(current)) {
                    core.getLogger().severe("[Advancements] Tab '" + tabId
                            + "' skipped: Cycle detected in advancement hierarchy involving '" + current + "'.");
                    return;
                }
                RawAdvancementDefinition currentDef = definitions.get(current);
                current = (currentDef != null) ? currentDef.parentId() : null;
            }
        }

        // 4. Build adjacency tree and topologically sort (parents before children)
        Map<String, List<String>> childrenMap = new HashMap<>();
        for (String id : definitions.keySet()) {
            childrenMap.put(id, new ArrayList<>());
        }
        for (RawAdvancementDefinition def : definitions.values()) {
            if (def.parentId() != null && !def.parentId().isBlank()) {
                childrenMap.get(def.parentId()).add(def.id());
            }
        }

        String rootId = rootIds.get(0);
        List<RawAdvancementDefinition> sortedDefs = new ArrayList<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(rootId);

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            sortedDefs.add(definitions.get(currentId));
            List<String> children = childrenMap.get(currentId);
            if (children != null) {
                queue.addAll(children);
            }
        }

        if (sortedDefs.size() != definitions.size()) {
            core.getLogger().severe("[Advancements] Tab '" + tabId
                    + "' skipped: Disconnected or orphaned advancements detected.");
            return;
        }

        // 5. Layout coordinates: bypass auto-layout if all nodes are manual, otherwise compute and check collisions
        Map<String, Float> xCoords = new HashMap<>();
        Map<String, Float> yCoords = new HashMap<>();

        boolean allManual = definitions.values().stream().allMatch(RawAdvancementDefinition::hasManualCoords);
        if (allManual) {
            for (RawAdvancementDefinition def : definitions.values()) {
                xCoords.put(def.id(), def.manualX());
                yCoords.put(def.id(), def.manualY());
            }
        } else {
            computeCoordinates(rootId, 0, definitions, childrenMap, xCoords, yCoords, new float[]{0.0f});
            detectCollisions(tabId, definitions, xCoords, yCoords, core != null ? core.getLogger() : null);
        }

        // 6. Create tab in UltimateAdvancementAPI
        AdvancementTab tab;
        try {
            if (api.isAdvancementTabRegistered(tabId)) {
                api.unregisterAdvancementTab(tabId);
            }
            tab = api.createAdvancementTab(tabId);
        } catch (Throwable t) {
            core.getLogger().log(Level.SEVERE, "[Advancements] Failed to initialize AdvancementTab '" + tabId + "': " + t.getMessage(), t);
            return;
        }

        // 7. Instantiate RootAdvancement and BaseAdvancements
        Map<String, Advancement> builtAdvancements = new HashMap<>();
        RootAdvancement rootAdvancement = null;
        Set<BaseAdvancement> childAdvancements = new LinkedHashSet<>();

        for (RawAdvancementDefinition def : sortedDefs) {
            float x = def.hasManualCoords() ? def.manualX() : xCoords.getOrDefault(def.id(), 0.0f);
            float y = def.hasManualCoords() ? def.manualY() : yCoords.getOrDefault(def.id(), 0.0f);

            AdvancementDisplay display = createDisplay(def, x, y);

            // Store raw trigger and reward data
            triggerDataMap.put(def.id(), def.trigger());
            rewardDataMap.put(def.id(), def.reward());

            int maxProgression = Math.max(1, def.trigger().amount());

            if (def.parentId() == null || def.parentId().isBlank()) {
                rootAdvancement = createRootAdvancement(tab, def, display, backgroundTexture, maxProgression);
                builtAdvancements.put(def.id(), rootAdvancement);
            } else {
                Advancement parentAdv = builtAdvancements.get(def.parentId());
                BaseAdvancement baseAdv = createBaseAdvancement(def, display, parentAdv, maxProgression);
                builtAdvancements.put(def.id(), baseAdv);
                childAdvancements.add(baseAdv);
            }
        }

        // 8. Register advancements on tab and show to players
        if (rootAdvancement != null) {
            tab.registerAdvancements(rootAdvancement, childAdvancements);
            tab.automaticallyShowToPlayers();
            registeredTabs.put(tabId, tab);
            advancementMap.putAll(builtAdvancements);
            core.getLogger().info("[Advancements] Successfully loaded and registered tab '" + tabId
                    + "' with " + sortedDefs.size() + " advancement(s).");
        }
    }

    /**
     * Computes x/y coordinates using post-order subtree traversal:
     * - y is directly proportional to depth level (unless manually specified)
     * - leaf nodes receive sequential x positions (accounting for manual sibling positions)
     * - parent nodes are horizontally centered over their children (unless manually specified)
     * This strictly prevents any coordinate overlaps across any subtrees.
     */
    public static void computeCoordinates(
            String currentId,
            int depth,
            Map<String, List<String>> childrenMap,
            Map<String, Float> xCoords,
            Map<String, Float> yCoords,
            float[] nextLeafX
    ) {
        computeCoordinates(currentId, depth, Collections.emptyMap(), childrenMap, xCoords, yCoords, nextLeafX);
    }

    /**
     * Computes coordinates with support for manual coordinate overrides and sibling spacing.
     */
    public static void computeCoordinates(
            String currentId,
            int depth,
            Map<String, RawAdvancementDefinition> definitions,
            Map<String, List<String>> childrenMap,
            Map<String, Float> xCoords,
            Map<String, Float> yCoords,
            float[] nextLeafX
    ) {
        RawAdvancementDefinition def = definitions != null ? definitions.get(currentId) : null;
        boolean manual = def != null && def.hasManualCoords();

        if (manual) {
            xCoords.put(currentId, def.manualX());
            yCoords.put(currentId, def.manualY());
        } else {
            yCoords.put(currentId, (float) depth);
        }

        List<String> children = childrenMap.getOrDefault(currentId, Collections.emptyList());
        if (children.isEmpty()) {
            if (manual) {
                // Sibling manual leaf: advance nextLeafX so next auto sibling does not overlap
                nextLeafX[0] = Math.max(nextLeafX[0], def.manualX() + 1.5f);
            } else {
                // Leaf node: allocate next horizontal slot
                xCoords.put(currentId, nextLeafX[0]);
                nextLeafX[0] += 1.5f;
            }
        } else {
            // Internal node: recursively lay out all children first
            for (String childId : children) {
                computeCoordinates(childId, depth + 1, definitions, childrenMap, xCoords, yCoords, nextLeafX);
            }
            if (!manual) {
                // Center parent over children span
                float firstChildX = xCoords.get(children.get(0));
                float lastChildX = xCoords.get(children.get(children.size() - 1));
                xCoords.put(currentId, (firstChildX + lastChildX) / 2.0f);
            }
        }
    }

    /**
     * Detects coordinate collisions between auto-placed and manually-placed nodes in a tab,
     * logging a clear warning if any collision is found.
     */
    public static void detectCollisions(
            String tabId,
            Map<String, RawAdvancementDefinition> definitions,
            Map<String, Float> xCoords,
            Map<String, Float> yCoords,
            Logger logger
    ) {
        if (logger == null || definitions == null) {
            return;
        }

        for (RawAdvancementDefinition def : definitions.values()) {
            if (!def.hasManualCoords()) {
                Float autoX = xCoords.get(def.id());
                Float autoY = yCoords.get(def.id());
                if (autoX == null || autoY == null) {
                    continue;
                }

                for (RawAdvancementDefinition otherDef : definitions.values()) {
                    if (otherDef.hasManualCoords() && !otherDef.id().equals(def.id())) {
                        float manualX = otherDef.manualX();
                        float manualY = otherDef.manualY();
                        if (Math.abs(autoX - manualX) < 0.001f && Math.abs(autoY - manualY) < 0.001f) {
                            logger.warning("[Advancements] Coordinate collision in tab '" + tabId
                                    + "': Auto-placed advancement '" + def.id()
                                    + "' computed position (" + autoX + ", " + autoY
                                    + ") collides with manually-placed advancement '" + otherDef.id() + "'.");
                        }
                    }
                }
            }
        }
    }

    private RawAdvancementDefinition parseDefinition(String id, ConfigurationSection sec) {
        return parseDefinition(id, sec, null, null);
    }

    private RawAdvancementDefinition parseDefinition(String id, ConfigurationSection sec, String tabIcon, String tabIconFallback) {
        String parent = sec.getString("parent");
        if (parent != null && parent.isBlank()) {
            parent = null;
        }

        String title = sec.getString("title", id);
        List<String> description = sec.isList("description")
                ? sec.getStringList("description")
                : Collections.singletonList(sec.getString("description", ""));

        // Icon resolution with tab-level inheritance for root advancement
        String defaultIcon = (parent == null && tabIcon != null) ? tabIcon : "STONE";
        String icon = sec.getString("icon", defaultIcon);

        String defaultFallback = (parent == null) ? tabIconFallback : null;
        String iconFallback = sec.getString("icon_fallback", defaultFallback);

        // Manual coordinates
        Float manualX = sec.contains("x") ? (float) sec.getDouble("x") : null;
        Float manualY = sec.contains("y") ? (float) sec.getDouble("y") : null;

        String frameStr = sec.getString("frame", "TASK");
        boolean toast = sec.getBoolean("toast", true);
        boolean announce = sec.getBoolean("announce", true);

        // Trigger block
        String triggerType = sec.getString("trigger.type", "JOIN_SERVER");
        String triggerTarget = sec.getString("trigger.target", null);
        int triggerAmount = sec.getInt("trigger.amount", 1);
        AdvancementTriggerData trigger = new AdvancementTriggerData(triggerType, triggerTarget, triggerAmount);

        // Reward block
        String rewardCommand = sec.getString("reward.command", null);
        String rewardPermission = sec.getString("reward.permission", null);
        AdvancementRewardData reward = new AdvancementRewardData(rewardCommand, rewardPermission);

        return new RawAdvancementDefinition(id, parent, title, description, icon, iconFallback, manualX, manualY, frameStr, toast, announce, trigger, reward);
    }

    @SuppressWarnings("deprecation")
    protected AdvancementDisplay createDisplay(RawAdvancementDefinition def, float x, float y) {
        ItemStack iconItem = createIconItem(def);

        // Translate Title & Description Color Codes
        String coloredTitle = ChatColor.translateAlternateColorCodes('&', def.title());
        List<String> coloredDesc = new ArrayList<>();
        for (String line : def.description()) {
            coloredDesc.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        // Parse Frame Type
        AdvancementFrameType frameType;
        try {
            frameType = AdvancementFrameType.valueOf(def.frame().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            frameType = AdvancementFrameType.TASK;
        }

        return new AdvancementDisplay(iconItem, coloredTitle, frameType, def.toast(), def.announce(), x, y, coloredDesc);
    }

    protected RootAdvancement createRootAdvancement(AdvancementTab tab, RawAdvancementDefinition def, AdvancementDisplay display, String backgroundTexture, int maxProgression) {
        return new RootAdvancement(tab, def.id(), display, backgroundTexture, maxProgression);
    }

    protected BaseAdvancement createBaseAdvancement(RawAdvancementDefinition def, AdvancementDisplay display, Advancement parentAdv, int maxProgression) {
        return new BaseAdvancement(def.id(), display, parentAdv, maxProgression);
    }

    protected ItemStack createIconItem(RawAdvancementDefinition def) {
        Material fallback = AdvancementIconResolver.resolveFallbackMaterial(
                def.iconFallback(),
                core != null ? core.getLogger() : null
        );
        return AdvancementIconResolver.resolveIcon(
                def.icon(),
                fallback,
                core != null ? core.getLogger() : null,
                this::createIconItem
        );
    }

    protected ItemStack createIconItem(Material material) {
        return new ItemStack(material);
    }

    /**
     * Internal container representing an unverified advancement configuration node.
     */
    public static record RawAdvancementDefinition(
            String id,
            String parentId,
            String title,
            List<String> description,
            String icon,
            String iconFallback,
            Float manualX,
            Float manualY,
            String frame,
            boolean toast,
            boolean announce,
            AdvancementTriggerData trigger,
            AdvancementRewardData reward
    ) {
        public boolean hasManualCoords() {
            return manualX != null && manualY != null;
        }

        public RawAdvancementDefinition(
                String id,
                String parentId,
                String title,
                List<String> description,
                String icon,
                String frame,
                boolean toast,
                boolean announce,
                AdvancementTriggerData trigger,
                AdvancementRewardData reward
        ) {
            this(id, parentId, title, description, icon, null, null, null, frame, toast, announce, trigger, reward);
        }
    }
}

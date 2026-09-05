package net.vyrim.core.module.advancements;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdvancementIconResolverTest {

    private Logger mockLogger;
    private final Map<Material, ItemMeta> mockMetas = new HashMap<>();

    private Function<Material, ItemStack> testItemFactory;

    @BeforeEach
    void setUp() {
        mockLogger = mock(Logger.class);
        mockMetas.clear();

        testItemFactory = material -> {
            ItemStack item = mock(ItemStack.class);
            ItemMeta meta = mock(ItemMeta.class);
            mockMetas.put(material, meta);
            when(item.getType()).thenReturn(material);
            when(item.getItemMeta()).thenReturn(meta);
            return item;
        };
    }

    @Test
    @DisplayName("Plain Material enum name resolves directly to ItemStack of that material")
    void testPlainMaterialResolves() {
        ItemStack item = AdvancementIconResolver.resolveIcon("DIAMOND_SWORD", Material.PAPER, mockLogger, testItemFactory);
        assertNotNull(item);
        assertEquals(Material.DIAMOND_SWORD, item.getType());
        verify(mockLogger, never()).warning(anyString());

        ItemMeta meta = mockMetas.get(Material.DIAMOND_SWORD);
        if (meta != null) {
            verify(meta, never()).setItemModel(any());
        }
    }

    @Test
    @DisplayName("Valid namespaced item model sets minecraft:item_model component with custom fallback")
    void testCustomNamespacedModelWithCustomFallback() {
        ItemStack item = AdvancementIconResolver.resolveIcon("vyrim:advancement/dragon_head", Material.DRAGON_HEAD, mockLogger, testItemFactory);
        assertNotNull(item);
        assertEquals(Material.DRAGON_HEAD, item.getType());

        ItemMeta meta = mockMetas.get(Material.DRAGON_HEAD);
        assertNotNull(meta);
        ArgumentCaptor<NamespacedKey> captor = ArgumentCaptor.forClass(NamespacedKey.class);
        verify(meta).setItemModel(captor.capture());
        assertEquals("vyrim", captor.getValue().getNamespace());
        assertEquals("advancement/dragon_head", captor.getValue().getKey());
        verify(mockLogger, never()).warning(anyString());
    }

    @Test
    @DisplayName("Valid namespaced item model with omitted fallback defaults to PAPER")
    void testCustomNamespacedModelWithDefaultFallback() {
        ItemStack item = AdvancementIconResolver.resolveIcon("minecraft:custom_thing", null, mockLogger, testItemFactory);
        assertNotNull(item);
        assertEquals(Material.PAPER, item.getType());

        ItemMeta meta = mockMetas.get(Material.PAPER);
        assertNotNull(meta);
        ArgumentCaptor<NamespacedKey> captor = ArgumentCaptor.forClass(NamespacedKey.class);
        verify(meta).setItemModel(captor.capture());
        assertEquals("minecraft", captor.getValue().getNamespace());
        assertEquals("custom_thing", captor.getValue().getKey());
    }

    @Test
    @DisplayName("Malformed namespaced model (empty path or namespace) logs warning and falls back safely")
    void testMalformedNamespacedModel() {
        ItemStack item1 = AdvancementIconResolver.resolveIcon("vyrim:", Material.IRON_SWORD, mockLogger, testItemFactory);
        assertEquals(Material.IRON_SWORD, item1.getType());
        verify(mockLogger).warning(contains("Malformed namespaced icon 'vyrim:'"));

        ItemStack item2 = AdvancementIconResolver.resolveIcon(":empty_namespace", Material.GOLDEN_APPLE, mockLogger, testItemFactory);
        assertEquals(Material.GOLDEN_APPLE, item2.getType());
        verify(mockLogger).warning(contains("Malformed namespaced icon ':empty_namespace'"));
    }

    @Test
    @DisplayName("Invalid Material name without colon logs warning and degrades to fallback base item")
    void testInvalidMaterialNameFallsBack() {
        ItemStack item = AdvancementIconResolver.resolveIcon("COMPLETELY_INVALID_MATERIAL_NAME", Material.STONE, mockLogger, testItemFactory);
        assertNotNull(item);
        assertEquals(Material.STONE, item.getType());
        verify(mockLogger).warning(contains("Invalid icon 'COMPLETELY_INVALID_MATERIAL_NAME'"));
    }

    @Test
    @DisplayName("Null or blank icon string logs warning and defaults to fallback base item")
    void testNullOrBlankIcon() {
        ItemStack itemNull = AdvancementIconResolver.resolveIcon(null, Material.DIRT, mockLogger, testItemFactory);
        assertEquals(Material.DIRT, itemNull.getType());
        verify(mockLogger).warning(contains("Icon value is missing or blank"));

        ItemStack itemBlank = AdvancementIconResolver.resolveIcon("   ", null, mockLogger, testItemFactory);
        assertEquals(Material.PAPER, itemBlank.getType());
    }

    @Test
    @DisplayName("resolveFallbackMaterial correctly resolves valid materials and defaults to PAPER on invalid")
    void testResolveFallbackMaterial() {
        assertEquals(Material.NETHERITE_SWORD, AdvancementIconResolver.resolveFallbackMaterial("NETHERITE_SWORD", mockLogger));
        assertEquals(Material.PAPER, AdvancementIconResolver.resolveFallbackMaterial(null, mockLogger));
        assertEquals(Material.PAPER, AdvancementIconResolver.resolveFallbackMaterial("", mockLogger));

        Material fallback = AdvancementIconResolver.resolveFallbackMaterial("NOT_A_VALID_MATERIAL", mockLogger);
        assertEquals(Material.PAPER, fallback);
        verify(mockLogger).warning(contains("Invalid icon_fallback Material 'NOT_A_VALID_MATERIAL'"));
    }
}

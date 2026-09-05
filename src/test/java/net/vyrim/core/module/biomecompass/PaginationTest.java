package net.vyrim.core.module.biomecompass;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaginationTest {

    @Test
    @DisplayName("Calculates total pages correctly for various collection sizes")
    void testTotalPages() {
        assertEquals(1, calculateTotalPages(0));
        assertEquals(1, calculateTotalPages(1));
        assertEquals(1, calculateTotalPages(BiomeCompassGUI.PAGE_SIZE));
        assertEquals(2, calculateTotalPages(BiomeCompassGUI.PAGE_SIZE + 1));
        assertEquals(2, calculateTotalPages(BiomeCompassGUI.PAGE_SIZE * 2));
        assertEquals(3, calculateTotalPages(BiomeCompassGUI.PAGE_SIZE * 2 + 1));
    }

    @Test
    @DisplayName("Correctly slices items across pages")
    void testPageSublists() {
        int totalItems = 100;
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < totalItems; i++) {
            items.add(i);
        }

        int pageSize = BiomeCompassGUI.PAGE_SIZE;
        int totalPages = calculateTotalPages(totalItems);

        for (int p = 0; p < totalPages; p++) {
            int from = p * pageSize;
            int to = Math.min(from + pageSize, totalItems);
            List<Integer> page = items.subList(from, to);
            int expectedSize = to - from;
            assertEquals(expectedSize, page.size());
            assertEquals(from, page.get(0));
            assertEquals(to - 1, page.get(expectedSize - 1));
        }
    }

    private int calculateTotalPages(int totalItems) {
        return Math.max(1, (int) Math.ceil((double) totalItems / BiomeCompassGUI.PAGE_SIZE));
    }
}

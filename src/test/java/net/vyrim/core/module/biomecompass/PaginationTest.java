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

        int pageSize = BiomeCompassGUI.PAGE_SIZE; // 45
        int totalPages = calculateTotalPages(totalItems); // 3

        // Page 0: 0 to 44 (45 items)
        int p0From = 0;
        int p0To = Math.min(p0From + pageSize, totalItems);
        List<Integer> page0 = items.subList(p0From, p0To);
        assertEquals(45, page0.size());
        assertEquals(0, page0.get(0));
        assertEquals(44, page0.get(44));

        // Page 1: 45 to 89 (45 items)
        int p1From = 1 * pageSize;
        int p1To = Math.min(p1From + pageSize, totalItems);
        List<Integer> page1 = items.subList(p1From, p1To);
        assertEquals(45, page1.size());
        assertEquals(45, page1.get(0));
        assertEquals(89, page1.get(44));

        // Page 2: 90 to 99 (10 items)
        int p2From = 2 * pageSize;
        int p2To = Math.min(p2From + pageSize, totalItems);
        List<Integer> page2 = items.subList(p2From, p2To);
        assertEquals(10, page2.size());
        assertEquals(90, page2.get(0));
        assertEquals(99, page2.get(9));
    }

    private int calculateTotalPages(int totalItems) {
        return Math.max(1, (int) Math.ceil((double) totalItems / BiomeCompassGUI.PAGE_SIZE));
    }
}

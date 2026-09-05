package net.vyrim.core.module.biomecompass;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BiomeSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BiomeRingProbeTest {

    private World mockWorld;
    private Location playerLoc;

    @BeforeEach
    void setUp() {
        mockWorld = mock(World.class);
        when(mockWorld.getName()).thenReturn("world");
        playerLoc = new Location(mockWorld, 0, 64, 0);
    }

    private BiomeSearchResult createMockResult(World world, double x, double y, double z) {
        BiomeSearchResult result = mock(BiomeSearchResult.class);
        Location loc = new Location(world, x, y, z);
        when(result.getLocation()).thenReturn(loc);
        return result;
    }

    @Test
    @DisplayName("selectBestCandidate selects closest candidate at or above minDistance")
    void testSelectsClosestValidCandidate() {
        // Player at (0, 64, 0), minDistance = 150
        BiomeSearchResult candTooClose = createMockResult(mockWorld, 100, 64, 0);   // dist = 100 (< 150, discard)
        BiomeSearchResult candBorderline = createMockResult(mockWorld, 150, 64, 0); // dist = 150 (valid)
        BiomeSearchResult candFurther = createMockResult(mockWorld, 200, 64, 0);    // dist = 200 (valid)
        BiomeSearchResult candDistant = createMockResult(mockWorld, 500, 64, 0);    // dist = 500 (valid)

        List<BiomeSearchResult> candidates = Arrays.asList(candTooClose, candDistant, candBorderline, candFurther);

        BiomeSearchResult best = BiomeLocatorService.selectBestCandidate(playerLoc, candidates, 150.0);
        assertNotNull(best);
        assertSame(candBorderline, best, "Candidate at exactly minDistance (150) should be chosen as closest valid");
    }

    @Test
    @DisplayName("selectBestCandidate filters out results below minDistance")
    void testFiltersBelowMinDistance() {
        // Results at distances 50, 90, 149 (< 150)
        BiomeSearchResult cand1 = createMockResult(mockWorld, 50, 64, 0);
        BiomeSearchResult cand2 = createMockResult(mockWorld, 0, 64, 90);
        BiomeSearchResult cand3 = createMockResult(mockWorld, 0, 64, -149);

        List<BiomeSearchResult> candidates = Arrays.asList(cand1, cand2, cand3);

        BiomeSearchResult best = BiomeLocatorService.selectBestCandidate(playerLoc, candidates, 150.0);
        assertNull(best, "All candidates below minDistance (150) must be discarded");
    }

    @Test
    @DisplayName("selectBestCandidate gracefully discards null candidates and null locations")
    void testHandlesNullCandidatesAndLocations() {
        BiomeSearchResult validCandidate = createMockResult(mockWorld, 250, 64, 0);

        BiomeSearchResult nullLocationResult = mock(BiomeSearchResult.class);
        when(nullLocationResult.getLocation()).thenReturn(null);

        List<BiomeSearchResult> candidates = new ArrayList<>();
        candidates.add(null);
        candidates.add(nullLocationResult);
        candidates.add(validCandidate);
        candidates.add(null);

        BiomeSearchResult best = BiomeLocatorService.selectBestCandidate(playerLoc, candidates, 150.0);
        assertNotNull(best);
        assertSame(validCandidate, best);
    }

    @Test
    @DisplayName("selectBestCandidate returns null when list is null or empty")
    void testNullOrEmptyList() {
        assertNull(BiomeLocatorService.selectBestCandidate(playerLoc, null, 150.0));
        assertNull(BiomeLocatorService.selectBestCandidate(playerLoc, Collections.emptyList(), 150.0));
        assertNull(BiomeLocatorService.selectBestCandidate(null, Collections.emptyList(), 150.0));
    }

    @Test
    @DisplayName("selectBestCandidate handles candidate with null world by adopting player's world")
    void testCandidateWithNullWorld() {
        BiomeSearchResult candNoWorld = createMockResult(null, 300, 64, 0);

        BiomeSearchResult best = BiomeLocatorService.selectBestCandidate(playerLoc, List.of(candNoWorld), 150.0);
        assertNotNull(best);
        assertSame(candNoWorld, best);
    }

    @Test
    @DisplayName("selectBestCandidate ignores candidates in a mismatched world")
    void testCandidateInDifferentWorldIgnored() {
        World otherWorld = mock(World.class);
        when(otherWorld.getName()).thenReturn("world_nether");

        BiomeSearchResult candOtherWorld = createMockResult(otherWorld, 200, 64, 0);
        BiomeSearchResult candSameWorld = createMockResult(mockWorld, 300, 64, 0);

        BiomeSearchResult best = BiomeLocatorService.selectBestCandidate(playerLoc, List.of(candOtherWorld, candSameWorld), 150.0);
        assertNotNull(best);
        assertSame(candSameWorld, best);
    }
}

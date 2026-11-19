package com.noahsellers.agriflight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class FarmItemTest {
    private static FarmProperties props(String name) {
        return new FarmProperties(name, BigDecimal.ONE, 0, 0, 10, 10, 1);
    }

    @Test
    void itemIsALeafWithNoChildren() {
        assertTrue(new Item(props("Cow")).children().isEmpty());
    }

    @Test
    void containerAddsAndRemovesChildren() {
        ItemContainer barn = new ItemContainer(props("Barn"));
        FarmItem cow = new Item(props("Cow"));
        FarmItem stall = new ItemContainer(props("Stall"));
        barn.add(cow);
        barn.add(stall);
        assertEquals(List.of(cow, stall), barn.children());
        barn.remove(cow);
        assertEquals(List.of(stall), barn.children());
    }

    @Test
    void childrenListCannotBeModifiedFromOutside() {
        ItemContainer barn = new ItemContainer(props("Barn"));
        assertThrows(UnsupportedOperationException.class, () -> barn.children().add(new Item(props("Cow"))));
    }

    @Test
    void updateReplacesPropertiesOnLeavesAndContainers() {
        for (FarmItem element : List.<FarmItem>of(new Item(props("Old")), new ItemContainer(props("Old")))) {
            element.update(props("New"));
            assertEquals("New", element.properties().name());
        }
    }

    @Test
    void sampleFarmMatchesTestScenario() {
        ItemContainer farm = FarmDashboard.sampleFarm();
        assertEquals(List.of("Barn", "Storage Building", "Command Center", "Soy Crop"),
                farm.children().stream().map(child -> child.properties().name()).toList());
        assertEquals(11, count(farm)); // root + 4 containers + 6 items
    }

    @Test
    void parseRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> FarmProperties.parse(" ", "1", "0", "0", "1", "1", "1"));
        assertThrows(IllegalArgumentException.class, () -> FarmProperties.parse("Cow", "-1", "0", "0", "1", "1", "1"));
        assertThrows(IllegalArgumentException.class, () -> FarmProperties.parse("Cow", "1", "x", "0", "1", "1", "1"));
        assertThrows(IllegalArgumentException.class, () -> FarmProperties.parse("Cow", "1", "0", "0", "0", "1", "1"));
        assertEquals(new BigDecimal("2.50"), FarmProperties.parse(" Cow ", "2.50", "0", "0", "1", "1", "1").price());
    }

    @Test
    void scanRouteStartsAndEndsAtHome() {
        Point2D.Double home = new Point2D.Double(150, 470);
        List<Point2D.Double> route = FarmMap.scanRoute(home);
        assertEquals(home, route.get(0));
        assertEquals(home, route.get(route.size() - 1));
    }

    private static int count(FarmItem item) {
        return 1 + item.children().stream().mapToInt(FarmItemTest::count).sum();
    }
}

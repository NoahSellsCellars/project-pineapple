package com.agriflight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;
import java.lang.reflect.Modifier;
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
    void parseErrorsNameTheInvalidField() {
        assertEquals("Name can't be blank.", parseError(" ", "1", "0", "0", "1", "1", "1"));
        assertEquals("Price can't be negative.", parseError("Cow", "-5", "0", "0", "1", "1", "1"));
        assertEquals("Price must be a number, for example 1500 or 12.50.", parseError("Cow", "lots", "0", "0", "1", "1", "1"));
        assertEquals("Location-Y must be a number.", parseError("Cow", "1", "0", "up", "1", "1", "1"));
        assertEquals("Location-X must be a finite number.", parseError("Cow", "1", "Infinity", "0", "1", "1", "1"));
        assertEquals("Length must be a number.", parseError("Cow", "1", "0", "0", "abc", "1", "1"));
        assertEquals("Width must be a number greater than 0.", parseError("Cow", "1", "0", "0", "1", "0", "1"));
        assertEquals("Height must be a number greater than 0.", parseError("Cow", "1", "0", "0", "1", "1", "NaN"));
    }

    private static String parseError(String... values) {
        return assertThrows(IllegalArgumentException.class, () -> FarmProperties.parse(values)).getMessage();
    }

    @Test
    void scanRouteStartsAndEndsAtHome() {
        Point2D.Double home = new Point2D.Double(150, 470);
        List<Point2D.Double> route = FarmMap.scanRoute(home);
        assertEquals(home, route.get(0));
        assertEquals(home, route.get(route.size() - 1));
    }

    @Test
    void dashboardIsASingleton() {
        assertSame(FarmDashboard.getInstance(), FarmDashboard.getInstance());
        var constructors = FarmDashboard.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
    }

    @Test
    void totalPriceAddsUpContainerContents() {
        ItemContainer barn = new ItemContainer(price("Barn", "100"));
        ItemContainer stall = new ItemContainer(price("Stall", "20"));
        stall.add(new Item(price("Cow", "5")));
        barn.add(stall);
        barn.add(new Item(price("Hay", "1.50")));
        assertEquals(new BigDecimal("5"), barn.children().get(0).children().get(0).totalPrice());
        assertEquals(new BigDecimal("25"), stall.totalPrice());
        assertEquals(new BigDecimal("126.50"), barn.totalPrice());
    }

    @Test
    void sampleFarmTotalIncludesEveryElement() {
        assertEquals(new BigDecimal("197500"), FarmDashboard.sampleFarm().totalPrice());
    }

    @Test
    void parseAcceptsAnArrayOfSevenValues() {
        String[] values = {"Cow", "3", "1", "2", "4", "5", "6"};
        assertEquals(new FarmProperties("Cow", new BigDecimal("3"), 1, 2, 4, 5, 6), FarmProperties.parse(values));
        assertThrows(IllegalArgumentException.class, () -> FarmProperties.parse(new String[] {"Cow"}));
    }

    private static FarmProperties price(String name, String price) {
        return new FarmProperties(name, new BigDecimal(price), 0, 0, 10, 10, 1);
    }

    private static int count(FarmItem item) {
        return 1 + item.children().stream().mapToInt(FarmItemTest::count).sum();
    }
}

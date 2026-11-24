package com.agriflight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FarmApiServerTest {
    // Ids are given out depth-first: 1 Farm, 2 Barn, 3 Livestock Area, 4 Cow, 5 Milk Storage,
    // 6 Storage Building, 7 Tractor, 8 Tiller, 9 Command Center, 10 Drone, 11 Soy Crop.
    private final HttpClient client = HttpClient.newHttpClient();
    private FarmEditor editor;
    private FarmApiServer server;
    private String base;

    private record Reply(int status, Object json) {
        @SuppressWarnings("unchecked")
        Map<String, Object> object() {
            return (Map<String, Object>) json;
        }

        String error() {
            return (String) object().get("error");
        }
    }

    @BeforeEach
    void startServer() throws Exception {
        // The tests don't start the JavaFX toolkit: the map runs without drawing, and requests run
        // directly on the server's single request thread instead of the JavaFX application thread.
        ItemContainer farm = FarmDashboard.sampleFarm();
        editor = new FarmEditor(farm);
        server = new FarmApiServer(editor, new FarmMap(farm, editor, false), Runnable::run);
        base = "http://127.0.0.1:" + server.start(0) + "/api";
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    private Reply send(String method, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body().isEmpty() ? null : Json.parse(response.body()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object json) {
        return (Map<String, Object>) json;
    }

    private static void assertNumber(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) actual), () -> expected + " != " + actual);
    }

    @Test
    void farmReturnsTheWholeTreeWithIdsAndTotals() throws Exception {
        Reply reply = send("GET", "/farm", null);
        assertEquals(200, reply.status());
        assertEquals("Farm", reply.object().get("name"));
        assertNumber("1", reply.object().get("id"));
        assertNumber("197500", reply.object().get("totalPrice"));
        List<?> children = (List<?>) reply.object().get("children");
        assertEquals(4, children.size());
        assertEquals("Barn", map(children.get(0)).get("name"));
        assertNumber("2", map(children.get(0)).get("id"));
    }

    @Test
    void itemsCanBeReadById() throws Exception {
        Reply cow = send("GET", "/items/4", null);
        assertEquals(200, cow.status());
        assertEquals("Cow", cow.object().get("name"));
        assertEquals("item", cow.object().get("type"));
        assertNumber("1500", cow.object().get("price"));
        assertEquals(404, send("GET", "/items/999", null).status());
        assertEquals(404, send("GET", "/items/abc", null).status());
    }

    @Test
    void childrenCanBeAddedToContainersOnly() throws Exception {
        Reply hay = send("POST", "/items/2/children", "{\"type\":\"item\",\"name\":\"Hay\",\"price\":12.5,\"x\":30,\"y\":300}");
        assertEquals(201, hay.status());
        assertEquals("Hay", hay.object().get("name"));
        assertNumber("40", hay.object().get("length"));
        assertNumber("74512.5", send("GET", "/items/2", null).object().get("totalPrice"));
        assertEquals(3, editor.farm().children().get(0).children().size());
        assertNotNull(editor.nodeOf(editor.farm().children().get(0).children().get(2)));

        assertEquals(409, send("POST", "/items/4/children", "{\"type\":\"item\",\"name\":\"Calf\"}").status());
        assertEquals(400, send("POST", "/items/2/children", "{\"name\":\"Mystery\"}").status());
    }

    @Test
    void updatesAreValidatedLikeTheDashboard() throws Exception {
        Reply negative = send("PATCH", "/items/4", "{\"price\":-1}");
        assertEquals(400, negative.status());
        assertEquals("Price can't be negative.", negative.error());
        assertEquals(400, send("PATCH", "/items/4", "{\"colour\":\"brown\"}").status());
        assertEquals(400, send("PATCH", "/items/4", "{").status());

        Reply renamed = send("PATCH", "/items/4", "{\"name\":\"Bessie\",\"x\":75}");
        assertEquals(200, renamed.status());
        assertEquals("Bessie", renamed.object().get("name"));
        assertNumber("75", renamed.object().get("x"));
        assertEquals("Bessie", editor.nodeOf(editor.farm().children().get(0).children().get(0).children().get(0)).getValue().toString());
    }

    @Test
    void deletingRemovesContentsButNeverTheRoot() throws Exception {
        assertEquals(409, send("DELETE", "/items/1", null).status());
        assertEquals(204, send("DELETE", "/items/3", null).status());
        assertEquals(404, send("GET", "/items/4", null).status());
        assertNumber("68000", send("GET", "/items/2", null).object().get("totalPrice"));
    }

    @Test
    void droneReportsPositionAndRunsFlights() throws Exception {
        Reply idle = send("GET", "/drone", null);
        assertEquals(false, idle.object().get("flying"));
        assertNumber("150", map(idle.object().get("home")).get("x"));
        assertNumber("470", map(idle.object().get("home")).get("y"));

        Reply reported = send("PUT", "/drone/position", "{\"x\":10,\"y\":20}");
        assertNumber("10", map(reported.object().get("reportedPosition")).get("x"));
        assertEquals(400, send("PUT", "/drone/position", "{\"x\":10}").status());

        Reply visit = send("POST", "/drone/visit", "{\"id\":7}");
        assertEquals(200, visit.status());
        assertEquals(true, visit.object().get("flying"));
        List<?> route = (List<?>) visit.object().get("route");
        assertEquals(3, route.size());
        assertNumber("500", map(route.get(1)).get("x"));
        assertNumber("115", map(route.get(1)).get("y"));
        assertEquals(409, send("POST", "/drone/scan", null).status());

        assertNull(send("DELETE", "/drone/position", null).object().get("reportedPosition"));
    }

    @Test
    void visitingTheRootIsRefused() throws Exception {
        Reply reply = send("POST", "/drone/visit", "{\"id\":1}");
        assertEquals(409, reply.status());
        assertTrue(reply.error().startsWith("Select an item or container"));
    }

    @Test
    void unknownPathsAndMethodsAreRejected() throws Exception {
        assertEquals(200, send("GET", "", null).status());
        assertEquals(404, send("GET", "/nothing", null).status());
        assertEquals(405, send("POST", "/farm", null).status());
        assertEquals(405, send("PUT", "/items/4", "{}").status());
    }

    @Test
    void jsonRoundTripsEscapesAndNumbers() {
        Object parsed = Json.parse(" {\"a\": [1, 2.50, \"x\\n\\u00e9\\\"\", true, null], \"b\": {}} ");
        assertEquals("{\"a\":[1,2.50,\"x\\né\\\"\",true,null],\"b\":{}}", Json.write(parsed));
    }
}

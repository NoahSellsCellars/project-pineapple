package com.agriflight;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import javafx.geometry.Point2D;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A local HTTP + JSON API so other programs on this computer, such as the physical drone's
 * controller, can read and change the farm and the drone. It listens on 127.0.0.1 only.
 * Every request runs on the user-interface thread (the JavaFX application thread in the app),
 * so API changes and dashboard changes never race, and the tree and map update immediately.
 */
final class FarmApiServer {
    static final int DEFAULT_PORT = 8080;

    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final List<String> FIELDS = List.of("name", "price", "x", "y", "length", "width", "height");
    private static final FarmProperties NEW_ELEMENT = new FarmProperties("", BigDecimal.ZERO, 0, 0, 40, 30, 1);

    private final FarmEditor editor;
    private final FarmMap map;
    private final Executor uiThread;
    private final Map<FarmItem, Integer> ids = new IdentityHashMap<>();
    private final Map<Integer, FarmItem> elements = new HashMap<>();
    private int nextId = 1;
    private HttpServer server;
    private ExecutorService executor;

    /** @param uiThread runs each request's work on the UI thread; the app passes {@code Platform::runLater}. */
    FarmApiServer(FarmEditor editor, FarmMap map, Executor uiThread) {
        this.editor = editor;
        this.map = map;
        this.uiThread = uiThread;
    }

    /** Starts listening on 127.0.0.1 and returns the port (pass 0 to pick any free port). */
    int start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/api", this::handle);
        executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "agriflight-api");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.start();
        return server.getAddress().getPort();
    }

    void stop() {
        if (server != null) {
            server.stop(0);
            executor.shutdown();
        }
    }

    private record Response(int status, Object body) {}

    private static final class ApiException extends RuntimeException {
        final int status;

        ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        Response response;
        try {
            Object body = readBody(exchange);
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            response = onUiThread(() -> route(method, path, body));
        } catch (ApiException exception) {
            response = new Response(exception.status, Map.of("error", exception.getMessage()));
        } catch (Exception exception) {
            response = new Response(500, Map.of("error", "Internal error: " + exception));
        }
        send(exchange, response);
    }

    private static Object readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) {
            throw new ApiException(413, "The request body is larger than 64 KB.");
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            return null;
        }
        try {
            return Json.parse(text);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(400, exception.getMessage());
        }
    }

    private static void send(HttpExchange exchange, Response response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        if (response.body() == null) {
            exchange.sendResponseHeaders(response.status(), -1);
        } else {
            byte[] bytes = Json.write(response.body()).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(response.status(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    private <T> T onUiThread(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        uiThread.execute(task);
        try {
            return task.get();
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw exception;
        }
    }

    private Response route(String method, String path, Object body) {
        String trimmed = path.replaceAll("/+$", "");
        if (!trimmed.equals("/api") && !trimmed.startsWith("/api/")) {
            throw new ApiException(404, "No endpoint at " + path);
        }
        List<String> parts = trimmed.equals("/api") ? List.of() : List.of(trimmed.substring(5).split("/"));
        assignIds(editor.farm());

        if (parts.isEmpty()) {
            allow(method, "GET");
            return ok(index());
        }
        switch (parts.get(0)) {
            case "farm" -> {
                if (parts.size() == 1) {
                    allow(method, "GET");
                    return ok(element(editor.farm()));
                }
            }
            case "items" -> {
                if (parts.size() == 2) {
                    FarmItem item = find(parts.get(1));
                    return switch (method) {
                        case "GET" -> ok(element(item));
                        case "PATCH" -> update(item, body);
                        case "DELETE" -> delete(item);
                        default -> throw notAllowed("GET, PATCH or DELETE");
                    };
                }
                if (parts.size() == 3 && parts.get(2).equals("children")) {
                    allow(method, "POST");
                    return addChild(find(parts.get(1)), body);
                }
            }
            case "drone" -> {
                if (parts.size() == 1) {
                    allow(method, "GET");
                    return ok(drone());
                }
                if (parts.size() == 2) {
                    switch (parts.get(1)) {
                        case "position" -> {
                            return switch (method) {
                                case "PUT" -> reportPosition(body);
                                case "DELETE" -> {
                                    map.setReportedPosition(null);
                                    yield ok(drone());
                                }
                                default -> throw notAllowed("PUT or DELETE");
                            };
                        }
                        case "visit" -> {
                            allow(method, "POST");
                            FarmItem target = find(String.valueOf(integer(object(body), "id")));
                            return flight(map.startVisit(target));
                        }
                        case "scan" -> {
                            allow(method, "POST");
                            return flight(map.startScan());
                        }
                        default -> { }
                    }
                }
            }
            default -> { }
        }
        throw new ApiException(404, "No endpoint at " + method + " " + path + ". GET /api lists the endpoints.");
    }

    private static Map<String, Object> index() {
        Map<String, Object> index = new LinkedHashMap<>();
        index.put("endpoints", List.of(
                "GET /api/farm",
                "GET /api/items/{id}",
                "PATCH /api/items/{id}",
                "DELETE /api/items/{id}",
                "POST /api/items/{id}/children",
                "GET /api/drone",
                "PUT /api/drone/position",
                "DELETE /api/drone/position",
                "POST /api/drone/visit",
                "POST /api/drone/scan"));
        return index;
    }

    private Response update(FarmItem item, Object body) {
        Map<String, Object> fields = object(body);
        if (fields.isEmpty()) {
            throw new ApiException(400, "Send at least one of: " + String.join(", ", FIELDS) + ".");
        }
        editor.update(item, properties(item.properties(), fields));
        return ok(element(item));
    }

    private Response delete(FarmItem item) {
        if (item == editor.farm()) {
            throw new ApiException(409, "The root farm can't be deleted.");
        }
        editor.remove(item);
        return new Response(204, null);
    }

    private Response addChild(FarmItem parent, Object body) {
        if (!(parent instanceof ItemContainer container)) {
            throw new ApiException(409, parent.properties().name() + " is an item, not a container, so it can't hold other elements.");
        }
        Map<String, Object> fields = new LinkedHashMap<>(object(body));
        Object type = fields.remove("type");
        if (!"item".equals(type) && !"container".equals(type)) {
            throw new ApiException(400, "Set \"type\" to \"item\" or \"container\".");
        }
        FarmProperties properties = properties(NEW_ELEMENT, fields);
        FarmItem child = "container".equals(type) ? new ItemContainer(properties) : new Item(properties);
        editor.add(container, child);
        assignIds(child);
        return new Response(201, element(child));
    }

    private Response reportPosition(Object body) {
        Map<String, Object> fields = object(body);
        double x = number(fields, "x");
        double y = number(fields, "y");
        map.setReportedPosition(new Point2D(x, y));
        return ok(drone());
    }

    private Response flight(String problem) {
        if (problem != null) {
            throw new ApiException(409, problem + ".");
        }
        return ok(drone());
    }

    /** Applies the given JSON fields on top of a starting set of properties, with the dashboard's validation. */
    private static FarmProperties properties(FarmProperties start, Map<String, Object> fields) {
        String[] values = FarmDashboard.values(start);
        for (Map.Entry<String, Object> field : fields.entrySet()) {
            int index = FIELDS.indexOf(field.getKey());
            if (index < 0) {
                throw new ApiException(400, "Unknown field \"" + field.getKey() + "\". Use: " + String.join(", ", FIELDS) + ".");
            }
            values[index] = text(field.getKey(), field.getValue());
        }
        try {
            return FarmProperties.parse(values);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(400, exception.getMessage());
        }
    }

    private static String text(String field, Object value) {
        if (field.equals("name")) {
            if (value instanceof String name) {
                return name;
            }
            throw new ApiException(400, "name must be a string.");
        }
        if (value instanceof BigDecimal number) {
            return number.toPlainString();
        }
        if (value instanceof String text) {
            return text;
        }
        throw new ApiException(400, field + " must be a number.");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object body) {
        if (body == null) {
            return Map.of();
        }
        if (body instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new ApiException(400, "The request body must be a JSON object.");
    }

    private static double number(Map<String, Object> fields, String field) {
        if (!(fields.get(field) instanceof BigDecimal number)) {
            throw new ApiException(400, field + " is required and must be a number.");
        }
        return number.doubleValue();
    }

    private static int integer(Map<String, Object> fields, String field) {
        try {
            if (fields.get(field) instanceof BigDecimal number) {
                return number.intValueExact();
            }
        } catch (ArithmeticException exception) {
            // Falls through to the error below.
        }
        throw new ApiException(400, field + " is required and must be a whole number.");
    }

    private FarmItem find(String idText) {
        FarmItem item = null;
        try {
            item = elements.get(Integer.parseInt(idText));
        } catch (NumberFormatException exception) {
            // Falls through to the error below.
        }
        if (item == null || !editor.contains(item)) {
            throw new ApiException(404, "No farm element with id " + idText + ".");
        }
        return item;
    }

    /** Gives every element a stable id the first time the API sees it. */
    private void assignIds(FarmItem item) {
        if (!ids.containsKey(item)) {
            ids.put(item, nextId);
            elements.put(nextId, item);
            nextId++;
        }
        for (FarmItem child : item.children()) {
            assignIds(child);
        }
    }

    private Map<String, Object> element(FarmItem item) {
        FarmProperties properties = item.properties();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", ids.get(item));
        json.put("type", item instanceof ItemContainer ? "container" : "item");
        json.put("name", properties.name());
        json.put("price", properties.price());
        json.put("totalPrice", item.totalPrice());
        json.put("x", properties.x());
        json.put("y", properties.y());
        json.put("length", properties.length());
        json.put("width", properties.width());
        json.put("height", properties.height());
        List<Object> children = new ArrayList<>();
        for (FarmItem child : item.children()) {
            children.add(element(child));
        }
        json.put("children", children);
        return json;
    }

    private Map<String, Object> drone() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("flying", map.isFlying());
        json.put("status", map.status());
        json.put("home", point(map.droneHome()));
        json.put("position", point(map.dronePosition()));
        json.put("reportedPosition", point(map.reportedPosition()));
        List<Object> route = new ArrayList<>();
        for (Point2D waypoint : map.route()) {
            route.add(point(waypoint));
        }
        json.put("route", route);
        json.put("nextWaypoint", map.isFlying() ? map.nextWaypoint() : null);
        return json;
    }

    private static Map<String, Object> point(Point2D point) {
        if (point == null) {
            return null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("x", point.getX());
        json.put("y", point.getY());
        return json;
    }

    private static Response ok(Object body) {
        return new Response(200, body);
    }

    private static void allow(String method, String allowed) {
        if (!method.equals(allowed)) {
            throw notAllowed(allowed);
        }
    }

    private static ApiException notAllowed(String allowed) {
        return new ApiException(405, "Use " + allowed + " for this endpoint.");
    }
}

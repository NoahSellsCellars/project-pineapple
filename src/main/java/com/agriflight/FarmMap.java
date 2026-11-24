package com.agriflight;

import java.util.ArrayList;
import java.util.List;

import javafx.animation.AnimationTimer;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * The 800 × 600 farm map: a JavaFX {@link Canvas} that draws the composite tree and animates the
 * drone with an {@link AnimationTimer}. It uses screen coordinates: the origin is the top-left
 * corner, x grows to the right and y grows downward.
 */
final class FarmMap extends Canvas {
    static final int WIDTH = 800;
    static final int HEIGHT = 600;

    private static final Color GROUND = Color.rgb(246, 249, 242);
    private static final Color GRID = Color.rgb(224, 232, 218);
    private static final Color CONTAINER = Color.rgb(58, 95, 74);
    private static final Color ITEM = Color.rgb(192, 221, 169);
    private static final Color SELECTION = Color.rgb(31, 103, 186);
    private static final Color REPORTED = Color.rgb(214, 120, 30);

    /** Drone speed in map pixels per second. */
    private static final double FLIGHT_SPEED = 480;

    private final ItemContainer farm;
    private final boolean animated;
    private final AnimationTimer flightTimer;
    private final ReadOnlyStringWrapper status = new ReadOnlyStringWrapper("Drone ready");
    private final FarmItem originalCommandCenter;
    private final FarmItem originalDrone;
    private FarmItem selected;
    private List<Point2D> route = List.of();
    private Point2D dronePosition;
    private Point2D reportedPosition;
    private int nextWaypoint;
    private boolean flying;
    private long lastTick = -1;

    FarmMap(ItemContainer farm, FarmEditor editor) {
        this(farm, editor, true);
    }

    /**
     * With {@code animated} false the map neither draws nor starts a timer, so flight logic can be
     * tested without starting the JavaFX toolkit. Flights then advance only through
     * {@link #advanceFlight(double)}.
     */
    FarmMap(ItemContainer farm, FarmEditor editor, boolean animated) {
        super(WIDTH, HEIGHT);
        this.farm = farm;
        this.animated = animated;
        originalCommandCenter = namedChild(farm, "Command Center", true);
        originalDrone = namedChild(originalCommandCenter, "Drone", false);
        flightTimer = animated ? new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastTick >= 0) {
                    advanceFlight((now - lastTick) / 1_000_000_000.0);
                }
                lastTick = now;
            }
        } : null;
        editor.onChange(this::farmChanged);
        redraw();
    }

    FarmItem selected() {
        return selected;
    }

    void setSelected(FarmItem item) {
        selected = item;
        redraw();
    }

    /** The drone's status message, for a label to bind to. */
    ReadOnlyStringProperty statusProperty() {
        return status.getReadOnlyProperty();
    }

    String status() {
        return status.get();
    }

    /** Flies home → target → home. Returns null if the flight started, otherwise the reason it didn't. */
    String startVisit(FarmItem target) {
        return startFlight(false, target);
    }

    /** Sweeps the whole farm and returns home. Returns null if the flight started, otherwise the reason it didn't. */
    String startScan() {
        return startFlight(true, null);
    }

    private String startFlight(boolean scan, FarmItem target) {
        if (flying) {
            return "The drone is already flying";
        }
        Point2D home = droneHome();
        if (home == null) {
            return report("Add a Drone to the Command Center");
        }
        if (!scan && (target == null || target == farm)) {
            return report("Select an item or container to visit");
        }
        route = scan ? scanRoute(home) : List.of(home, center(target), home);
        dronePosition = home;
        nextWaypoint = 1;
        flying = true;
        status.set(scan ? "Scanning farm" : "Flying to " + target.properties().name());
        if (flightTimer != null) {
            lastTick = -1;
            flightTimer.start();
        }
        redraw();
        return null;
    }

    private String report(String message) {
        status.set(message);
        return message;
    }

    private void endFlight(String message) {
        flying = false;
        if (flightTimer != null) {
            flightTimer.stop();
        }
        status.set(message);
    }

    boolean isFlying() {
        return flying;
    }

    /** The waypoints of the current or most recent flight. */
    List<Point2D> route() {
        return route;
    }

    /** Index in {@link #route()} of the waypoint the drone is flying toward. */
    int nextWaypoint() {
        return nextWaypoint;
    }

    /** Where the simulated drone is now: on its route while flying, otherwise at home. */
    Point2D dronePosition() {
        return flying ? dronePosition : droneHome();
    }

    /** Where the real drone says it is, or null if nothing has been reported. */
    Point2D reportedPosition() {
        return reportedPosition;
    }

    void setReportedPosition(Point2D position) {
        reportedPosition = position;
        redraw();
    }

    /** Back-and-forth rows 50 px apart that cover the whole 800 × 600 farm, starting and ending at home. */
    static List<Point2D> scanRoute(Point2D home) {
        List<Point2D> points = new ArrayList<>();
        points.add(home);
        for (int y = 25; y < HEIGHT; y += 50) {
            boolean eastbound = ((y - 25) / 50) % 2 == 0;
            points.add(new Point2D(eastbound ? 10 : WIDTH - 10, y));
            points.add(new Point2D(eastbound ? WIDTH - 10 : 10, y));
        }
        points.add(home);
        return List.copyOf(points);
    }

    /** Moves the drone along its route by the distance it flies in the given number of seconds. */
    void advanceFlight(double seconds) {
        if (!flying) {
            return;
        }
        double remaining = seconds * FLIGHT_SPEED;
        while (nextWaypoint < route.size()) {
            Point2D target = route.get(nextWaypoint);
            double distance = dronePosition.distance(target);
            if (distance > remaining) {
                dronePosition = dronePosition.add(target.subtract(dronePosition).multiply(remaining / distance));
                break;
            }
            dronePosition = target;
            remaining -= distance;
            nextWaypoint++;
        }
        if (nextWaypoint == route.size()) {
            endFlight("Flight complete. Drone at home.");
        }
        redraw();
    }

    Point2D droneHome() {
        FarmItem commandCenter = originalCommandCenter != null && farm.children().contains(originalCommandCenter)
                ? originalCommandCenter : namedChild(farm, "Command Center", true);
        if (commandCenter == null) {
            return null;
        }
        FarmItem drone = originalDrone != null && commandCenter.children().contains(originalDrone)
                ? originalDrone : namedChild(commandCenter, "Drone", false);
        return drone == null ? null : center(drone);
    }

    private static FarmItem namedChild(FarmItem parent, String name, boolean container) {
        if (parent == null) {
            return null;
        }
        for (FarmItem child : parent.children()) {
            if ((child instanceof ItemContainer) == container && name.equalsIgnoreCase(child.properties().name())) {
                return child;
            }
        }
        return null;
    }

    private static Point2D center(FarmItem item) {
        FarmProperties p = item.properties();
        return new Point2D(p.x() + p.length() / 2, p.y() + p.width() / 2);
    }

    private void farmChanged() {
        if (flying) {
            endFlight("Flight canceled because the farm changed");
        }
        redraw();
    }

    /** Redraws the whole map: ground, grid, every farm element, the selection and the drone. */
    private void redraw() {
        if (!animated) {
            return;
        }
        GraphicsContext plot = getGraphicsContext2D();
        plot.setFill(GROUND);
        plot.fillRect(0, 0, WIDTH, HEIGHT);
        plot.setStroke(GRID);
        plot.setLineWidth(1);
        for (int x = 50; x < WIDTH; x += 50) {
            plot.strokeLine(x + 0.5, 0, x + 0.5, HEIGHT);
        }
        for (int y = 50; y < HEIGHT; y += 50) {
            plot.strokeLine(0, y + 0.5, WIDTH, y + 0.5);
        }
        plot.setFont(Font.font(12));
        drawElement(plot, farm);
        if (selected != null) {
            FarmProperties p = selected.properties();
            plot.setStroke(SELECTION);
            plot.setLineWidth(2);
            plot.strokeRect(p.x(), p.y(), p.length(), p.width());
        }
        Point2D position = dronePosition();
        if (position != null) {
            plot.setFill(Color.WHITE);
            plot.fillOval(position.getX() - 8, position.getY() - 8, 16, 16);
            plot.setFill(SELECTION);
            plot.fillOval(position.getX() - 6, position.getY() - 6, 12, 12);
        }
        if (reportedPosition != null) {
            plot.setStroke(REPORTED);
            plot.setLineWidth(3);
            plot.strokeOval(reportedPosition.getX() - 10, reportedPosition.getY() - 10, 20, 20);
            plot.setFill(REPORTED);
            plot.fillText("Live drone", reportedPosition.getX() + 14, reportedPosition.getY() + 4);
        }
    }

    /** Draws an element at its location, scaled to its length and width, then each of its children. */
    private void drawElement(GraphicsContext plot, FarmItem item) {
        FarmProperties p = item.properties();
        if (!(item instanceof ItemContainer)) {
            plot.setFill(ITEM);
            plot.fillRect(p.x(), p.y(), p.length(), p.width());
        }
        plot.setStroke(CONTAINER);
        plot.setLineWidth(1);
        plot.strokeRect(p.x() + 0.5, p.y() + 0.5, p.length(), p.width());
        plot.setFill(CONTAINER);
        plot.fillText(p.name(), p.x() + 5, p.y() + 15);
        for (FarmItem child : item.children()) {
            drawElement(plot, child);
        }
    }
}

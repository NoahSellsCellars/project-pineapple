package com.agriflight;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.Timer;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

final class FarmMap extends JPanel implements TreeModelListener {
    static final int WIDTH = 800;
    static final int HEIGHT = 600;

    private static final Color GROUND = new Color(246, 249, 242);
    private static final Color GRID = new Color(224, 232, 218);
    private static final Color CONTAINER = new Color(58, 95, 74);
    private static final Color ITEM = new Color(192, 221, 169);
    private static final Color SELECTION = new Color(31, 103, 186);

    private final ItemContainer farm;
    private FarmItem selected;
    private final FarmItem originalCommandCenter;
    private final FarmItem originalDrone;
    private final Timer flightTimer = new Timer(16, event -> advanceFlight());
    private List<Point2D.Double> route = List.of();
    private Point2D.Double dronePosition;
    private int nextWaypoint;
    private long lastTick;
    private JLabel flightStatus;

    private static final double FLIGHT_SPEED = 480;

    FarmMap(ItemContainer farm, DefaultTreeModel model, JTree hierarchy) {
        this.farm = farm;
        originalCommandCenter = namedChild(farm, "Command Center", true);
        originalDrone = namedChild(originalCommandCenter, "Drone", false);
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        model.addTreeModelListener(this);
        hierarchy.addTreeSelectionListener(event -> {
            var node = (DefaultMutableTreeNode) hierarchy.getLastSelectedPathComponent();
            selected = node == null ? null : (FarmItem) node.getUserObject();
            repaint();
        });
    }

    void startFlight(boolean scan, JLabel status) {
        if (flightTimer.isRunning()) {
            return;
        }
        Point2D.Double home = droneHome();
        if (home == null) {
            status.setText("Add a Drone to the Command Center");
            return;
        }
        if (!scan && (selected == null || selected == farm)) {
            status.setText("Select an item or container to visit");
            return;
        }
        flightStatus = status;
        route = scan ? scanRoute(home) : List.of(home, center(selected), home);
        dronePosition = home;
        nextWaypoint = 1;
        lastTick = System.nanoTime();
        status.setText(scan ? "Scanning farm" : "Flying to " + selected.properties().name());
        flightTimer.start();
        repaint();
    }

    static List<Point2D.Double> scanRoute(Point2D.Double home) {
        List<Point2D.Double> points = new ArrayList<>();
        points.add(home);
        for (int y = 25; y < HEIGHT; y += 50) {
            boolean eastbound = ((y - 25) / 50) % 2 == 0;
            points.add(new Point2D.Double(eastbound ? 10 : WIDTH - 10, y));
            points.add(new Point2D.Double(eastbound ? WIDTH - 10 : 10, y));
        }
        points.add(home);
        return List.copyOf(points);
    }

    private void advanceFlight() {
        long now = System.nanoTime();
        double remaining = (now - lastTick) / 1_000_000_000.0 * FLIGHT_SPEED;
        lastTick = now;
        while (nextWaypoint < route.size()) {
            Point2D.Double target = route.get(nextWaypoint);
            double distance = dronePosition.distance(target);
            if (distance > remaining) {
                double progress = remaining / distance;
                dronePosition = new Point2D.Double(
                        dronePosition.x + (target.x - dronePosition.x) * progress,
                        dronePosition.y + (target.y - dronePosition.y) * progress);
                break;
            }
            dronePosition = target;
            remaining -= distance;
            nextWaypoint++;
        }
        if (nextWaypoint == route.size()) {
            flightTimer.stop();
            flightStatus.setText("Flight complete. Drone at home.");
        }
        repaint();
    }

    Point2D.Double droneHome() {
        FarmItem commandCenter = originalCommandCenter != null && farm.children().contains(originalCommandCenter) ? originalCommandCenter
                : namedChild(farm, "Command Center", true);
        if (commandCenter == null) {
            return null;
        }
        FarmItem drone = originalDrone != null && commandCenter.children().contains(originalDrone) ? originalDrone
                : namedChild(commandCenter, "Drone", false);
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

    private static Point2D.Double center(FarmItem item) {
        Rectangle2D shape = bounds(item);
        return new Point2D.Double(shape.getCenterX(), shape.getCenterY());
    }

    private void farmChanged() {
        if (flightTimer.isRunning()) {
            flightTimer.stop();
            flightStatus.setText("Flight canceled because the farm changed");
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D plot = (Graphics2D) graphics.create();
        try {
            plot.clipRect(0, 0, WIDTH, HEIGHT);
            plot.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            plot.setColor(GROUND);
            plot.fillRect(0, 0, WIDTH, HEIGHT);
            plot.setColor(GRID);
            for (int x = 50; x < WIDTH; x += 50) {
                plot.drawLine(x, 0, x, HEIGHT);
            }
            for (int y = 50; y < HEIGHT; y += 50) {
                plot.drawLine(0, y, WIDTH, y);
            }
            drawElement(plot, farm);
            if (selected != null) {
                plot.setColor(SELECTION);
                plot.setStroke(new BasicStroke(2));
                plot.draw(bounds(selected));
            }
            Point2D.Double position = flightTimer.isRunning() ? dronePosition : droneHome();
            if (position != null) {
                plot.setColor(Color.WHITE);
                plot.fill(new Ellipse2D.Double(position.x - 8, position.y - 8, 16, 16));
                plot.setColor(SELECTION);
                plot.fill(new Ellipse2D.Double(position.x - 6, position.y - 6, 12, 12));
            }
        } finally {
            plot.dispose();
        }
    }

    private void drawElement(Graphics2D plot, FarmItem item) {
        Rectangle2D shape = bounds(item);
        if (!(item instanceof ItemContainer)) {
            plot.setColor(ITEM);
            plot.fill(shape);
        }
        plot.setColor(CONTAINER);
        plot.draw(shape);
        FarmProperties properties = item.properties();
        plot.drawString(properties.name(), (float) properties.x() + 5, (float) properties.y() + 15);
        for (FarmItem child : item.children()) {
            drawElement(plot, child);
        }
    }

    private static Rectangle2D bounds(FarmItem item) {
        FarmProperties properties = item.properties();
        return new Rectangle2D.Double(properties.x(), properties.y(), properties.length(), properties.width());
    }

    @Override
    public void treeNodesChanged(TreeModelEvent event) {
        farmChanged();
    }

    @Override
    public void treeNodesInserted(TreeModelEvent event) {
        farmChanged();
    }

    @Override
    public void treeNodesRemoved(TreeModelEvent event) {
        farmChanged();
    }

    @Override
    public void treeStructureChanged(TreeModelEvent event) {
        farmChanged();
    }
}

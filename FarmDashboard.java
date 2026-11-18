import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

public final class FarmDashboard {
    private static final FarmDashboard INSTANCE = new FarmDashboard();
    private JFrame window;

    private FarmDashboard() {}

    static void open() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(FarmDashboard::open);
            return;
        }
        if (INSTANCE.window == null) {
            INSTANCE.window = createWindow();
        }
        INSTANCE.window.setVisible(true);
        INSTANCE.window.toFront();
    }

    private static JFrame createWindow() {
        JFrame window = new JFrame("AgriFlight | Farm Dashboard");
        window.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        ItemContainer farm = sampleFarm();
        DefaultTreeModel model = new DefaultTreeModel(treeNode(farm));
        JTree hierarchy = new JTree(model);
        for (int row = 0; row < hierarchy.getRowCount(); row++) {
            hierarchy.expandRow(row);
        }
        JButton addItem = new JButton("Add Item");
        JButton addContainer = new JButton("Add Container");
        JButton edit = new JButton("Edit Properties");
        JButton delete = new JButton("Delete");
        JPanel controls = new JPanel();
        for (JButton button : List.of(addItem, addContainer, edit, delete)) {
            controls.add(button);
        }

        hierarchy.addTreeSelectionListener(event -> {
            var selected = (DefaultMutableTreeNode) hierarchy.getLastSelectedPathComponent();
            boolean isContainer = selected != null && selected.getUserObject() instanceof ItemContainer;
            addItem.setEnabled(isContainer);
            addContainer.setEnabled(isContainer);
            edit.setEnabled(selected != null);
            delete.setEnabled(selected != null && selected.getParent() != null);
        });
        addItem.addActionListener(event -> add(hierarchy, model, false));
        addContainer.addActionListener(event -> add(hierarchy, model, true));
        edit.addActionListener(event -> {
            var selected = (DefaultMutableTreeNode) hierarchy.getLastSelectedPathComponent();
            if (selected == null) {
                return;
            }
            FarmItem item = (FarmItem) selected.getUserObject();
            FarmProperties properties = editProperties(hierarchy, "Edit Properties", item.properties());
            if (properties != null) {
                item.update(properties);
                model.nodeChanged(selected);
            }
        });
        delete.addActionListener(event -> {
            var selected = (DefaultMutableTreeNode) hierarchy.getLastSelectedPathComponent();
            if (selected == null || !(selected.getParent() instanceof DefaultMutableTreeNode parent)) {
                return;
            }
            if (JOptionPane.showConfirmDialog(hierarchy, "Delete " + selected + " and any contents?",
                    "Delete Farm Element", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                ((ItemContainer) parent.getUserObject()).remove((FarmItem) selected.getUserObject());
                model.removeNodeFromParent(selected);
                hierarchy.setSelectionPath(new TreePath(parent.getPath()));
            }
        });
        FarmMap map = new FarmMap(farm, model, hierarchy);
        JButton visit = new JButton("Visit Selected");
        JButton scan = new JButton("Scan Farm");
        JLabel flightStatus = new JLabel("Drone ready");
        visit.addActionListener(event -> map.startFlight(false, flightStatus));
        scan.addActionListener(event -> map.startFlight(true, flightStatus));
        controls.add(visit);
        controls.add(scan);
        controls.add(flightStatus);
        hierarchy.setSelectionRow(0);
        JScrollPane treeScroll = new JScrollPane(hierarchy);
        treeScroll.setPreferredSize(new Dimension(230, FarmMap.HEIGHT));
        JSplitPane workspace = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                treeScroll, new JScrollPane(map));
        window.add(workspace, BorderLayout.CENTER);
        window.add(controls, BorderLayout.SOUTH);
        window.pack();
        Dimension screen = window.getToolkit().getScreenSize();
        window.setSize(Math.min(window.getWidth(), screen.width - 40),
                Math.min(window.getHeight(), screen.height - 100));
        window.setLocationByPlatform(true);
        return window;
    }

    private static void add(JTree hierarchy, DefaultTreeModel model, boolean container) {
        var selected = (DefaultMutableTreeNode) hierarchy.getLastSelectedPathComponent();
        if (selected == null || !(selected.getUserObject() instanceof ItemContainer parent)) {
            return;
        }
        String title = container ? "Add Container" : "Add Item";
        FarmProperties properties = editProperties(hierarchy, title,
                new FarmProperties("", BigDecimal.ZERO, 0, 0, 40, 30, 1));
        if (properties != null) {
            FarmItem child = container ? new ItemContainer(properties) : new Item(properties);
            parent.add(child);
            var node = treeNode(child);
            model.insertNodeInto(node, selected, selected.getChildCount());
            var path = new TreePath(node.getPath());
            hierarchy.setSelectionPath(path);
            hierarchy.scrollPathToVisible(path);
        }
    }

    private static FarmProperties editProperties(JTree hierarchy, String title, FarmProperties initial) {
        String[] labels = {"Name", "Price", "X", "Y", "Length", "Width", "Height"};
        String[] values = {initial.name(), initial.price().toPlainString(), "" + initial.x(),
                "" + initial.y(), "" + initial.length(), "" + initial.width(), "" + initial.height()};
        JTextField[] fields = new JTextField[labels.length];
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        for (int i = 0; i < labels.length; i++) {
            fields[i] = new JTextField(values[i], 14);
            JLabel label = new JLabel(labels[i]);
            label.setLabelFor(fields[i]);
            form.add(label);
            form.add(fields[i]);
        }
        while (JOptionPane.showConfirmDialog(hierarchy, form, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                return FarmProperties.parse(fields[0].getText(), fields[1].getText(), fields[2].getText(),
                        fields[3].getText(), fields[4].getText(), fields[5].getText(), fields[6].getText());
            } catch (IllegalArgumentException exception) {
                JOptionPane.showMessageDialog(hierarchy, exception.getMessage(),
                        "Invalid Properties", JOptionPane.ERROR_MESSAGE);
            }
        }
        return null;
    }

    static ItemContainer sampleFarm() {
        ItemContainer farm = new ItemContainer(new FarmProperties("Farm", BigDecimal.ZERO, 0, 0, 800, 600, 1));
        ItemContainer barn = new ItemContainer(new FarmProperties("Barn", BigDecimal.ZERO, 20, 40, 360, 300, 1));
        ItemContainer livestock = new ItemContainer(new FarmProperties("Livestock Area", BigDecimal.ZERO, 40, 70, 200, 140, 1));
        livestock.add(new Item(new FarmProperties("Cow", BigDecimal.ZERO, 70, 120, 50, 30, 1)));
        barn.add(livestock);
        barn.add(new Item(new FarmProperties("Milk Storage", BigDecimal.ZERO, 260, 70, 100, 60, 1)));
        farm.add(barn);

        ItemContainer storage = new ItemContainer(new FarmProperties("Storage Building", BigDecimal.ZERO, 420, 40, 360, 160, 1));
        storage.add(new Item(new FarmProperties("Tractor", BigDecimal.ZERO, 450, 90, 100, 50, 1)));
        storage.add(new Item(new FarmProperties("Tiller", BigDecimal.ZERO, 600, 90, 80, 50, 1)));
        farm.add(storage);

        ItemContainer commandCenter = new ItemContainer(new FarmProperties("Command Center", BigDecimal.ZERO, 20, 380, 260, 180, 1));
        commandCenter.add(new Item(new FarmProperties("Drone", BigDecimal.ZERO, 120, 440, 60, 60, 1)));
        farm.add(commandCenter);
        farm.add(new Item(new FarmProperties("Soy Crop", BigDecimal.ZERO, 420, 240, 360, 320, 1)));
        return farm;
    }

    static DefaultMutableTreeNode treeNode(FarmItem item) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(item, item instanceof ItemContainer);
        for (FarmItem child : item.children()) {
            node.add(treeNode(child));
        }
        return node;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(FarmDashboard::open);
    }
}

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
                plot.setStroke(new java.awt.BasicStroke(2));
                plot.draw(bounds(selected));
            }
            Point2D.Double position = flightTimer.isRunning() ? dronePosition : droneHome();
            if (position != null) {
                plot.setColor(Color.WHITE);
                plot.fill(new java.awt.geom.Ellipse2D.Double(position.x - 8, position.y - 8, 16, 16));
                plot.setColor(SELECTION);
                plot.fill(new java.awt.geom.Ellipse2D.Double(position.x - 6, position.y - 6, 12, 12));
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

record FarmProperties(String name, BigDecimal price, double x, double y,
                      double length, double width, double height) {
    static FarmProperties parse(String name, String price, String x, String y,
                                String length, String width, String height) {
        FarmProperties result;
        try {
            result = new FarmProperties(name.strip(), new BigDecimal(price.strip()),
                    Double.parseDouble(x), Double.parseDouble(y), Double.parseDouble(length),
                    Double.parseDouble(width), Double.parseDouble(height));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid number for price, coordinates, and dimensions.");
        }
        if (result.name().isBlank() || result.price().signum() < 0) {
            throw new IllegalArgumentException("Enter a name and a nonnegative price.");
        }
        if (!Double.isFinite(result.x()) || !Double.isFinite(result.y())
                || !Double.isFinite(result.length()) || !Double.isFinite(result.width())
                || !Double.isFinite(result.height()) || result.length() <= 0
                || result.width() <= 0 || result.height() <= 0) {
            throw new IllegalArgumentException("Coordinates must be finite and dimensions must be positive and finite.");
        }
        return result;
    }
}

interface FarmItem {
    FarmProperties properties();
    void update(FarmProperties properties);
    List<FarmItem> children();
}

abstract class FarmElement implements FarmItem {
    private FarmProperties properties;

    FarmElement(FarmProperties properties) {
        this.properties = properties;
    }

    @Override
    public FarmProperties properties() {
        return properties;
    }

    @Override
    public void update(FarmProperties properties) {
        this.properties = properties;
    }

    @Override
    public String toString() {
        return properties.name();
    }
}

final class Item extends FarmElement {
    Item(FarmProperties properties) {
        super(properties);
    }

    @Override
    public List<FarmItem> children() {
        return List.of();
    }
}

final class ItemContainer extends FarmElement {
    private final List<FarmItem> children = new ArrayList<>();

    ItemContainer(FarmProperties properties) {
        super(properties);
    }

    @Override
    public List<FarmItem> children() {
        return List.copyOf(children);
    }

    void add(FarmItem child) {
        children.add(child);
    }

    void remove(FarmItem child) {
        children.remove(child);
    }
}

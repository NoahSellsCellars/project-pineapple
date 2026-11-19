package com.agriflight;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/**
 * The farm dashboard window. This class is a Singleton: its constructor is private and the
 * only instance is available through {@link #getInstance()}.
 */
public final class FarmDashboard {
    private static final FarmDashboard INSTANCE = new FarmDashboard();

    /** Property names, in the order {@link FarmProperties#parse} takes them. */
    static final String[] PROPERTY_NAMES = {"Name", "Price", "Location-X", "Location-Y", "Length", "Width", "Height"};

    private JFrame window;

    private FarmDashboard() {}

    /** Returns the one and only dashboard. */
    public static FarmDashboard getInstance() {
        return INSTANCE;
    }

    /** Opens the dashboard window, creating it on first use and reusing it afterwards. */
    public void open() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::open);
            return;
        }
        if (window == null) {
            window = createWindow();
        }
        window.setVisible(true);
        window.toFront();
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
        JButton edit = new JButton("Edit ▾");
        JButton delete = new JButton("Delete");
        JPanel controls = new JPanel();
        for (JButton button : List.of(addItem, addContainer, edit, delete)) {
            controls.add(button);
        }

        List<Action> changeCommands = new ArrayList<>();
        for (int field = 0; field < PROPERTY_NAMES.length; field++) {
            int property = field;
            changeCommands.add(new AbstractAction("Change " + PROPERTY_NAMES[field]) {
                @Override
                public void actionPerformed(ActionEvent event) {
                    changeProperty(hierarchy, model, property);
                }
            });
        }
        JPopupMenu editPopup = new JPopupMenu();
        JMenu editMenu = new JMenu("Edit");
        for (Action command : changeCommands) {
            editPopup.add(command);
            editMenu.add(command);
        }
        edit.addActionListener(event -> editPopup.show(edit, 0, edit.getHeight()));
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(editMenu);
        window.setJMenuBar(menuBar);

        JLabel selectionSummary = new JLabel();
        selectionSummary.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        Runnable refreshSummary = () -> {
            var selected = (DefaultMutableTreeNode) hierarchy.getLastSelectedPathComponent();
            if (selected == null) {
                selectionSummary.setText("Nothing selected");
                return;
            }
            FarmItem item = (FarmItem) selected.getUserObject();
            selectionSummary.setText(item.properties().name() + "  |  Price: " + money(item.properties().price())
                    + "  |  Total value: " + money(item.totalPrice()));
        };

        hierarchy.addTreeSelectionListener(event -> {
            var selected = (DefaultMutableTreeNode) hierarchy.getLastSelectedPathComponent();
            boolean isContainer = selected != null && selected.getUserObject() instanceof ItemContainer;
            addItem.setEnabled(isContainer);
            addContainer.setEnabled(isContainer);
            edit.setEnabled(selected != null);
            editMenu.setEnabled(selected != null);
            delete.setEnabled(selected != null && selected.getParent() != null);
            refreshSummary.run();
        });
        model.addTreeModelListener(new TreeModelListener() {
            @Override
            public void treeNodesChanged(TreeModelEvent event) {
                refreshSummary.run();
            }

            @Override
            public void treeNodesInserted(TreeModelEvent event) {
                refreshSummary.run();
            }

            @Override
            public void treeNodesRemoved(TreeModelEvent event) {
                refreshSummary.run();
            }

            @Override
            public void treeStructureChanged(TreeModelEvent event) {
                refreshSummary.run();
            }
        });
        addItem.addActionListener(event -> add(hierarchy, model, false));
        addContainer.addActionListener(event -> add(hierarchy, model, true));
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
        window.add(selectionSummary, BorderLayout.NORTH);
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

    /** Changes one property of the selected item or item-container, such as its name or price. */
    private static void changeProperty(JTree hierarchy, DefaultTreeModel model, int property) {
        var selected = (DefaultMutableTreeNode) hierarchy.getLastSelectedPathComponent();
        if (selected == null) {
            return;
        }
        FarmItem item = (FarmItem) selected.getUserObject();
        String[] values = values(item.properties());
        String title = "Change " + PROPERTY_NAMES[property];
        String input = values[property];
        while (true) {
            input = (String) JOptionPane.showInputDialog(hierarchy,
                    "New " + PROPERTY_NAMES[property].toLowerCase() + " for " + item.properties().name() + ":",
                    title, JOptionPane.PLAIN_MESSAGE, null, null, input);
            if (input == null) {
                return;
            }
            values[property] = input;
            try {
                item.update(FarmProperties.parse(values));
                model.nodeChanged(selected);
                return;
            } catch (IllegalArgumentException exception) {
                JOptionPane.showMessageDialog(hierarchy, exception.getMessage(),
                        "Invalid " + PROPERTY_NAMES[property], JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static FarmProperties editProperties(JTree hierarchy, String title, FarmProperties initial) {
        String[] values = values(initial);
        JTextField[] fields = new JTextField[PROPERTY_NAMES.length];
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        for (int i = 0; i < PROPERTY_NAMES.length; i++) {
            fields[i] = new JTextField(values[i], 14);
            JLabel label = new JLabel(PROPERTY_NAMES[i]);
            label.setLabelFor(fields[i]);
            form.add(label);
            form.add(fields[i]);
        }
        while (JOptionPane.showConfirmDialog(hierarchy, form, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                String[] entered = new String[fields.length];
                for (int i = 0; i < fields.length; i++) {
                    entered[i] = fields[i].getText();
                }
                return FarmProperties.parse(entered);
            } catch (IllegalArgumentException exception) {
                JOptionPane.showMessageDialog(hierarchy, exception.getMessage(),
                        "Invalid Properties", JOptionPane.ERROR_MESSAGE);
            }
        }
        return null;
    }

    private static String[] values(FarmProperties properties) {
        return new String[] {properties.name(), properties.price().toPlainString(), "" + properties.x(),
                "" + properties.y(), "" + properties.length(), "" + properties.width(), "" + properties.height()};
    }

    private static String money(BigDecimal amount) {
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static ItemContainer sampleFarm() {
        ItemContainer farm = new ItemContainer(new FarmProperties("Farm", BigDecimal.ZERO, 0, 0, 800, 600, 1));
        ItemContainer barn = new ItemContainer(new FarmProperties("Barn", new BigDecimal("60000"), 20, 40, 360, 300, 1));
        ItemContainer livestock = new ItemContainer(new FarmProperties("Livestock Area", new BigDecimal("5000"), 40, 70, 200, 140, 1));
        livestock.add(new Item(new FarmProperties("Cow", new BigDecimal("1500"), 70, 120, 50, 30, 1)));
        barn.add(livestock);
        barn.add(new Item(new FarmProperties("Milk Storage", new BigDecimal("8000"), 260, 70, 100, 60, 1)));
        farm.add(barn);

        ItemContainer storage = new ItemContainer(new FarmProperties("Storage Building", new BigDecimal("40000"), 420, 40, 360, 160, 1));
        storage.add(new Item(new FarmProperties("Tractor", new BigDecimal("45000"), 450, 90, 100, 50, 1)));
        storage.add(new Item(new FarmProperties("Tiller", new BigDecimal("3500"), 600, 90, 80, 50, 1)));
        farm.add(storage);

        ItemContainer commandCenter = new ItemContainer(new FarmProperties("Command Center", new BigDecimal("20000"), 20, 380, 260, 180, 1));
        commandCenter.add(new Item(new FarmProperties("Drone", new BigDecimal("2500"), 120, 440, 60, 60, 1)));
        farm.add(commandCenter);
        farm.add(new Item(new FarmProperties("Soy Crop", new BigDecimal("12000"), 420, 240, 360, 320, 1)));
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
        FarmDashboard.getInstance().open();
    }
}

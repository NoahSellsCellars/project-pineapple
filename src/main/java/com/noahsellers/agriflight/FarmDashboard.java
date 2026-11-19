package com.noahsellers.agriflight;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
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

package com.agriflight;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.control.TreeItem;

/**
 * Applies changes to the farm and keeps the hierarchy view's tree items in step with it, so the
 * tree and the map update no matter whether a change came from the dashboard or from the API.
 * Call it only on the JavaFX application thread.
 */
final class FarmEditor {
    private final ItemContainer farm;
    private final TreeItem<FarmItem> root;
    private final List<Runnable> listeners = new ArrayList<>();

    FarmEditor(ItemContainer farm) {
        this.farm = farm;
        this.root = treeItem(farm);
    }

    ItemContainer farm() {
        return farm;
    }

    /** The root of the tree items that mirror the farm, for the hierarchy view. */
    TreeItem<FarmItem> root() {
        return root;
    }

    /** Runs the listener after every add, update or remove. */
    void onChange(Runnable listener) {
        listeners.add(listener);
    }

    /** Returns the tree item that shows this element, or null if it is no longer on the farm. */
    TreeItem<FarmItem> nodeOf(FarmItem item) {
        return find(root, item);
    }

    boolean contains(FarmItem item) {
        return nodeOf(item) != null;
    }

    TreeItem<FarmItem> add(ItemContainer parent, FarmItem child) {
        TreeItem<FarmItem> parentNode = require(parent);
        parent.add(child);
        TreeItem<FarmItem> node = treeItem(child);
        parentNode.getChildren().add(node);
        parentNode.setExpanded(true);
        changed();
        return node;
    }

    void update(FarmItem item, FarmProperties properties) {
        require(item);
        item.update(properties);
        changed();
    }

    /** Removes an element and everything inside it, and returns the container it was in. */
    ItemContainer remove(FarmItem item) {
        TreeItem<FarmItem> node = require(item);
        TreeItem<FarmItem> parentNode = node.getParent();
        if (parentNode == null) {
            throw new IllegalArgumentException("The root farm can't be deleted.");
        }
        ItemContainer parent = (ItemContainer) parentNode.getValue();
        parent.remove(item);
        parentNode.getChildren().remove(node);
        changed();
        return parent;
    }

    /** Builds an expanded tree item for an element and everything inside it. */
    static TreeItem<FarmItem> treeItem(FarmItem item) {
        TreeItem<FarmItem> node = new TreeItem<>(item);
        node.setExpanded(true);
        for (FarmItem child : item.children()) {
            node.getChildren().add(treeItem(child));
        }
        return node;
    }

    private static TreeItem<FarmItem> find(TreeItem<FarmItem> node, FarmItem item) {
        if (node.getValue() == item) {
            return node;
        }
        for (TreeItem<FarmItem> child : node.getChildren()) {
            TreeItem<FarmItem> found = find(child, item);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private TreeItem<FarmItem> require(FarmItem item) {
        TreeItem<FarmItem> node = nodeOf(item);
        if (node == null) {
            throw new IllegalArgumentException("That element is no longer on the farm.");
        }
        return node;
    }

    private void changed() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}

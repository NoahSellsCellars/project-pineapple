package com.agriflight;

import java.util.Enumeration;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

/**
 * Applies changes to the farm and keeps the tree model in step with it, so the hierarchy view
 * and the map update no matter whether a change came from the dashboard or from the API.
 * Call it only on the Swing event dispatch thread.
 */
final class FarmEditor {
    private final ItemContainer farm;
    private final DefaultTreeModel model;

    FarmEditor(ItemContainer farm, DefaultTreeModel model) {
        this.farm = farm;
        this.model = model;
    }

    ItemContainer farm() {
        return farm;
    }

    /** Returns the tree node that shows this element, or null if it is no longer on the farm. */
    DefaultMutableTreeNode nodeOf(FarmItem item) {
        Enumeration<TreeNode> nodes = ((DefaultMutableTreeNode) model.getRoot()).depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            var node = (DefaultMutableTreeNode) nodes.nextElement();
            if (node.getUserObject() == item) {
                return node;
            }
        }
        return null;
    }

    boolean contains(FarmItem item) {
        return nodeOf(item) != null;
    }

    DefaultMutableTreeNode add(ItemContainer parent, FarmItem child) {
        DefaultMutableTreeNode parentNode = require(parent);
        parent.add(child);
        DefaultMutableTreeNode node = FarmDashboard.treeNode(child);
        model.insertNodeInto(node, parentNode, parentNode.getChildCount());
        return node;
    }

    void update(FarmItem item, FarmProperties properties) {
        DefaultMutableTreeNode node = require(item);
        item.update(properties);
        model.nodeChanged(node);
    }

    /** Removes an element and everything inside it, and returns the container it was in. */
    ItemContainer remove(FarmItem item) {
        DefaultMutableTreeNode node = require(item);
        if (!(node.getParent() instanceof DefaultMutableTreeNode parentNode)) {
            throw new IllegalArgumentException("The root farm can't be deleted.");
        }
        ItemContainer parent = (ItemContainer) parentNode.getUserObject();
        parent.remove(item);
        model.removeNodeFromParent(node);
        return parent;
    }

    private DefaultMutableTreeNode require(FarmItem item) {
        DefaultMutableTreeNode node = nodeOf(item);
        if (node == null) {
            throw new IllegalArgumentException("That element is no longer on the farm.");
        }
        return node;
    }
}

package com.agriflight;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The farm dashboard window. This class is a Singleton: its constructor is private and the
 * only instance is available through {@link #getInstance()}. JavaFX creates its
 * {@link Application} object itself, so {@link FarmApp} is only a launcher that asks this
 * singleton to open its window.
 */
public final class FarmDashboard {
    private static final FarmDashboard INSTANCE = new FarmDashboard();

    /** Property names, in the order {@link FarmProperties#parse} takes them. */
    static final String[] PROPERTY_NAMES = {"Name", "Price", "Location-X", "Location-Y", "Length", "Width", "Height"};

    private Stage window;
    private FarmApiServer api;

    private FarmDashboard() {}

    /** Returns the one and only dashboard. */
    public static FarmDashboard getInstance() {
        return INSTANCE;
    }

    /** Opens the dashboard window, creating it on first use and reusing it afterwards. */
    public void open() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::open);
            return;
        }
        if (window == null) {
            window = createWindow();
        }
        window.show();
        window.toFront();
    }

    private Stage createWindow() {
        Stage stage = new Stage();
        stage.setTitle("AgriFlight | Farm Dashboard");

        ItemContainer farm = sampleFarm();
        FarmEditor editor = new FarmEditor(farm);
        TreeView<FarmItem> hierarchy = new TreeView<>(editor.root());
        hierarchy.setPrefWidth(230);
        FarmMap map = new FarmMap(farm, editor);

        Button addItem = new Button("Add Item");
        Button addContainer = new Button("Add Container");
        MenuButton edit = new MenuButton("Edit");
        Button delete = new Button("Delete");

        // One "Change ..." command per property, offered in both the Edit button and the Edit menu.
        Menu editMenu = new Menu("Edit");
        for (int field = 0; field < PROPERTY_NAMES.length; field++) {
            int property = field;
            for (List<MenuItem> commands : List.of(edit.getItems(), editMenu.getItems())) {
                MenuItem command = new MenuItem("Change " + PROPERTY_NAMES[field]);
                command.setOnAction(event -> changeProperty(stage, hierarchy, editor, property));
                commands.add(command);
            }
        }
        MenuBar menuBar = new MenuBar(editMenu);

        Label selectionSummary = new Label();
        Runnable refreshSummary = () -> {
            FarmItem item = selectedItem(hierarchy);
            selectionSummary.setText(item == null ? "Nothing selected"
                    : item.properties().name() + "  |  Price: " + money(item.properties().price())
                            + "  |  Total value: " + money(item.totalPrice()));
        };

        hierarchy.getSelectionModel().selectedItemProperty().addListener((observable, old, node) -> {
            FarmItem item = node == null ? null : node.getValue();
            boolean isContainer = item instanceof ItemContainer;
            addItem.setDisable(!isContainer);
            addContainer.setDisable(!isContainer);
            edit.setDisable(item == null);
            editMenu.setDisable(item == null);
            delete.setDisable(node == null || node.getParent() == null);
            map.setSelected(item);
            refreshSummary.run();
        });
        editor.onChange(() -> {
            hierarchy.refresh();
            refreshSummary.run();
        });

        addItem.setOnAction(event -> add(stage, hierarchy, editor, false));
        addContainer.setOnAction(event -> add(stage, hierarchy, editor, true));
        delete.setOnAction(event -> {
            TreeItem<FarmItem> node = hierarchy.getSelectionModel().getSelectedItem();
            if (node == null || node.getParent() == null) {
                return;
            }
            TreeItem<FarmItem> parent = node.getParent();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete " + node.getValue() + " and any contents?", ButtonType.YES, ButtonType.NO);
            confirm.initOwner(stage);
            confirm.setTitle("Delete Farm Element");
            confirm.setHeaderText(null);
            if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                editor.remove(node.getValue());
                hierarchy.getSelectionModel().select(parent);
            }
        });

        Label flightStatus = new Label();
        flightStatus.textProperty().bind(map.statusProperty());
        Button visit = new Button("Visit Selected");
        Button scan = new Button("Scan Farm");
        visit.setOnAction(event -> map.startVisit(map.selected()));
        scan.setOnAction(event -> map.startScan());

        HBox controls = new HBox(8, addItem, addContainer, edit, delete, visit, scan, flightStatus);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(8, 10, 8, 10));

        Label apiStatus = new Label(startApi(editor, map));
        BorderPane topBar = new BorderPane();
        topBar.setCenter(selectionSummary);
        BorderPane.setAlignment(selectionSummary, Pos.CENTER_LEFT);
        topBar.setRight(apiStatus);
        topBar.setPadding(new Insets(6, 10, 6, 10));

        BorderPane root = new BorderPane();
        root.setTop(new VBox(menuBar, topBar));
        root.setLeft(hierarchy);
        root.setCenter(new ScrollPane(map));
        root.setBottom(controls);

        hierarchy.getSelectionModel().select(0);

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        stage.setScene(new Scene(root, Math.min(1080, screen.getWidth() - 40), Math.min(740, screen.getHeight() - 40)));
        stage.setOnHidden(event -> {
            if (api != null) {
                api.stop();
            }
        });
        return stage;
    }

    /**
     * Starts the local API for other programs, such as the drone controller. Run with
     * -Dagriflight.api=off to disable it, or -Dagriflight.api.port=N to change the port.
     */
    private String startApi(FarmEditor editor, FarmMap map) {
        if ("off".equalsIgnoreCase(System.getProperty("agriflight.api"))) {
            return "API off";
        }
        int port = Integer.getInteger("agriflight.api.port", FarmApiServer.DEFAULT_PORT);
        try {
            FarmApiServer server = new FarmApiServer(editor, map, Platform::runLater);
            int bound = server.start(port);
            api = server;
            String url = "http://127.0.0.1:" + bound + "/api";
            System.out.println("AgriFlight API listening at " + url);
            return "API: " + url;
        } catch (IOException | RuntimeException exception) {
            System.err.println("AgriFlight API not started on port " + port + ": " + exception.getMessage());
            return "API unavailable (port " + port + " in use)";
        }
    }

    private static FarmItem selectedItem(TreeView<FarmItem> hierarchy) {
        TreeItem<FarmItem> node = hierarchy.getSelectionModel().getSelectedItem();
        return node == null ? null : node.getValue();
    }

    /** Adds an item or item-container to the selected item-container. */
    private static void add(Window owner, TreeView<FarmItem> hierarchy, FarmEditor editor, boolean container) {
        if (!(selectedItem(hierarchy) instanceof ItemContainer parent)) {
            return;
        }
        String title = container ? "Add Container" : "Add Item";
        FarmProperties properties = editProperties(owner, title, new FarmProperties("", BigDecimal.ZERO, 0, 0, 40, 30, 1));
        if (properties != null) {
            FarmItem child = container ? new ItemContainer(properties) : new Item(properties);
            TreeItem<FarmItem> node = editor.add(parent, child);
            hierarchy.getSelectionModel().select(node);
            hierarchy.scrollTo(hierarchy.getRow(node));
        }
    }

    /** Changes one property of the selected item or item-container, such as its name or price. */
    private static void changeProperty(Window owner, TreeView<FarmItem> hierarchy, FarmEditor editor, int property) {
        FarmItem item = selectedItem(hierarchy);
        if (item == null) {
            return;
        }
        String[] values = values(item.properties());
        String input = values[property];
        while (true) {
            TextInputDialog dialog = new TextInputDialog(input);
            dialog.initOwner(owner);
            dialog.setTitle("Change " + PROPERTY_NAMES[property]);
            dialog.setHeaderText(null);
            dialog.setContentText("New " + PROPERTY_NAMES[property].toLowerCase() + " for " + item.properties().name() + ":");
            Optional<String> answer = dialog.showAndWait();
            if (answer.isEmpty()) {
                return;
            }
            input = answer.get();
            values[property] = input;
            try {
                editor.update(item, FarmProperties.parse(values));
                return;
            } catch (IllegalArgumentException exception) {
                showError(owner, "Invalid " + PROPERTY_NAMES[property], exception.getMessage());
            }
        }
    }

    /** Shows a form for all seven properties and returns them, or null if the user cancels. */
    private static FarmProperties editProperties(Window owner, String title, FarmProperties initial) {
        String[] values = values(initial);
        TextField[] fields = new TextField[PROPERTY_NAMES.length];
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(6);
        for (int i = 0; i < PROPERTY_NAMES.length; i++) {
            fields[i] = new TextField(values[i]);
            Label label = new Label(PROPERTY_NAMES[i]);
            label.setLabelFor(fields[i]);
            form.addRow(i, label, fields[i]);
        }
        Dialog<FarmProperties> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(title);
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        FarmProperties[] parsed = new FarmProperties[1];
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(ActionEvent.ACTION, event -> {
            String[] entered = new String[fields.length];
            for (int i = 0; i < fields.length; i++) {
                entered[i] = fields[i].getText();
            }
            try {
                parsed[0] = FarmProperties.parse(entered);
            } catch (IllegalArgumentException exception) {
                event.consume(); // keeps the form open so the user can fix the value
                showError(dialog.getDialogPane().getScene().getWindow(), "Invalid Properties", exception.getMessage());
            }
        });
        dialog.setResultConverter(button -> button == ButtonType.OK ? parsed[0] : null);
        Platform.runLater(fields[0]::requestFocus);
        return dialog.showAndWait().orElse(null);
    }

    private static void showError(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    static String[] values(FarmProperties properties) {
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

    /** Starts JavaFX, which then opens the singleton dashboard through {@link FarmApp}. */
    public static void main(String[] args) {
        Application.launch(FarmApp.class, args);
    }
}

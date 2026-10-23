package com.noahsellers.controller;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

public class DashboardController {

    @FXML
    private TextField taskField;

    @FXML
    private ComboBox<String> priorityCombo;

    @FXML
    private ListView<String> taskList;

    @FXML
    private Label statusLabel;


    /*
     * Runs automatically after the FXML file is loaded
     */
    @FXML
    private void initialize() {

        priorityCombo.getItems().addAll(
                "Low",
                "Medium",
                "High"
        );

        priorityCombo.setValue("Medium");
    }


    /*
     * ADD TASK
     */
    @FXML
    private void handleAdd() {

        String task = taskField.getText().trim();
        String priority = priorityCombo.getValue();

        if (task.isEmpty()) {

            statusLabel.setText(
                    "Please enter a task."
            );

            return;
        }

        String taskItem =
                task + "  [" + priority + "]";

        taskList.getItems().add(taskItem);

        taskField.clear();

        statusLabel.setText(
                "Task added successfully."
        );
    }


    /*
     * COMPLETE TASK
     */
    @FXML
    private void handleComplete() {

        String selectedTask =
                taskList.getSelectionModel()
                        .getSelectedItem();

        if (selectedTask == null) {

            statusLabel.setText(
                    "Select a task first."
            );

            return;
        }

        taskList.getItems().remove(selectedTask);

        statusLabel.setText(
                "Completed: " + selectedTask
        );
    }


    /*
     * CLEAR ALL TASKS
     */
    @FXML
    private void handleClear() {

        taskList.getItems().clear();

        statusLabel.setText(
                "All tasks cleared."
        );
    }
}
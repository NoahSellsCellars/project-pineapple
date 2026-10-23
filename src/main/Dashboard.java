import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;

import javafx.scene.Parent;
import javafx.stage.Stage;
public class Dashboard extends Application {
}

    @Override
    public void start(Stage primaryStage) {

        try {

            Parent root = FXMLLoader.load(
                    getClass().getResource("DashboardView.fxml")
            );

            Scene scene = new Scene(root, 600, 450);

            primaryStage.setTitle("Study Task Planner");
            primaryStage.setScene(scene);
            primaryStage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
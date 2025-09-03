package algobot;

//import javafx.animation.*;
//import javafx.stage.Screen;
import javafx.application.Application;
//import javafx.application.Platform;
//import javafx.geometry.Insets;
//import javafx.geometry.Pos;
//import javafx.geometry.VPos;
import javafx.scene.Scene;
//import javafx.scene.canvas.Canvas;
//import javafx.scene.canvas.GraphicsContext;
//import javafx.scene.control.*;
//import javafx.scene.text.TextAlignment;
//import javafx.scene.text.Text;
//import javafx.util.Callback;
//import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
//import javafx.scene.input.KeyCode;
//import javafx.scene.input.MouseEvent;
//import javafx.event.EventHandler;
//import javafx.scene.Node;
//import javafx.scene.layout.HBox;
//import javafx.scene.paint.Color;
//import javafx.scene.paint.LinearGradient;
//import javafx.scene.paint.Stop;
//import javafx.scene.paint.Paint;
//import javafx.scene.shape.*;
//import javafx.scene.text.Font;
//import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
//import javafx.util.Duration;
//import java.util.*;
//import java.util.function.BiConsumer;

public class AlgoBotApplication extends Application {
    private Pane currentVisualizerPane; 
    private Stage primaryStage;
 
    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("AlgoBot - Algorithm Visualizer");
        
        // Create main stack pane for page transitions
        common.mainStackPane = new StackPane();
        
        // Create all pages
        common.home.createHomePage();
        common.sort.createSortingPage(); // Build sorting page early so handler works
        common.mSort.createMergeSortPage(); // Dedicated merge sort recursive tree visualization
        common.graph.createGraphPage();
        common.net.createNetworkPage();
        common.ds.createDataStructurePage();
        common.maze.createMazePage();
        common.about.createAboutPage();
        
        // Start with home page
        common.mainStackPane.getChildren().add(common.homeContainer);
        
	    Scene scene = new Scene(common.mainStackPane, 1200, 800);
	    // Attach modern stylesheet (guard against missing resource to prevent startup crash)
	    try {
	        java.net.URL css = getClass().getResource("/modern-styles.css");
	        if (css != null) {
	            scene.getStylesheets().add(css.toExternalForm());
	        } else {
	            System.err.println("[WARN] modern-styles.css not found on classpath; continuing without stylesheet.");
	        }
	    } catch (Exception ex) {
	        System.err.println("[WARN] Failed to load modern-styles.css: " + ex.getMessage());
	    }
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.setMaximized(true); // Start maximized for better experience
        
        // Disable full-screen exit hint popup
        primaryStage.setFullScreenExitHint("");
        primaryStage.setFullScreen(true); // Open in full screen mode
        
        // Add key handler for full screen toggle (F11) and exit (Escape)
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case F11:
                    primaryStage.setFullScreen(!primaryStage.isFullScreen());
                    break;
                case ESCAPE:
                    if (primaryStage.isFullScreen()) {
                        primaryStage.setFullScreen(false);
                    }
                    break;
                default:
                    break;
            }
        });
        
        primaryStage.show();
    }
    
        public static void main(String[] args) {
            launch(args);
        }
}

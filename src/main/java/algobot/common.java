package algobot;

import java.util.Locale;

import javafx.animation.*;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.util.Duration;

public class common {
	public static StackPane mainStackPane;
	public static Pane currentVisualizerPane;
	public static VBox homeContainer;
	public static VBox aboutContainer;
	public static VBox developerContainer;
	public static VBox sortingContainer;
	public static VBox graphContainer;
    public static VBox networkContainer;
    public static VBox dataStructureContainer;
    public static VBox mazeContainer; 
    public static VBox mergeSortContainer; 
    public static Label sortingStatusLabel;
    
    // Entry point for application
    public static HomeManager home = new HomeManager();
    public static SortingManager sort = new SortingManager();
    public static MergeSortManager mSort = new MergeSortManager();
    public static GraphManager graph = new GraphManager();
    public static NetworkManager net = new NetworkManager();
    public static MazeManager maze = new MazeManager();
    public static DataStructureManager ds = new DataStructureManager();
    public static About about = new About();
	
	public static void transitionToPage(VBox targetPage) {
        if (mainStackPane.getChildren().contains(targetPage)) {
            return; // Already on this page
        }

        // If navigating to graph page, reset it so it's always clean
        if (targetPage == common.graphContainer) {
            graph.clearGraph();
        }
        
        // Get current page
        VBox currentPage = (VBox) mainStackPane.getChildren().get(0);
        
        // Add target page behind current page
        mainStackPane.getChildren().add(0, targetPage);
        
        // Create fade out animation for current page
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), currentPage);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        
        // Create fade in animation for target page
        targetPage.setOpacity(0.0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), targetPage);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        
        // Start animations
        fadeOut.play();
        fadeIn.play();
        
        // Remove current page after animation
        fadeOut.setOnFinished(e -> {
            mainStackPane.getChildren().remove(currentPage);
            currentPage.setOpacity(1.0);
        });
    }
	
	public static void transitionToPage(Pane targetPage) { // Modified to accept Pane
        if (common.mainStackPane.getChildren().contains(targetPage)) {
            return; // Already on this page
        }

        // If navigating to graph page, reset it so it's always clean
        // This check needs to be more robust if other visualizers have reset methods
        if (targetPage == common.graphContainer) {
        	graph.clearGraph();
        } 
        else if (targetPage == common.networkContainer) {
            net.clearNetwork();
        }
        else if (targetPage == common.dataStructureContainer) {
            ds.clear();
        }
        
        // Get current page
        Pane currentPage = (Pane) common.mainStackPane.getChildren().get(0); // Changed to Pane
        
        // Add target page behind current page
        common.mainStackPane.getChildren().add(0, targetPage);
        
        // Create fade out animation for current page
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), currentPage);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        
        // Create fade in animation for target page
        targetPage.setOpacity(0.0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), targetPage);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        
        // Start animations
        fadeOut.play();
        fadeIn.play();
        
        // Remove current page after animation
        fadeOut.setOnFinished(e -> {
        	common.mainStackPane.getChildren().remove(currentPage);
            currentPage.setOpacity(1.0);
        });
    }
	
	public static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
	public static String fmt(double x){ if (Math.abs(x - Math.rint(x)) < 1e-9) return String.valueOf((long)Math.rint(x)); return String.format(Locale.US, "%.2f", x); }
}

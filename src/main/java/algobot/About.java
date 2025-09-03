package algobot;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

public class About {
	public void createAboutPage() {
        common.aboutContainer = new VBox(30);
        common.aboutContainer.setAlignment(Pos.CENTER);
        common.aboutContainer.setPadding(new Insets(40));
        
        // Dark gradient background similar to other pages
        LinearGradient gradient = new LinearGradient(0, 0, 1, 1, true, null,
            new Stop(0, Color.web("#0c0c0c")),
            new Stop(0.3, Color.web("#1a1a2e")),
            new Stop(0.6, Color.web("#16213e")),
            new Stop(1, Color.web("#0f051d"))
        );
        
        common.aboutContainer.setBackground(new Background(new BackgroundFill(gradient, null, null)));
        
        // Back button
        Button backButton = new Button("← BACK TO HOME");
        backButton.setFont(Font.font("SF Pro Text", FontWeight.BOLD, 16));
        backButton.setStyle(
            "-fx-background-color: rgba(255, 255, 255, 0.25);" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 18;" +
            "-fx-border-radius: 18;" +
            "-fx-border-color: rgba(255, 255, 255, 0.3);" +
            "-fx-border-width: 1;" +
            "-fx-padding: 12 24;" +
            "-fx-letter-spacing: 1px;" +
            "-fx-cursor: hand;"
        );
        
        backButton.setOnAction(e -> {
            if (common.mainStackPane.getChildren().contains(common.graphContainer)) {
//            	GraphManager gm = new GraphManager();
//                graph.clearGraph();
//                graph.unlockAlgorithmSelection();
            }
            
            common.transitionToPage(common.homeContainer);
        });
        
        // Add stylish hover effects to back button
        backButton.setOnMouseEntered(e -> {
            ScaleTransition scaleUp = new ScaleTransition(Duration.millis(200), backButton);
            scaleUp.setToX(1.08);
            scaleUp.setToY(1.08);
            scaleUp.play();
            
            // Add glow effect on hover
            DropShadow glowEffect = new DropShadow();
            glowEffect.setColor(Color.web("#ffffff", 0.6));
            glowEffect.setOffsetX(0);
            glowEffect.setOffsetY(0);
            glowEffect.setRadius(20);
            glowEffect.setSpread(0.3);
            backButton.setEffect(glowEffect);
            
            // Change button style on hover
            backButton.setStyle(
                "-fx-background-color: rgba(255, 255, 255, 0.35);" +
                "-fx-text-fill: white;" +
                "-fx-background-radius: 18;" +
                "-fx-border-radius: 18;" +
                "-fx-border-color: rgba(255, 255, 255, 0.5);" +
                "-fx-border-width: 1.5;" +
                "-fx-padding: 12 24;" +
                "-fx-letter-spacing: 1px;" +
                "-fx-cursor: hand;"
            );
        });
        
        backButton.setOnMouseExited(e -> {
            ScaleTransition scaleDown = new ScaleTransition(Duration.millis(200), backButton);
            scaleDown.setToX(1.0);
            scaleDown.setToY(1.0);
            scaleDown.play();
            
            // Remove glow effect
            backButton.setEffect(null);
            
            // Restore original button style
            backButton.setStyle(
                "-fx-background-color: rgba(255, 255, 255, 0.25);" +
                "-fx-text-fill: white;" +
                "-fx-background-radius: 18;" +
                "-fx-border-radius: 18;" +
                "-fx-border-color: rgba(255, 255, 255, 0.3);" +
                "-fx-border-width: 1;" +
                "-fx-padding: 12 24;" +
                "-fx-letter-spacing: 1px;" +
                "-fx-cursor: hand;"
            );
        });
        
        HBox backContainer = new HBox();
        backContainer.setAlignment(Pos.CENTER_LEFT);
        backContainer.getChildren().add(backButton);
        
        // Project title
        Label projectTitle = new Label("ALGOBOT");
        projectTitle.setFont(Font.font("SF Pro Display", FontWeight.EXTRA_BOLD, 64));
        projectTitle.setTextFill(Color.web("#6a5af9"));
        projectTitle.setStyle("-fx-letter-spacing: 3px;");
        
        DropShadow titleShadow = new DropShadow();
        titleShadow.setColor(Color.web("#6a5af9"));
        titleShadow.setOffsetX(0);
        titleShadow.setOffsetY(0);
        titleShadow.setRadius(20);
        titleShadow.setSpread(0.3);
        projectTitle.setEffect(titleShadow);
        
        // Project description
        Label description = new Label(
            "AlgoBot is an interactive algorithm visualization platform designed to help students\n" +
            "and developers understand complex data structures and algorithms through visual,\n" +
            "step-by-step animations. Built with JavaFX, it provides an intuitive interface\n" +
            "for exploring sorting algorithms, graph algorithms, and data structures."
        );
        description.setFont(Font.font("SF Pro Text", FontWeight.MEDIUM, 18));
        description.setTextFill(Color.color(1, 1, 1, 0.85));
        description.setStyle("-fx-text-alignment: center; -fx-line-spacing: 6px; -fx-padding: 0 0 0 30;");
        description.setWrapText(true);
        description.setMaxWidth(800);
        
        // Developers section
        Label developersTitle = new Label("DEVELOPMENT TEAM");
        developersTitle.setFont(Font.font("SF Pro Display", FontWeight.BOLD, 28));
        developersTitle.setTextFill(Color.WHITE);
        developersTitle.setStyle("-fx-letter-spacing: 2px;");
        
        VBox developersBox = new VBox(15);
        developersBox.setAlignment(Pos.CENTER);
        developersBox.setPadding(new Insets(0, 0, 0, 40)); // Add left padding to shift text to the right
        
        String[] developers = {
            "Sheikh Farhan Adib Auvro",
            "Abu Bakar Alam", 
            "G.M. Noor-Ul Islam Labib"
        };
        
        String[] colors = {"#ff6b6b", "#4ecdc4", "#45b7d1"};
        
        for (int i = 0; i < developers.length; i++) {
            Label devLabel = new Label((i + 1) + ". " + developers[i]);
            devLabel.setFont(Font.font("SF Pro Text", FontWeight.MEDIUM, 20));
            devLabel.setTextFill(Color.web(colors[i]));
            devLabel.setStyle("-fx-letter-spacing: 1px;");
            
            DropShadow devShadow = new DropShadow();
            devShadow.setColor(Color.web(colors[i]));
            devShadow.setOffsetX(0);
            devShadow.setOffsetY(0);
            devShadow.setRadius(10);
            devShadow.setSpread(0.2);
            devLabel.setEffect(devShadow);
            
            developersBox.getChildren().add(devLabel);
        }
        
        // Version info
        Label versionLabel = new Label("Version 1.0 | August 2025");
        versionLabel.setFont(Font.font("SF Pro Text", FontWeight.NORMAL, 14));
        versionLabel.setTextFill(Color.color(1, 1, 1, 0.5));
        versionLabel.setStyle("-fx-letter-spacing: 2px;");
        
        common.aboutContainer.getChildren().addAll(
            backContainer, projectTitle, description, 
            developersTitle, developersBox, versionLabel
        );
    }
}

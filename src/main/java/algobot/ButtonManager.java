package algobot;

import java.util.Locale;

import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

public class ButtonManager {
	public static HBox createTitleWithBackButton(String title) {
        HBox titleContainer = new HBox(20);
        titleContainer.setAlignment(Pos.CENTER_LEFT);
        // Add extra top padding so back button and title sit lower and remain clearly visible
        titleContainer.setPadding(new Insets(40, 0, 24, 0));
        
        // Enhanced back button with proper styling
        Button backButton = new Button("← BACK TO HOME");
        backButton.setStyle(
            "-fx-background-color: linear-gradient(to bottom, rgba(20,25,35,0.95), rgba(15,20,30,0.95)); " +
            "-fx-text-fill: #00ffff; " +
            "-fx-font-weight: bold; " +
            "-fx-font-family: 'SF Pro Text'; " +
            "-fx-background-radius: 8; " +
            "-fx-border-radius: 8; " +
            "-fx-border-color: #00ffff77; " +
            "-fx-border-width: 1.5; " +
            "-fx-padding: 8 16; " +
            "-fx-font-size: 12px; " +
            "-fx-letter-spacing: 0.8px; " +
            "-fx-effect: dropshadow(gaussian, #00ffff44, 6, 0, 0, 2);"
        );
        
        backButton.setOnAction(e -> {
            // Proactively reset the current visualizer before returning home
            String T = title.toUpperCase(Locale.ROOT);
//            if (T.contains("MAZE")) {
//                resetMazeState();
//            } else if (T.contains("GRAPH")) {
//                clearGraph();
//            } else if (T.contains("SORTING")) {
//                clearSortingState();
//            } else if (T.contains("MERGE")) {
//                clearMergeSortState();
//            } else if (T.contains("DATA STRUCTURES") || T.contains("STRUCTURES")) {
//                resetDataStructureUI();
//            } else if (T.contains("MAX FLOW") || T.contains("MIN CUT") || T.contains("NETWORK")) {
//                clearNetwork();
//            }
            
            common.transitionToPage(common.homeContainer);
        });
        
        // Add hover effects to back button
        backButton.setOnMouseEntered(e -> {
            ScaleTransition scaleUp = new ScaleTransition(Duration.millis(150), backButton);
            scaleUp.setToX(1.05);
            scaleUp.setToY(1.05);
            scaleUp.play();
            
            // Add glow effect on hover
            DropShadow glowEffect = new DropShadow();
            glowEffect.setColor(Color.web("#00ffff", 0.8));
            glowEffect.setOffsetX(0);
            glowEffect.setOffsetY(0);
            glowEffect.setRadius(15);
            glowEffect.setSpread(0.4);
            backButton.setEffect(glowEffect);
        });
        
        backButton.setOnMouseExited(e -> {
            ScaleTransition scaleDown = new ScaleTransition(Duration.millis(150), backButton);
            scaleDown.setToX(1.0);
            scaleDown.setToY(1.0);
            scaleDown.play();
            
            // Remove glow effect
            backButton.setEffect(null);
        });
        
        // Enhanced title with better visibility and effects
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("SF Pro Display", FontWeight.BOLD, 36));
        titleLabel.setTextFill(Color.web("#e0ffff"));
        titleLabel.setStyle("-fx-letter-spacing: 3px;");
        
        // Create layered glow effect
        DropShadow innerGlow = new DropShadow();
        innerGlow.setColor(Color.web("#00ffff"));
        innerGlow.setOffsetX(0);
        innerGlow.setOffsetY(0);
        innerGlow.setRadius(12);
        innerGlow.setSpread(0.3);
        
        DropShadow outerGlow = new DropShadow();
        outerGlow.setColor(Color.web("#4ecdc4"));
        outerGlow.setOffsetX(0);
        outerGlow.setOffsetY(0);
        outerGlow.setRadius(20);
        outerGlow.setSpread(0.1);
        outerGlow.setInput(innerGlow);
        
        DropShadow textShadow = new DropShadow();
        textShadow.setColor(Color.color(0, 0, 0, 0.6));
        textShadow.setOffsetX(0);
        textShadow.setOffsetY(2);
        textShadow.setRadius(6);
        textShadow.setInput(outerGlow);
        
        titleLabel.setEffect(textShadow);
        
        // Add subtle animation on hover
        titleLabel.setOnMouseEntered(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(200), titleLabel);
            scale.setToX(1.02);
            scale.setToY(1.02);
            scale.play();
            
            // Enhanced glow on hover
            DropShadow hoverGlow = new DropShadow();
            hoverGlow.setColor(Color.web("#00ffff"));
            hoverGlow.setOffsetX(0);
            hoverGlow.setOffsetY(0);
            hoverGlow.setRadius(25);
            hoverGlow.setSpread(0.4);
            hoverGlow.setInput(textShadow);
            titleLabel.setEffect(hoverGlow);
        });
        
        titleLabel.setOnMouseExited(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(200), titleLabel);
            scale.setToX(1.0);
            scale.setToY(1.0);
            scale.play();
            titleLabel.setEffect(textShadow);
        });
        
        titleContainer.getChildren().addAll(backButton, titleLabel);
        
        return titleContainer;
    }
	
	public static Button createStyledButton(String text, String color) {
        Button button = new Button(text);
        button.setMinWidth(100);
        button.setPrefWidth(Region.USE_COMPUTED_SIZE);
        
        String baseStyle = 
            "-fx-background-color: linear-gradient(to bottom, rgba(20,25,35,0.95), rgba(15,20,30,0.95)); " +
            "-fx-text-fill: " + color + "; " +
            "-fx-font-weight: bold; " +
            "-fx-font-family: 'SF Pro Text'; " +
            "-fx-background-radius: 8; " +
            "-fx-border-radius: 8; " +
            "-fx-border-color: " + color + "77; " +
            "-fx-border-width: 1.5; " +
            "-fx-padding: 10 18; " +
            "-fx-font-size: 13px; " +
            "-fx-letter-spacing: 0.8px; " +
            "-fx-effect: dropshadow(gaussian, " + color + "44, 6, 0, 0, 2);";
            
        String hoverStyle = 
            "-fx-background-color: linear-gradient(to right, " + color + "22, rgba(15,20,30,0.98)); " +
            "-fx-text-fill: white; " +
            "-fx-font-weight: bold; " +
            "-fx-font-family: 'SF Pro Text'; " +
            "-fx-background-radius: 8; " +
            "-fx-border-radius: 8; " +
            "-fx-border-color: " + color + "; " +
            "-fx-border-width: 2; " +
            "-fx-padding: 10 18; " +
            "-fx-font-size: 13px; " +
            "-fx-letter-spacing: 0.8px; " +
            "-fx-effect: dropshadow(gaussian, " + color + "88, 12, 0, 0, 4);";
        
        button.setStyle(baseStyle);

        button.setOnMouseEntered(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(150), button);
            scale.setToX(1.05);
            scale.setToY(1.05);
            scale.play();
            button.setStyle(hoverStyle);
        });

        button.setOnMouseExited(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(150), button);
            scale.setToX(1.0);
            scale.setToY(1.0);
            scale.play();
            button.setStyle(baseStyle);
        });

        return button;
    }
	
	public static Button createCompactButton(String text, String color) {
	        Button button = new Button(text);
	        button.setMinWidth(75); // smaller minimum width
	        button.setPrefWidth(Region.USE_COMPUTED_SIZE);
        
	        String baseStyle = 
	            "-fx-background-color: linear-gradient(to bottom, rgba(20,25,35,0.95), rgba(15,20,30,0.95)); " +
	            "-fx-text-fill: " + color + "; " +
	            "-fx-font-weight: bold; " +
	            "-fx-font-family: 'SF Pro Text'; " +
	            "-fx-background-radius: 6; " +
	            "-fx-border-radius: 6; " +
	            "-fx-border-color: " + color + "77; " +
	            "-fx-border-width: 1.5; " +
	            "-fx-padding: 6 12; " + // smaller padding
	            "-fx-font-size: 11px; " + // smaller font
	            "-fx-letter-spacing: 0.5px; " +
	            "-fx-effect: dropshadow(gaussian, " + color + "44, 4, 0, 0, 1);"; // smaller shadow
            
	        String hoverStyle = 
	            "-fx-background-color: linear-gradient(to right, " + color + "22, rgba(15,20,30,0.98)); " +
	            "-fx-text-fill: white; " +
	            "-fx-font-weight: bold; " +
	            "-fx-font-family: 'SF Pro Text'; " +
	            "-fx-background-radius: 6; " +
	            "-fx-border-radius: 6; " +
	            "-fx-border-color: " + color + "; " +
	            "-fx-border-width: 2; " +
	            "-fx-padding: 6 12; " +
	            "-fx-font-size: 11px; " +
	            "-fx-letter-spacing: 0.5px; " +
	            "-fx-effect: dropshadow(gaussian, " + color + "88, 8, 0, 0, 2);";
        
	        button.setStyle(baseStyle);
	        // Store styles for later enable/disable toggling
	        button.getProperties().put("baseStyle", baseStyle);
	        button.getProperties().put("hoverStyle", hoverStyle);
	        button.getProperties().put("disabledStyle",
	            "-fx-background-color: linear-gradient(to bottom, rgba(25,32,42,0.92), rgba(15,20,28,0.92)); " +
	            "-fx-text-fill: #00ffff99; -fx-font-weight: bold; -fx-font-family: 'SF Pro Text'; " +
	            "-fx-background-radius:6; -fx-border-radius:6; -fx-border-color:#00ffff33; -fx-border-width:1.2; " +
	            "-fx-padding:6 12; -fx-font-size:11px; -fx-letter-spacing:0.5px; -fx-opacity:0.55;"
	        );

	        button.setOnMouseEntered(e -> {
	            ScaleTransition scale = new ScaleTransition(Duration.millis(120), button);
	            scale.setToX(1.03); // smaller scale
	            scale.setToY(1.03);
	            scale.play();
	            button.setStyle(hoverStyle);
	        });
	
	        button.setOnMouseExited(e -> {
	            ScaleTransition scale = new ScaleTransition(Duration.millis(120), button);
	            scale.setToX(1.0);
	            scale.setToY(1.0);
	            scale.play();
	            button.setStyle(baseStyle);
	        });
	
	        return button;
    }
	
	public static void setCompactButtonEnabled(Button b){
        if (b == null) return;
        b.setDisable(false);
        Object bs = b.getProperties().get("baseStyle");
        if (bs instanceof String) b.setStyle((String)bs);
    }
	
	public static void setCompactButtonDisabled(Button b){
        if (b == null) return;
        b.setDisable(true);
        Object ds = b.getProperties().get("disabledStyle");
        if (ds instanceof String) b.setStyle((String)ds);
    }
}

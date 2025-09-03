package algobot;

import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

public class HomeManager {
	public void createHomePage() {
		common.homeContainer = new VBox(35);
        common.homeContainer.setAlignment(Pos.CENTER);
        common.homeContainer.setPadding(new Insets(50));
        
        // Enhanced dark gradient background matching project theme
        LinearGradient gradient = new LinearGradient(0, 0, 1, 1, true, null,
            new Stop(0, Color.web("#0a0e1a")),
            new Stop(0.25, Color.web("#0f1419")),
            new Stop(0.5, Color.web("#141e30")),
            new Stop(0.75, Color.web("#243b55")),
            new Stop(1, Color.web("#0f051d"))
        );
        
        common.homeContainer.setBackground(new Background(new BackgroundFill(gradient, null, null)));
        
        // Enhanced title section with neon styling
        Label titleLabel = createModernTitle();
        Label subtitleLabel = createModernSubtitle();
        
        // Stylish separator line
        Region separatorLine = new Region();
        separatorLine.setPrefSize(300, 3);
        separatorLine.setStyle(
            "-fx-background-color: linear-gradient(to right, " +
            "transparent, #00ffff, #4ecdc4, #00ffff, transparent);" +
            "-fx-background-radius: 2; -fx-effect: dropshadow(gaussian, #00ffff66, 8, 0, 0, 0);"
        );
        
        // Enhanced cards container
	    VBox cardsContainer = new VBox(22);
	    cardsContainer.setAlignment(Pos.CENTER);
	    cardsContainer.setPadding(new Insets(25, 0, 0, 0));
	        
	        // Create enhanced cards with neon theme
	    VBox sortingCard = createEnhancedCard("SORTING", "Visualize sorting algorithms", "#00ffff", "Explore interactive sorting visualizations");
	    VBox mergeSortCard = createEnhancedCard("DIVIDE & CONQUER", "Recursive tree visualization", "#00ffff", "Watch divide-and-conquer in action");
	    VBox graphCard = createEnhancedCard("GRAPHS", "Explore graph algorithms", "#00ffff", "Discover graph traversal and pathfinding");
	    VBox dataStructureCard = createEnhancedCard("STRUCTURES", "Learn data structures", "#00ffff", "Experience dynamic data structure operations");
	    VBox mazeCard = createEnhancedCard("MAZE SOLUTION", "Solve mazes with DSU", "#00ffff", "Explore maze solving with Disjoint Set Union");
	    VBox networkCard = createEnhancedCard("MAX FLOW / MIN CUT", "Edmonds–Karp network flow", "#00ffff", "Build a capacity network and compute max flow");
	
	        // Enhanced click handlers with hover effects
	    addCardInteractivity(sortingCard, () -> common.transitionToPage(common.sortingContainer));
	    addCardInteractivity(mergeSortCard, () -> common.transitionToPage(common.mergeSortContainer));
	    addCardInteractivity(graphCard, () -> common.transitionToPage(common.graphContainer));
	    addCardInteractivity(dataStructureCard, () -> common.transitionToPage(common.dataStructureContainer));
	    addCardInteractivity(mazeCard, () -> common.transitionToPage(common.mazeContainer));
	    addCardInteractivity(networkCard, () -> common.transitionToPage(common.networkContainer));
	
	    // Arrange as 2 rows x 3 columns
	    HBox row1 = new HBox(30);
	    row1.setAlignment(Pos.CENTER);
	    row1.getChildren().addAll(sortingCard, mergeSortCard, graphCard);
	    HBox row2 = new HBox(30);
	    row2.setAlignment(Pos.CENTER);
	    row2.getChildren().addAll(dataStructureCard, mazeCard, networkCard);
	    cardsContainer.getChildren().addAll(row1, row2);
        
	    common.homeContainer.getChildren().addAll(titleLabel, subtitleLabel, separatorLine, cardsContainer);
    }
	
	private Label createModernTitle() {
        Label titleLabel = new Label("ALGOBOT");
        titleLabel.setFont(Font.font("SF Pro Display", FontWeight.EXTRA_BOLD, 84));
        titleLabel.setTextFill(Color.web("#e0ffff")); // Consistent with theme
        titleLabel.setStyle("-fx-letter-spacing: 6px; -fx-cursor: hand;");
        
        // Subtle glow effect matching theme
        DropShadow neonGlow = new DropShadow();
        neonGlow.setColor(Color.web("#00ffff"));
        neonGlow.setOffsetX(0);
        neonGlow.setOffsetY(0);
        neonGlow.setRadius(15);
        neonGlow.setSpread(0.2);
        
        titleLabel.setEffect(neonGlow);
        
        // Hover animation
        titleLabel.setOnMouseEntered(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(200), titleLabel);
            scale.setToX(1.02);
            scale.setToY(1.02);
            scale.play();
            
            DropShadow hoverGlow = new DropShadow();
            hoverGlow.setColor(Color.web("#4ecdc4"));
            hoverGlow.setOffsetX(0);
            hoverGlow.setOffsetY(0);
            hoverGlow.setRadius(20);
            hoverGlow.setSpread(0.3);
            titleLabel.setEffect(hoverGlow);
        });
        
        titleLabel.setOnMouseExited(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(200), titleLabel);
            scale.setToX(1.0);
            scale.setToY(1.0);
            scale.play();
            titleLabel.setEffect(neonGlow);
        });
        
        // Click to show developer page
        titleLabel.setOnMouseClicked(e -> showDeveloperPage());
        
        return titleLabel;
    }
	
	private void showDeveloperPage() {
        if (common.developerContainer == null) {
            createDeveloperPage();
        }
        
        common.transitionToPage(common.developerContainer);
    }
    
    private void createDeveloperPage() {
        common.developerContainer = new VBox(40);
        common.developerContainer.setAlignment(Pos.CENTER);
        common.developerContainer.setPadding(new Insets(50));
        
        // Same background as other pages
        LinearGradient gradient = new LinearGradient(0, 0, 1, 1, true, null,
            new Stop(0, Color.web("#0a0e1a")),
            new Stop(0.25, Color.web("#0f1419")),
            new Stop(0.5, Color.web("#141e30")),
            new Stop(0.75, Color.web("#243b55")),
            new Stop(1, Color.web("#0f051d"))
        );
        
        common.developerContainer.setBackground(new Background(new BackgroundFill(gradient, null, null)));
        
        // Back button
        HBox titleBox = ButtonManager.createTitleWithBackButton("DEVELOPER INFO");
        
        if (!titleBox.getChildren().isEmpty() && titleBox.getChildren().get(0) instanceof Button backBtn) {
            backBtn.setOnAction(e -> common.transitionToPage(common.homeContainer));
        }
        
        // Developer info
        Label devTitle = new Label("DEVELOPED BY");
        devTitle.setFont(Font.font("SF Pro Display", FontWeight.BOLD, 32));
        devTitle.setTextFill(Color.web("#00ffff"));
        devTitle.setStyle("-fx-letter-spacing: 2px;");
        
        // Developer names with consistent styling
        VBox developersBox = new VBox(15);
        developersBox.setAlignment(Pos.CENTER);
        
        Label dev1 = new Label("G.M. NOOR-UL ISLAM LABIB");
        dev1.setFont(Font.font("SF Pro Display", FontWeight.BOLD, 20));
        dev1.setTextFill(Color.web("#e0ffff"));
        dev1.setStyle("-fx-letter-spacing: 1px;");
        
        Label dev2 = new Label("JEHAD ALAM");
        dev2.setFont(Font.font("SF Pro Display", FontWeight.BOLD, 20));
        dev2.setTextFill(Color.web("#e0ffff"));
        dev2.setStyle("-fx-letter-spacing: 1px;");
        
        Label dev3 = new Label("SHEIKH FARHAN ADIB AUVRO");
        dev3.setFont(Font.font("SF Pro Display", FontWeight.BOLD, 20));
        dev3.setTextFill(Color.web("#e0ffff"));
        dev3.setStyle("-fx-letter-spacing: 1px;");
        
        developersBox.getChildren().addAll(dev1, dev2, dev3);
        
        Label description = new Label("Algorithm Visualization Tool\nBuilt with JavaFX");
        description.setFont(Font.font("SF Pro Text", FontWeight.NORMAL, 16));
        description.setTextFill(Color.web("#b0c4de"));
        description.setTextAlignment(TextAlignment.CENTER);
        description.setStyle("-fx-line-spacing: 5px;");
        
        // Tech stack
        Label techLabel = new Label("TECHNOLOGIES USED");
        techLabel.setFont(Font.font("SF Pro Display", FontWeight.MEDIUM, 18));
        techLabel.setTextFill(Color.web("#4ecdc4"));
        techLabel.setStyle("-fx-letter-spacing: 1px;");
        
        Label techStack = new Label("Java • JavaFX • Maven • CSS");
        techStack.setFont(Font.font("SF Pro Text", FontWeight.NORMAL, 14));
        techStack.setTextFill(Color.web("#8ab4ff"));
        techStack.setStyle("-fx-letter-spacing: 0.5px;");
        
        // Add glow effects
        DropShadow titleGlow = new DropShadow();
        titleGlow.setColor(Color.web("#00ffff"));
        titleGlow.setRadius(10);
        titleGlow.setSpread(0.2);
        devTitle.setEffect(titleGlow);
        
        // Apply glow to all developer names
        DropShadow nameGlow = new DropShadow();
        nameGlow.setColor(Color.web("#4ecdc4"));
        nameGlow.setRadius(8);
        nameGlow.setSpread(0.15);
        dev1.setEffect(nameGlow);
        dev2.setEffect(nameGlow);
        dev3.setEffect(nameGlow);
        
        common.developerContainer.getChildren().addAll(titleBox, devTitle, developersBox, description, techLabel, techStack);
    }
    
    private Label createModernSubtitle() {
        Label subtitleLabel = new Label("INTERACTIVE ALGORITHM VISUALIZATION");
        subtitleLabel.setFont(Font.font("JetBrains Mono", FontWeight.MEDIUM, 16));
        subtitleLabel.setTextFill(Color.web("#b0c4de"));
        subtitleLabel.setStyle("-fx-letter-spacing: 2px; -fx-opacity: 0.9;");
        
        // Subtle glow
        DropShadow subtitleGlow = new DropShadow();
        subtitleGlow.setColor(Color.web("#4ecdc4"));
        subtitleGlow.setOffsetX(0);
        subtitleGlow.setOffsetY(0);
        subtitleGlow.setRadius(8);
        subtitleGlow.setSpread(0.2);
        subtitleLabel.setEffect(subtitleGlow);
        
        return subtitleLabel;
    }
    
    private VBox createEnhancedCard(String title, String description, String accentColor, String features) {
        VBox card = new VBox(18);
        card.setAlignment(Pos.CENTER);
        card.setPrefSize(280, 220);
        card.setPadding(new Insets(25));
        card.setStyle(
            "-fx-background-color: linear-gradient(to bottom, " +
            "rgba(20, 25, 35, 0.95), rgba(15, 20, 30, 0.95));" +
            "-fx-background-radius: 18;" +
            "-fx-border-color: linear-gradient(to right, " + accentColor + "77, transparent, " + accentColor + "77);" +
            "-fx-border-width: 2;" +
            "-fx-border-radius: 18;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 15, 0, 0, 8);" +
            "-fx-cursor: hand;"
        );
        
        // Title with aesthetic font
	    Label titleLabel = new Label(title);
	    titleLabel.setFont(Font.font("SF Pro Display", FontWeight.BOLD, 24));
	    titleLabel.setTextFill(Color.web(accentColor));
	    titleLabel.setStyle("-fx-letter-spacing: 2px;");
	    // Ensure multi-line titles (with \n) render centered nicely
	    titleLabel.setWrapText(true);
	    titleLabel.setTextAlignment(TextAlignment.CENTER);
	    titleLabel.setAlignment(Pos.CENTER);
        
        // Description with refined typography
        Label descLabel = new Label(description);
        descLabel.setFont(Font.font("SF Pro Text", FontWeight.MEDIUM, 15));
        descLabel.setTextFill(Color.web("#e0e0e0"));
        descLabel.setWrapText(true);
        descLabel.setTextAlignment(TextAlignment.CENTER);
        descLabel.setStyle("-fx-opacity: 0.9; -fx-line-spacing: 2px;");
        
        // Explore text with elegant styling
        Label featuresLabel = new Label(features);
        featuresLabel.setFont(Font.font("SF Pro Text", FontWeight.LIGHT, 13));
        featuresLabel.setTextFill(Color.web("#b0c4de"));
        featuresLabel.setWrapText(true);
        featuresLabel.setTextAlignment(TextAlignment.CENTER);
        featuresLabel.setStyle("-fx-letter-spacing: 0.8px; -fx-opacity: 0.85; -fx-font-style: italic;");
        
        // Elegant accent line
        Region accentLine = new Region();
        accentLine.setPrefSize(80, 2);
        accentLine.setStyle(
            "-fx-background-color: linear-gradient(to right, transparent, " + accentColor + ", transparent);" +
            "-fx-background-radius: 1;" +
            "-fx-effect: dropshadow(gaussian, " + accentColor + "66, 4, 0, 0, 0);"
        );
        
        card.getChildren().addAll(titleLabel, descLabel, accentLine, featuresLabel);
        return card;
    }
    
    private void addCardInteractivity(VBox card, Runnable onClickAction) {
        // Base styling
        String baseStyle = card.getStyle();
        
        // Enhanced hover effect
        card.setOnMouseEntered(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(150), card);
            scale.setToX(1.05);
            scale.setToY(1.05);
            scale.play();
            
            card.setStyle(baseStyle.replace("rgba(20, 25, 35, 0.95)", "rgba(25, 35, 45, 0.98)")
                                  .replace("dropshadow(gaussian, rgba(0,0,0,0.4), 15, 0, 0, 8)",
                                          "dropshadow(gaussian, rgba(0,255,255,0.3), 20, 0, 0, 10)"));
        });
        
        card.setOnMouseExited(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(150), card);
            scale.setToX(1.0);
            scale.setToY(1.0);
            scale.play();
            
            card.setStyle(baseStyle);
        });
        
        card.setOnMouseClicked(e -> onClickAction.run());
    }
}

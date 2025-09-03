package algobot;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.*;
import javafx.util.Duration;
import java.util.*;
import java.util.function.BiConsumer;

public class GraphManager {
	// Store graph algorithm checkboxes
    private Map<String, CheckBox> graphAlgoChecks = new HashMap<>();
    private boolean graphAlgoSelectionLocked = false; // once any algorithm chosen, prevent further changes until reset
    
    // Workflow buttons for graph algorithms
    private Button selectSourceBtnRef;
    private Button runGraphAlgosBtnRef;
    private Button resetVisualizationBtnRef;
    private Button pauseBtnRef;
    
    private Map<String, GraphNode> graphNodes = new HashMap<>();
    private List<String> nodeOrder = new ArrayList<>();
    private int nextNodeId = 0;
    private static final double NODE_RADIUS = 22;
    private static final double BOUNDARY_MARGIN = NODE_RADIUS + 15; // Safe boundary for node placement
    private Pane graphCanvas;
    private boolean addNodeArmed = false; // true only after clicking ADD NODE before placing
    private Button addNodeButtonRef; // reference to update button text/style when armed/disarmed
    private boolean addEdgeArmed = false; // edge creation mode toggle
    private Button addEdgeButtonRef;
    
    // Graph configuration
    private boolean isDirected = false;
    private boolean isWeighted = false;
    private boolean isConfigurationComplete = false;
    private RadioButton directedRadio, undirectedRadio, weightedRadio, unweightedRadio;
    private double animationSpeed = 1.0; // Speed multiplier for algorithm animations
    private Timeline currentVisualizationTimeline; // Track current running animation for stopping
    private boolean isPaused = false; // Track pause state
    // Edge data
    private Map<String, Circle> nodeCircles = new HashMap<>();
    private Set<String> edgeKeys = new HashSet<>(); // undirected key: minId|maxId
    
    private String selectedNodeId = null;
    private List<EdgeRecord> edges = new ArrayList<>();
    
    // Drag-to-create edge variables
    private boolean isDraggingEdge = false;
    private String dragSourceNodeId = null;
    private Line dragLine = null;
    private GraphNode bfsSource;
    
    // Graph explanation area (separate from sorting explanation)
    private TextArea graphExplanationArea;
    private String lastGraphExplanation = "";
    private Slider graphSpeedSlider;
    
    private Map<Circle, GraphNode> circleToNode = new HashMap<>();
    // Distance labels for Dijkstra visualization (styled like edge weight boxes)
    private Map<String, Label> dijkstraDistLabels = new HashMap<>();
    private Map<String, Label> bellmanFordDistLabels = new HashMap<>();
    
    // Inline weight overlay state
    private Node currentWeightOverlay = null;
    private EventHandler<MouseEvent> outsideWeightClickHandler = null;
    
    private static class EdgeRecord { 
        // Core edge data
        Line line;                 // main visible line (graph) or inner pipe (network)
        String a; 
        String b; 
        double weight;
        Label weightLabel;         // for capacity/weight label
        Polygon arrowHead;         // arrow head for directed visuals
        // Network-specific layered pipe rendering (optional; null for plain graph edges)
        Line pipeBaseLine;         // dark outer pipe
        Line pipeInnerLine;        // inner pipe body (also referenced by 'line')
        Line waterLine;            // animated water overlay (dash offset)
        // Flow value (used by Network page; harmless for Graph page)
        double flow = 0.0;
        // Network residual back-edge overlay (reverse direction visual)
        Line residualBackLine;     // dashed magenta line from b -> a
        Label residualBackLabel;   // small label showing residual (flow) on back edge
        // Water flow animation state
        Timeline waterDashAnim;
        EdgeRecord(Line l, String a, String b, double w, Label wl, Polygon ah) {
            this.line = l; 
            this.a = a; 
            this.b = b; 
            this.weight = w;
            this.weightLabel = wl;
            this.arrowHead = ah;
            this.pipeBaseLine = null;
            this.pipeInnerLine = null;
            this.waterLine = null;
            this.waterDashAnim = null;
            this.flow = 0.0;
            this.residualBackLine = null;
            this.residualBackLabel = null;
        } 
    }
    
    private static class DFSStep {
        enum Type { VISIT, EXPLORE_EDGE, BACKTRACK, EDGE_RESET, FINISH }
        final Type type;
        final String nodeId;
        final String targetNodeId; // for EXPLORE_EDGE/BACKTRACK/EDGE_RESET
        final int depth;
        DFSStep(Type type, String nodeId, String targetNodeId, int depth) {
            this.type = type;
            this.nodeId = nodeId;
            this.targetNodeId = targetNodeId;
            this.depth = depth;
        }
    }
    
	public void createGraphPage() {
    	common.graphContainer = new VBox(10);
    	common.graphContainer.setAlignment(Pos.TOP_CENTER);
    	common.graphContainer.setPadding(new Insets(60, 20, 20, 20));
        
        // Dramatic dark gradient for graph algorithms with neon accents
        LinearGradient gradient = new LinearGradient(0, 0, 1, 1, true, null,
            new Stop(0, Color.web("#0c0c0c")),
            new Stop(0.3, Color.web("#141e30")),
            new Stop(0.6, Color.web("#243b55")),
            new Stop(1, Color.web("#0f051d"))
        );
        common.graphContainer.setBackground(new Background(new BackgroundFill(gradient, null, null)));
        
        // Title with back button + control buttons organized in rows for better visibility
        HBox titleContainer = ButtonManager.createTitleWithBackButton("GRAPH VISUALIZATION");
        
        // Create button control panel with better organization (compact layout)
        VBox buttonPanel = new VBox(6); // reduced spacing
        buttonPanel.setAlignment(Pos.CENTER_RIGHT);
        graphAlgoSelectionLocked = false;
        buttonPanel.setPadding(new Insets(25, 0, 0, 15)); // increased top padding for perfect positioning
        
        // First row: Primary action buttons (compact)
        HBox firstRowButtons = new HBox(8); // reduced spacing
        firstRowButtons.setAlignment(Pos.CENTER_RIGHT);
        Button addBtn = ButtonManager.createCompactButton("ADD NODE", "#00ffff");
        addNodeButtonRef = addBtn;
        addBtn.setOnAction(e -> { toggleAddNodeMode(); if(addEdgeArmed) toggleAddEdgeMode(); });
	    Button addEdgeBtn = ButtonManager.createCompactButton("ADD EDGE", "#00ffff");
	    // Disabled until configuration complete; apply consistent disabled styling
	    ButtonManager.setCompactButtonDisabled(addEdgeBtn);
        addEdgeButtonRef = addEdgeBtn;
        addEdgeBtn.setOnAction(e -> { toggleAddEdgeMode(); if(addNodeArmed) toggleAddNodeMode(); });
        firstRowButtons.getChildren().addAll(addBtn, addEdgeBtn);
        
        // Second row: Undo and Clear buttons (compact)
        HBox secondRowButtons = new HBox(8); // reduced spacing
        secondRowButtons.setAlignment(Pos.CENTER_RIGHT);
        Button undoEdgeBtn = ButtonManager.createCompactButton("UNDO EDGE", "#00ffff");
        undoEdgeBtn.setOnAction(e -> undoLastEdge());
        Button undoBtn = ButtonManager.createCompactButton("UNDO NODE", "#00ffff");
        undoBtn.setOnAction(e -> removePreviousNode());
        Button clearBtn = ButtonManager.createCompactButton("CLEAR", "#00ffff"); // shorter text
        clearBtn.setOnAction(e -> clearGraph());
        secondRowButtons.getChildren().addAll(undoEdgeBtn, undoBtn, clearBtn);
        
        buttonPanel.getChildren().addAll(firstRowButtons, secondRowButtons);

	    // Third row: Controlled workflow (Select Source -> Run) (compact)
	    HBox thirdRow = new HBox(8); // reduced spacing
	    thirdRow.setAlignment(Pos.CENTER_RIGHT);
	    selectSourceBtnRef = ButtonManager.createCompactButton("SET SOURCE", "#00ffff");
	    runGraphAlgosBtnRef = ButtonManager.createCompactButton("RUN", "#00ffff");
	    // disabled until at least one algorithm selected
	    ButtonManager.setCompactButtonDisabled(selectSourceBtnRef);
	    runGraphAlgosBtnRef.setDisable(true); // disabled until source chosen
	    selectSourceBtnRef.setOnAction(e -> enableSourceSelection());
	    runGraphAlgosBtnRef.setOnAction(e -> runChosenGraphAlgorithms());
	    thirdRow.getChildren().addAll(selectSourceBtnRef, runGraphAlgosBtnRef);
	    buttonPanel.getChildren().add(thirdRow);
	    
	    // Fourth row: Reset Visualization and Pause buttons (compact)
	    HBox fourthRow = new HBox(8); // reduced spacing
	    fourthRow.setAlignment(Pos.CENTER_RIGHT);
	    pauseBtnRef = ButtonManager.createCompactButton("PAUSE", "#ffaa00");
	    pauseBtnRef.setDisable(true); // disabled until animation is running
	    pauseBtnRef.setOnAction(e -> togglePauseVisualization());
	    resetVisualizationBtnRef = ButtonManager.createCompactButton("RESET VISUALIZATION", "#ff6b35");
	    resetVisualizationBtnRef.setDisable(true); // disabled until algorithms have been run
	    resetVisualizationBtnRef.setOnAction(e -> resetVisualizationState());
	    fourthRow.getChildren().addAll(pauseBtnRef, resetVisualizationBtnRef);
	    buttonPanel.getChildren().add(fourthRow);
        
        // Add spacer and button panel to title container
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleContainer.getChildren().addAll(spacer, buttonPanel);

        // Graph configuration panel
        VBox configPanel = new VBox(8);
        configPanel.setAlignment(Pos.CENTER);
        configPanel.setStyle("-fx-background-color: rgba(0,0,0,0.3); -fx-background-radius: 12; -fx-padding: 12;");
        
        Label configLabel = new Label("GRAPH CONFIGURATION");
        configLabel.setStyle("-fx-text-fill: #00ffff; -fx-font-weight: bold; -fx-font-size: 14px;");
        
        // Direction toggle
        HBox directionBox = new HBox(15);
        directionBox.setAlignment(Pos.CENTER);
        Label dirLabel = new Label("Type:");
        dirLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");
        
        ToggleGroup directionGroup = new ToggleGroup();
        undirectedRadio = new RadioButton("Undirected");
        undirectedRadio.setToggleGroup(directionGroup);
        undirectedRadio.setStyle("-fx-text-fill: white; -fx-font-size: 11px;");
        undirectedRadio.setOnAction(e -> {
            isDirected = false;
	        checkConfigurationComplete();
	        // Disable both radio buttons after selection
	        undirectedRadio.setDisable(true);
	        directedRadio.setDisable(true);
	    });
    
	    directedRadio = new RadioButton("Directed");
	    directedRadio.setToggleGroup(directionGroup);
	    directedRadio.setStyle("-fx-text-fill: white; -fx-font-size: 11px;");
	    directedRadio.setOnAction(e -> {
	        isDirected = true;
	        checkConfigurationComplete();
	        // Disable both radio buttons after selection
	        undirectedRadio.setDisable(true);
	        directedRadio.setDisable(true);
	    });
    
	    directionBox.getChildren().addAll(dirLabel, undirectedRadio, directedRadio);
	    
	    // Weight toggle
	    HBox weightBox = new HBox(15);
	    weightBox.setAlignment(Pos.CENTER);
	    Label weightLabel = new Label("Weight:");
	    weightLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");
	    
	    ToggleGroup weightGroup = new ToggleGroup();
	    unweightedRadio = new RadioButton("Unweighted");
	    unweightedRadio.setToggleGroup(weightGroup);
	    unweightedRadio.setStyle("-fx-text-fill: white; -fx-font-size: 11px;");
	    unweightedRadio.setOnAction(e -> {
	        isWeighted = false;
	        checkConfigurationComplete();
	        // Disable both radio buttons after selection
	        unweightedRadio.setDisable(true);
	        weightedRadio.setDisable(true);
	    });
	    
	    weightedRadio = new RadioButton("Weighted");
	    weightedRadio.setToggleGroup(weightGroup);
	    weightedRadio.setStyle("-fx-text-fill: white; -fx-font-size: 11px;");
	    weightedRadio.setOnAction(e -> {
	        isWeighted = true;
	        checkConfigurationComplete();
	        // Disable both radio buttons after selection
	        unweightedRadio.setDisable(true);
	        weightedRadio.setDisable(true);
	    });
    
	    weightBox.getChildren().addAll(weightLabel, unweightedRadio, weightedRadio);
        configPanel.getChildren().addAll(configLabel, directionBox, weightBox);

	    // Graph algorithm selection (horizontal compact bar)
	    Label algoLabel = new Label("ALGORITHMS:");
	    algoLabel.setStyle("-fx-text-fill:#00ffff;-fx-font-size:12px;-fx-font-weight:bold;");
	    HBox algoFlow = new HBox(10);
	    algoFlow.setAlignment(Pos.CENTER_LEFT);
	    algoFlow.setPadding(new Insets(4,8,4,8));
	    algoFlow.setStyle("-fx-background-color: rgba(10,15,22,0.65); -fx-background-radius:10; -fx-border-radius:10; -fx-border-color:#00ffff44; -fx-border-width:1;");
	    String[] algoNames = {"BFS","DFS","Dijkstra","Bellman-Ford","Floyd-Warshall","Prim","Kruskal"};
	    graphAlgoChecks.clear();
	        
	    for (String name : algoNames) {
	            CheckBox cb = new CheckBox(name);
	            cb.setStyle("-fx-text-fill:#e0e0e0;-fx-font-size:11px;");
	            cb.setDisable(true); // Initially disabled until graph is created
	            cb.setOnAction(e-> {
	                if (graphAlgoSelectionLocked) { cb.setSelected(!cb.isSelected()); return; }
	                boolean anySelected = graphAlgoChecks.values().stream().anyMatch(CheckBox::isSelected);
	                // Disable structural edits while algorithms chosen
	                if (addNodeButtonRef != null) {
	                    if (anySelected) ButtonManager.setCompactButtonDisabled(addNodeButtonRef); 
	                    else ButtonManager.setCompactButtonEnabled(addNodeButtonRef);
	                }
	                if (addEdgeButtonRef != null) {
	                    boolean disableEdge = anySelected || !isConfigurationComplete;
	                    if (disableEdge) ButtonManager.setCompactButtonDisabled(addEdgeButtonRef); 
	                    else ButtonManager.setCompactButtonEnabled(addEdgeButtonRef);
	                }
	                // Set Source enabled only when at least one algorithm picked and no source yet
	                if (selectSourceBtnRef != null) {
	                    if (anySelected && bfsSource == null) ButtonManager.setCompactButtonEnabled(selectSourceBtnRef); 
	                    else ButtonManager.setCompactButtonDisabled(selectSourceBtnRef);
	                }
	                
	                validateGraphForAlgorithms();
	                updateGraphAlgoButtons();
	            });
	            graphAlgoChecks.put(name, cb);
	            algoFlow.getChildren().add(cb);
        }
	    
	    Label algoHint = new Label("(Add nodes + edges -> pick algos -> set source)");
	    algoHint.setStyle("-fx-text-fill:#8ab4ff;-fx-font-size:10px; -fx-font-style:italic;");
        
        // Algorithm speed control
        Label speedLabel = new Label("ANIMATION SPEED");
        speedLabel.setStyle("-fx-text-fill:#00ffff;-fx-font-size:12px;-fx-font-weight:bold;");
        
        HBox speedBox = new HBox(15);
        speedBox.setAlignment(Pos.CENTER);
        Label speedLabelLeft = new Label("Speed:");
        speedLabelLeft.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");
        
	    graphSpeedSlider = new javafx.scene.control.Slider(0.5, 3.0, 1.0);
	    graphSpeedSlider.setShowTickLabels(true);
	    graphSpeedSlider.setShowTickMarks(true);
	    graphSpeedSlider.setMajorTickUnit(0.5);
	    graphSpeedSlider.setMinorTickCount(1);
	    graphSpeedSlider.setPrefWidth(200);
	    graphSpeedSlider.setStyle("-fx-control-inner-background: rgba(255,255,255,0.1);");
        
        Label speedValueLabel = new Label("1.0x");
        speedValueLabel.setStyle("-fx-text-fill: #00ffff; -fx-font-weight: bold; -fx-font-size: 12px;");
        speedValueLabel.setPrefWidth(50);
        
        // Update speed value and store in instance variable
        graphSpeedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double speed = newVal.doubleValue();
            speedValueLabel.setText(String.format("%.1fx", speed));
            animationSpeed = speed;
        });
        
        speedBox.getChildren().addAll(speedLabelLeft, graphSpeedSlider, speedValueLabel);
        
	    // Re-layout config: direction + weight + algos + speed all horizontal blocks
	    VBox speedBoxWrapper = new VBox(4, speedLabel, speedBox);
	    speedBoxWrapper.setAlignment(Pos.CENTER_LEFT);
	    HBox horizontalConfig = new HBox(22, directionBox, weightBox, new VBox(2, algoLabel, algoFlow, algoHint), speedBoxWrapper);
	    horizontalConfig.setAlignment(Pos.CENTER_LEFT);
	    horizontalConfig.setPadding(new Insets(6,0,6,0));
	    // Replace existing vertical configPanel contents with horizontal layout
	    configPanel.getChildren().clear();
	    configPanel.getChildren().add(horizontalConfig);
	        
	    // Main graph canvas and explanation side-by-side
	    Pane graphCanvas = createGraphCanvas();
	    // Increased base preferred size for fullscreen usage
	    graphCanvas.setPrefSize(1500, 900);
	    graphCanvas.setMinSize(1000, 620);
	    VBox.setVgrow(graphCanvas, Priority.ALWAYS);
	
	    VBox graphExplainBox = new VBox(8);
	    graphExplainBox.setPrefWidth(320);
	    graphExplainBox.setMinWidth(300);
	    graphExplainBox.setAlignment(Pos.TOP_CENTER);
	    graphExplainBox.setStyle("-fx-background-color: rgba(20,25,35,0.92);"
	        +"-fx-border-color: linear-gradient(to bottom, #00ffff55,#0066ff55);"
	        +"-fx-border-radius:14; -fx-background-radius:14; -fx-padding:12;" );
	
	    Label gExplLabel = new Label("STEP EXPLANATION");
	    gExplLabel.setStyle("-fx-text-fill:#ffffff;-fx-font-size:16px;-fx-font-weight:800;" +
	        "-fx-effect:dropshadow(gaussian, rgba(255,255,255,0.4),10,0.7,0,0);");
	
	    graphExplanationArea = new TextArea();
	    graphExplanationArea.setEditable(false);
	    graphExplanationArea.setWrapText(true);
	    graphExplanationArea.setPrefWidth(420);
	    graphExplanationArea.setPrefHeight(520);
	    // Now that graphExplainBox exists, make canvas responsive
	    Platform.runLater(() -> {
	        try {
	            graphCanvas.prefWidthProperty().bind(common.graphContainer.widthProperty().subtract(graphExplainBox.widthProperty()).subtract(60));
	            graphCanvas.prefHeightProperty().bind(common.graphContainer.heightProperty().subtract(140));
	        } catch (Exception ignored) {}
	    });
	    graphExplanationArea.setStyle(
	        "-fx-control-inner-background: rgba(8,10,14,0.95);" +
	        "-fx-background-color: rgba(8,10,14,0.95);" +
	        "-fx-text-fill: #ffffff;" +
	        "-fx-highlight-fill: #ffffff33;" +
	        "-fx-highlight-text-fill: #ffffff;" +
	        "-fx-border-color: linear-gradient(to right,#888888,#bbbbbb);" +
	        "-fx-border-radius:14; -fx-background-radius:14;" +
	        "-fx-font-family: 'Consolas','JetBrains Mono','Menlo','SF Pro Text';" +
	        "-fx-font-size: 14px;" +
	        "-fx-prompt-text-fill: rgba(255,255,255,0.55);" +
	        "-fx-padding:10;"
	    );
	    graphExplanationArea.setPromptText("Graph algorithm steps will appear here...");
	    VBox.setVgrow(graphExplanationArea, Priority.ALWAYS);
	
	    HBox graphMainSplit = new HBox(16);
	    graphMainSplit.setAlignment(Pos.TOP_LEFT);
	    HBox.setHgrow(graphCanvas, Priority.ALWAYS);
	    graphCanvas.setMinWidth(650);
	
	    graphExplainBox.getChildren().addAll(gExplLabel, graphExplanationArea);
	    graphMainSplit.getChildren().addAll(graphCanvas, graphExplainBox);

        Label hint = new Label("1. Select graph type and weight type above  2. Click 'ADD NODE' then click on canvas  3. Click 'ADD EDGE' then click two nodes  4. Algorithms will be enabled after graph creation");
        hint.setStyle("-fx-text-fill: #dddddd; -fx-font-size: 13px; -fx-padding: 6 0 0 0; -fx-font-weight: 500;");

        common.graphContainer.getChildren().addAll(titleContainer, configPanel, graphMainSplit, hint);
	    // Override back button action to also reset graph speed & explanation (ensure only one instance)
	    if (!titleContainer.getChildren().isEmpty() && titleContainer.getChildren().get(0) instanceof Button backBtn) {
	        backBtn.setOnAction(e -> {
	            resetGraphUIState();
	            common.transitionToPage(common.homeContainer);
	        });
	    }
    }
	
	private void toggleAddNodeMode() {
        if (addNodeArmed) {
            disarmAddNodeMode();
        } 
        else {
            armAddNodeMode();
        }
    }

    private void toggleAddEdgeMode() {
        addEdgeArmed = !addEdgeArmed;
        if (addEdgeButtonRef != null) {
            if (addEdgeArmed) {
                addEdgeButtonRef.setText("EDGE MODE ON");
                addEdgeButtonRef.setStyle(addEdgeButtonRef.getStyle() + ";-fx-effect: dropshadow(gaussian,#1e90ff,25,0.7,0,0);-fx-opacity:0.95;");
            } else {
                addEdgeButtonRef.setText("ADD EDGE");
                addEdgeButtonRef.setStyle(
                    "-fx-background-color: linear-gradient(45deg, #1e90ff, #0b5fa8); -fx-text-fill: white; -fx-font-weight: bold; " +
                    "-fx-background-radius:8; -fx-border-radius:8; -fx-padding:8 16; -fx-font-size:12px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3),5,0,0,2);"
                );
            }
        }
        // Clear partial selection when turning off
        if (!addEdgeArmed && selectedNodeId != null) {
            highlightNode(selectedNodeId, false);
            selectedNodeId = null;
        }
    }
    
    private void togglePauseVisualization() {
        if (currentVisualizationTimeline == null) {
            return; // No animation running
        }
        
        if (isPaused) {
            // Resume animation
            currentVisualizationTimeline.play();
            isPaused = false;
            if (pauseBtnRef != null) {
                pauseBtnRef.setText("PAUSE");
                pauseBtnRef.setStyle("-fx-background-color: linear-gradient(45deg, #ffaa00, #ff8800); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 8 16; -fx-font-size: 12px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3),5,0,0,2);");
            }
        } else {
            // Pause animation
            currentVisualizationTimeline.pause();
            isPaused = true;
            if (pauseBtnRef != null) {
                pauseBtnRef.setText("RESUME");
                pauseBtnRef.setStyle("-fx-background-color: linear-gradient(45deg, #00ff88, #00cc66); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 8 16; -fx-font-size: 12px; -fx-effect: dropshadow(gaussian, rgba(0,255,136,0.5),10,0.7,0,0);");
            }
        }
    }
    
    private void disarmAddNodeMode() {
        // Only return if the mode is already disarmed and the button is in the default state
        if (!addNodeArmed && addNodeButtonRef != null && "ADD NODE".equals(addNodeButtonRef.getText())) {
            return;
        }

        addNodeArmed = false;
        if (addNodeButtonRef != null) {
            // Always reset the text and style to ensure the button is in the correct state
            addNodeButtonRef.setText("ADD NODE");
            addNodeButtonRef.setStyle(
                "-fx-background-color: linear-gradient(45deg, #00b894, #009b7f); " +
                "-fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-border-radius: 8; " +
                "-fx-padding: 8 16; -fx-font-size: 12px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3),5,0,0,2);"
            );
        }
    }
    
    private void armAddNodeMode() {
        addNodeArmed = true;
        if (addNodeButtonRef != null) {
            addNodeButtonRef.setText("CLICK CANVAS");
            // Append highlight effect while preserving base look
            addNodeButtonRef.setStyle(addNodeButtonRef.getStyle() + ";-fx-opacity:0.9; -fx-effect: dropshadow(gaussian, #00ffff, 25,0.75,0,0);");
        }
    }
    
    private void highlightNode(String nodeId, boolean on) {
        Circle c = nodeCircles.get(nodeId);
        if (c == null) return;
        if (on) {
            c.setStroke(Color.web("#ffe36e"));
            c.setStrokeWidth(3.0);
        } else {
            c.setStroke(Color.web("#00ffff"));
            c.setStrokeWidth(2.0);
        }
    }
    
    private void undoLastEdge() {
        if (edges.isEmpty()) return;
        EdgeRecord last = edges.remove(edges.size() - 1);
        graphCanvas.getChildren().remove(last.line);
        if (last.weightLabel != null) {
            graphCanvas.getChildren().remove(last.weightLabel);
        }
        if (last.arrowHead != null) {
            graphCanvas.getChildren().remove(last.arrowHead);
        }
        // Remove key respecting graph type
        if (isDirected) {
            edgeKeys.remove(last.a + "->" + last.b);
        } else {
            String na = last.a.compareTo(last.b) < 0 ? last.a : last.b;
            String nb = last.a.compareTo(last.b) < 0 ? last.b : last.a;
            edgeKeys.remove(na + "|" + nb);
        }
        // Clear selection highlight if it referenced a removed node pairing
        if (selectedNodeId != null) {
            highlightNode(selectedNodeId, false);
            selectedNodeId = null;
        }
    }
    
    private void removePreviousNode() {
        if (nodeOrder.isEmpty()) return;
        String lastId = nodeOrder.remove(nodeOrder.size() - 1);
        GraphNode node = graphNodes.remove(lastId);
        if (node != null) {
            graphCanvas.getChildren().removeIf(n ->
                (n instanceof Circle c && Math.abs(c.getCenterX() - node.getX()) < 0.1 && Math.abs(c.getCenterY() - node.getY()) < 0.1)
                || (n instanceof Label lbl && lbl.getText().equals(lastId)) // legacy labels (before switch to Text)
                || (n instanceof javafx.scene.text.Text txt && txt.getText().equals(lastId))
            );
            // Remove only edges connected to this node
            Iterator<EdgeRecord> eit = edges.iterator();
            while (eit.hasNext()) {
                EdgeRecord er = eit.next();
                if (er.a.equals(lastId) || er.b.equals(lastId)) {
                    graphCanvas.getChildren().remove(er.line);
                    if (er.weightLabel != null) {
                        graphCanvas.getChildren().remove(er.weightLabel);
                    }
                    if (er.arrowHead != null) {
                        graphCanvas.getChildren().remove(er.arrowHead);
                    }
                    edgeKeys.remove(er.a + "|" + er.b);
                    eit.remove();
                }
            }
            nodeCircles.remove(lastId);
            selectedNodeId = null;
        }
        try {
            int num = Integer.parseInt(lastId);
            if (num + 1 == nextNodeId) nextNodeId = num; // allow reuse
        } catch (NumberFormatException ignored) {}
        
        // If node "0" was removed, reset graph configuration to allow new type selection
        if ("0".equals(lastId)) {
            resetGraphConfiguration();
        }
    }
    
    private void resetGraphConfiguration() {
        // Re-enable all radio buttons
        if (directedRadio != null) directedRadio.setDisable(false);
        if (undirectedRadio != null) undirectedRadio.setDisable(false);
        if (weightedRadio != null) weightedRadio.setDisable(false);
        if (unweightedRadio != null) unweightedRadio.setDisable(false);
        
        // Clear all selections (no default mode)
        if (directedRadio != null) directedRadio.setSelected(false);
        if (undirectedRadio != null) undirectedRadio.setSelected(false);
        if (weightedRadio != null) weightedRadio.setSelected(false);
        if (unweightedRadio != null) unweightedRadio.setSelected(false);
        
        // Re-enable algorithm checkboxes for fresh start
        if (graphAlgoChecks != null) {
            graphAlgoChecks.values().forEach(cb -> {
                cb.setSelected(false);
                cb.setDisable(false); // re-enable for new session
            });
            graphAlgoSelectionLocked = false;
        }
        
        // Reset configuration flags
        isDirected = false;
        isWeighted = false;
        isConfigurationComplete = false;
        
        // Disable ADD EDGE button until configuration is complete
        if (addEdgeButtonRef != null) {
            addEdgeButtonRef.setDisable(true);
        }
    }
    
    public void clearGraph() {
        // Clear data structures
        graphNodes.clear();
        nodeOrder.clear();
        nextNodeId = 0;
        nodeCircles.clear();
        edgeKeys.clear();
        edges.clear();
        selectedNodeId = null;
        bfsSource = null; // reset chosen source

        // Clear UI nodes
        if (graphCanvas != null) {
            graphCanvas.getChildren().clear();
        }
        disarmAddNodeMode();

        // Unselect and disable algorithm checkboxes completely
        if (graphAlgoChecks != null) {
            graphAlgoChecks.values().forEach(cb -> { 
                cb.setSelected(false); 
                cb.setDisable(true); // disable checkboxes completely
            });
            graphAlgoSelectionLocked = false;
        }

        // Re-enable ADD NODE and ADD EDGE buttons (graph creation)
        if (addNodeButtonRef != null) addNodeButtonRef.setDisable(false);
        // ADD EDGE will be re-enabled by resetGraphConfiguration based on config completion

        // Disable gating buttons until a new algorithm selection is made & source re-set
        if (selectSourceBtnRef != null) selectSourceBtnRef.setDisable(true);
        if (runGraphAlgosBtnRef != null) runGraphAlgosBtnRef.setDisable(true);
        if (resetVisualizationBtnRef != null) resetVisualizationBtnRef.setDisable(true);

        // Re-enable configuration (directed/weighted choices etc.)
        resetGraphConfiguration();

        // Ensure any status label reflects cleared state
        common.sortingStatusLabel.setText("Create nodes and edges to enable algorithms.");

        // Update gating logic (will keep buttons disabled appropriately)
        updateGraphAlgoButtons();
    }
    
    private void enableSourceSelection(){
        if (bfsSource != null) return; // already chosen
        common.sortingStatusLabel.setText("Select a node as the source.");
        // subtle pulse animation on the button to show it's armed
        if (selectSourceBtnRef != null){
            ScaleTransition st = new ScaleTransition(Duration.millis(220), selectSourceBtnRef);
            st.setFromX(1.0); st.setFromY(1.0); st.setToX(1.13); st.setToY(1.13);
            st.setAutoReverse(true); st.setCycleCount(2); st.play();
        }
        for (GraphNode gn : graphNodes.values()) {
            Circle c = gn.getCircle(); if (c==null) continue;
            c.setOnMouseClicked(e-> {
                if (bfsSource != null) return;
                bfsSource = gn;
                highlightSourceNode(c);
                common.sortingStatusLabel.setText("Source set: "+gn.getId());
                restoreNodeClickHandlers();
                if (selectSourceBtnRef != null) ButtonManager.setCompactButtonDisabled(selectSourceBtnRef); // disable after choosing source
                if (addNodeButtonRef != null) ButtonManager.setCompactButtonDisabled(addNodeButtonRef);
                if (addEdgeButtonRef != null) ButtonManager.setCompactButtonDisabled(addEdgeButtonRef);
                // lock algorithm selection NOW (after source picked)
                lockAlgorithmSelection();
                updateGraphAlgoButtons();
            });
        }
        // Keep Set Source button enabled until a node is actually clicked
    }
    
 // Enable/disable algorithm workflow buttons based on selection & source
    private void updateGraphAlgoButtons(){
        boolean anyAlgo = graphAlgoChecks.values().stream().anyMatch(CheckBox::isSelected);
        if (selectSourceBtnRef != null) {
            boolean shouldDisableSetSource = !anyAlgo || graphNodes.isEmpty() || bfsSource != null;
            selectSourceBtnRef.setDisable(shouldDisableSetSource);
        }
        if (runGraphAlgosBtnRef != null) {
            boolean shouldDisableRun = !anyAlgo || bfsSource == null;
            runGraphAlgosBtnRef.setDisable(shouldDisableRun);
        }
    }
    
    private void highlightSourceNode(Circle c){
        c.setStroke(Color.web("#00ffa3"));
        c.setStrokeWidth(5);
        c.setFill(Color.web("#064663"));
        DropShadow glow = new DropShadow();
        glow.setColor(Color.web("#00ffa3"));
        glow.setRadius(18);
        glow.setSpread(0.6);
        c.setEffect(glow);
        ScaleTransition st = new ScaleTransition(Duration.millis(300), c);
        st.setFromX(1.0); st.setFromY(1.0); st.setToX(1.18); st.setToY(1.18);
        st.setAutoReverse(true); st.setCycleCount(2); st.play();
    }
    
    private void lockAlgorithmSelection(){
        graphAlgoSelectionLocked = true;
        graphAlgoChecks.values().forEach(cb -> cb.setDisable(true));
    }

    public void unlockAlgorithmSelection(){
        graphAlgoSelectionLocked = false;
        graphAlgoChecks.values().forEach(cb -> cb.setDisable(false));
    }
    
    private void resetVisualizationState() {
        // Stop any running visualization timeline immediately
        stopCurrentVisualization();
        
        // Clear all visualization colors and effects
        resetNodeColors();
        resetEdgeColors();
        restoreEdgeWeightLabels(); // Restore hidden edge weight labels
        clearVisualizationLabels();
        
        // Reset source selection
        bfsSource = null;
        
        // Unlock algorithm selection to allow new choices
        unlockAlgorithmSelection();
        
        // Unselect all algorithms
        graphAlgoChecks.values().forEach(cb -> cb.setSelected(false));
        
        // Re-enable the Set Source button and disable Run button
        if (selectSourceBtnRef != null) {
            ButtonManager.setCompactButtonDisabled(selectSourceBtnRef);
        }
        if (runGraphAlgosBtnRef != null) {
            runGraphAlgosBtnRef.setDisable(true);
        }
        
        // Disable the Reset Visualization button until algorithms run again
        if (resetVisualizationBtnRef != null) {
            resetVisualizationBtnRef.setDisable(true);
        }
        
        // Disable and reset the Pause button
        if (pauseBtnRef != null) {
            pauseBtnRef.setDisable(true);
            pauseBtnRef.setText("PAUSE");
            pauseBtnRef.setStyle("-fx-background-color: linear-gradient(45deg, #ffaa00, #ff8800); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 8 16; -fx-font-size: 12px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3),5,0,0,2);");
        }
        
        // Re-enable structural editing buttons (ADD NODE, ADD EDGE if configuration is complete)
        if (addNodeButtonRef != null) {
            ButtonManager.setCompactButtonEnabled(addNodeButtonRef);
        }
        if (addEdgeButtonRef != null && isConfigurationComplete) {
            ButtonManager.setCompactButtonEnabled(addEdgeButtonRef);
        }
        
        // Clear explanation area
        clearGraphExplanation();
        
        // Update status
        common.sortingStatusLabel.setText("Select algorithms to visualize on this graph.");
        
        // Restore normal node click handlers
        restoreNodeClickHandlers();
        
        // Update button states based on current selections
        updateGraphAlgoButtons();
    }
    
    private void stopCurrentVisualization() {
        // Stop any currently running visualization timeline
        if (currentVisualizationTimeline != null) {
            currentVisualizationTimeline.stop();
            currentVisualizationTimeline = null;
        }
        // Reset pause state and disable pause button
        isPaused = false;
        if (pauseBtnRef != null) {
            pauseBtnRef.setDisable(true);
            pauseBtnRef.setText("PAUSE");
            pauseBtnRef.setStyle("-fx-background-color: linear-gradient(45deg, #ffaa00, #ff8800); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 8 16; -fx-font-size: 12px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3),5,0,0,2);");
        }
    }
    
    private void enablePauseButton() {
        // Enable pause button when animation starts
        isPaused = false;
        if (pauseBtnRef != null) {
            pauseBtnRef.setDisable(false);
            pauseBtnRef.setText("PAUSE");
            pauseBtnRef.setStyle("-fx-background-color: linear-gradient(45deg, #ffaa00, #ff8800); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 8 16; -fx-font-size: 12px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3),5,0,0,2);");
        }
        
        // Set up completion handler for current timeline
        if (currentVisualizationTimeline != null) {
            currentVisualizationTimeline.setOnFinished(e -> {
                isPaused = false;
                if (pauseBtnRef != null) {
                    pauseBtnRef.setDisable(true);
                    pauseBtnRef.setText("PAUSE");
                    pauseBtnRef.setStyle("-fx-background-color: linear-gradient(45deg, #ffaa00, #ff8800); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 8 16; -fx-font-size: 12px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3),5,0,0,2);");
                }
                currentVisualizationTimeline = null;  // Clear reference when animation completes
            });
        }
    }
    
    private void restoreNodeClickHandlers(){
        for (GraphNode gn: graphNodes.values()) {
            Circle c = gn.getCircle(); if(c!=null){
                c.setOnMouseClicked(ev -> onGraphNodeClicked(gn));
            }
        }
    }
    
    private void onGraphNodeClicked(GraphNode gn) { /* To be updated by Labib */ }
    
    private void runChosenGraphAlgorithms(){
        System.out.println("DEBUG: runChosenGraphAlgorithms called");
        System.out.println("DEBUG: bfsSource=" + (bfsSource != null ? bfsSource.getId() : "null"));
        
        if (bfsSource==null){ 
        	common.sortingStatusLabel.setText("Set source first."); 
            System.out.println("DEBUG: No source set - returning");
            return; 
        }
        
        System.out.println("DEBUG: Checking algorithm selections:");
        System.out.println("DEBUG: BFS selected=" + isSelected("BFS"));
        System.out.println("DEBUG: DFS selected=" + isSelected("DFS"));
        System.out.println("DEBUG: Dijkstra selected=" + isSelected("Dijkstra"));
        System.out.println("DEBUG: Bellman-Ford selected=" + isSelected("Bellman-Ford"));
        
        if (isSelected("BFS")) visualizeBFS(bfsSource);
        if (isSelected("DFS")) visualizeDFS(bfsSource);
        if (isSelected("Dijkstra")) visualizeDijkstra(bfsSource);
        if (isSelected("Bellman-Ford")) visualizeBellmanFord(bfsSource);
        if (isSelected("Floyd-Warshall")) visualizeFloydWarshall();
        if (isSelected("Prim")) visualizePrim();
        if (isSelected("Kruskal")) visualizeKruskal();
        
        // Enable the Reset Visualization button after algorithms have been executed
        if (resetVisualizationBtnRef != null) {
            resetVisualizationBtnRef.setDisable(false);
        }
        
        // Disable the Run button since algorithms are now running/completed
        if (runGraphAlgosBtnRef != null) {
            runGraphAlgosBtnRef.setDisable(true);
        }
    }
    
    private boolean isSelected(String name){ 
    	CheckBox cb = graphAlgoChecks.get(name); 
    	return cb!=null && cb.isSelected(); 
    }
    
    private void visualizeBFS(GraphNode start){
        // Reset all node colors first
        resetNodeColors();
        
        Queue<GraphNode> q = new ArrayDeque<>();
        Set<GraphNode> visited = new HashSet<>();
        q.add(start); visited.add(start);
        
        Timeline tl = new Timeline();
        int step = 0;
        
        // Highlight starting node
        Circle startCircle = start.getCircle();
        tl.getKeyFrames().add(new KeyFrame(Duration.millis(getAnimationDelay(step * 600)), e -> {
            startCircle.setFill(Color.web("#e74c3c")); // Red for start
            startCircle.setStroke(Color.web("#c0392b"));
            startCircle.setStrokeWidth(4);
            common.sortingStatusLabel.setText("BFS: Starting from node " + start.getId());
            updateGraphExplanation("BFS START: Enqueue " + start.getId());
        }));
        step++;
        
        while(!q.isEmpty()){
            GraphNode current = q.poll();
            Circle currentCircle = current.getCircle();
            if(currentCircle == null) continue;
            
            final int currentStep = step;
            
            // Highlight current node being processed
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(getAnimationDelay(currentStep * 600)), e -> {
                currentCircle.setFill(Color.web("#f39c12")); // Orange for processing
                currentCircle.setStroke(Color.web("#e67e22"));
                currentCircle.setStrokeWidth(4);
                common.sortingStatusLabel.setText("BFS: Processing node " + current.getId());
                updateGraphExplanation("BFS PROCESS: Dequeue " + current.getId());
            }));
            
            // Find neighbors and add them to queue
            List<GraphNode> neighbors = new ArrayList<>();
            for(EdgeRecord er: edges) {
                GraphNode neighbor = null;
                Line edge = er.line;
                
                if(er.a.equals(current.getId())) {
                    neighbor = graphNodes.get(er.b);
                } else if(!isDirected && er.b.equals(current.getId())) {
                    neighbor = graphNodes.get(er.a);
                }
                
                if(neighbor != null && visited.add(neighbor)) {
                    q.add(neighbor);
                    neighbors.add(neighbor);
                    
                    final GraphNode finalNeighbor = neighbor;
                    final Line finalEdge = edge;
                    
                    // Highlight edge traversal
                    tl.getKeyFrames().add(new KeyFrame(Duration.millis(getAnimationDelay(currentStep * 600 + 200)), e -> {
                        finalEdge.setStroke(Color.web("#3498db"));
                        finalEdge.setStrokeWidth(4);
                        updateGraphExplanation("BFS EDGE: Explore " + current.getId() + " → " + finalNeighbor.getId());
                    }));
                    
                    // Highlight discovered neighbor
                    tl.getKeyFrames().add(new KeyFrame(Duration.millis(getAnimationDelay(currentStep * 600 + 400)), e -> {
                        Circle nc = finalNeighbor.getCircle();
                        if(nc != null) {
                            nc.setFill(Color.web("#3498db")); // Blue for discovered
                            nc.setStroke(Color.web("#2980b9"));
                            nc.setStrokeWidth(3);
                        }
                        updateGraphExplanation("BFS DISCOVER: Mark & enqueue " + finalNeighbor.getId());
                    }));
                }
            }
            
            // Mark current node as completely processed
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(getAnimationDelay(currentStep * 600 + 600)), e -> {
                currentCircle.setFill(Color.web("#27ae60")); // Green for completed
                currentCircle.setStroke(Color.web("#229954"));
                updateGraphExplanation("BFS DONE: Finished " + current.getId());
            }));
            
            step++;
        }
        
        // Final completion message
        tl.getKeyFrames().add(new KeyFrame(Duration.millis(getAnimationDelay(step * 600)), e -> {
        	common.sortingStatusLabel.setText("BFS: Traversal complete!");
            updateGraphExplanation("BFS COMPLETE");
        }));
        
        // Store timeline for potential stopping and play
        currentVisualizationTimeline = tl;
        enablePauseButton();
        tl.play();
    }
    private void visualizeDFS(GraphNode start){
        // Reset all node colors first
        resetNodeColors();
        
        Set<GraphNode> visited = new HashSet<>();
        List<DFSStep> dfsSteps = new ArrayList<>();
        
        // Generate DFS steps with proper recursion simulation
        generateDFSSteps(start, visited, dfsSteps, 0);
        
        // Create timeline animation for the steps
        Timeline tl = new Timeline();
        
        for(int i = 0; i < dfsSteps.size(); i++) {
            DFSStep step = dfsSteps.get(i);
            final int stepIndex = i;
            
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(getAnimationDelay(stepIndex * 600)), e -> {
                executeDrawStep(step, stepIndex);
            }));
        }
        
        // Final completion message
        tl.getKeyFrames().add(new KeyFrame(Duration.millis(getAnimationDelay(dfsSteps.size() * 600)), e -> {
        	common.sortingStatusLabel.setText("DFS: Depth-first traversal complete!");
            updateGraphExplanation("DFS COMPLETE");
        }));
        
        // Store timeline for potential stopping and play
        currentVisualizationTimeline = tl;
        enablePauseButton();
        tl.play();
    }
    
    private void resetNodeColors() {
        for (GraphNode node : graphNodes.values()) {
            Circle c = node.getCircle();
            if (c != null) {
                c.setFill(Color.web("#0097a7", 0.9));
                c.setStroke(Color.web("#00ffff"));
                c.setStrokeWidth(2.0);
                // Clear any special effects (like the source node glow)
                c.setEffect(null);
            }
        }
        for (EdgeRecord edge : edges) {
            edge.line.setStroke(Color.web("#00ffff"));
            edge.line.setStrokeWidth(1.0);
        }
    }
    
    private int getAnimationDelay(int baseDelay) {
        return (int) (baseDelay / animationSpeed);
    }
    
    private void updateGraphExplanation(String text) {
        System.out.println("DEBUG: updateGraphExplanation called with text: " + text);
        System.out.println("DEBUG: graphExplanationArea is null: " + (graphExplanationArea == null));
        
        if (graphExplanationArea == null) return;
        if (text == null || text.isBlank()) return;
        if (text.equals(lastGraphExplanation)) return;
        String existing = graphExplanationArea.getText();
        String combined = existing.isBlank()? text : existing + (existing.endsWith("\n")? "" : "\n") + text;
        String[] lines = combined.split("\n");
        if (lines.length > 80) { // keep last 80 lines
            lines = java.util.Arrays.copyOfRange(lines, lines.length-80, lines.length);
            combined = String.join("\n", lines);
        }
        if (combined.length() > 16000) combined = combined.substring(combined.length()-16000);
        graphExplanationArea.setText(combined);
        graphExplanationArea.positionCaret(graphExplanationArea.getText().length());
        lastGraphExplanation = text;
        System.out.println("DEBUG: graphExplanationArea updated successfully");
    }
    
    private void generateDFSSteps(GraphNode current, Set<GraphNode> visited, List<DFSStep> steps, int depth) {
        // Check if already visited
        if (visited.contains(current)) {
            return;
        }
        
        // Visit current node
        visited.add(current);
        steps.add(new DFSStep(DFSStep.Type.VISIT, current.getId(), null, depth));
        
        // Find all neighbors in consistent order (by node ID)
        List<String> neighbors = new ArrayList<>();
        for(EdgeRecord er: edges) {
            if(er.a.equals(current.getId())) {
                // For directed graphs, only follow outgoing edges
                neighbors.add(er.b);
            } else if(!isDirected && er.b.equals(current.getId())) {
                // For undirected graphs, can go both ways
                neighbors.add(er.a);
            }
        }
        
        // Sort neighbors for consistent ordering
        neighbors.sort(String::compareTo);
        
        // Explore each neighbor
        for(String neighborId : neighbors) {
            GraphNode neighbor = graphNodes.get(neighborId);
            if(neighbor != null) {
                // Always show edge exploration
                steps.add(new DFSStep(DFSStep.Type.EXPLORE_EDGE, current.getId(), neighborId, depth));
                
                if(!visited.contains(neighbor)) {
                    // Recursive exploration only if not visited
                    generateDFSSteps(neighbor, visited, steps, depth + 1);
                    // Add backtrack step after returning from recursion
                    steps.add(new DFSStep(DFSStep.Type.BACKTRACK, current.getId(), neighborId, depth));
                } else {
                    // Neighbor already visited; schedule edge reset to remove orange highlight
                    steps.add(new DFSStep(DFSStep.Type.EDGE_RESET, current.getId(), neighborId, depth));
                }
            }
        }
        
        // Finish processing current node
        steps.add(new DFSStep(DFSStep.Type.FINISH, current.getId(), null, depth));
    }
    
    private void executeDrawStep(DFSStep step, int stepIndex) {
        Circle nodeCircle = graphNodes.get(step.nodeId) != null ? graphNodes.get(step.nodeId).getCircle() : null;
        
        switch(step.type) {
            case VISIT:
                if(nodeCircle != null) {
                    nodeCircle.setFill(Color.web("#e74c3c")); // Red for visiting
                    nodeCircle.setStroke(Color.web("#c0392b"));
                    nodeCircle.setStrokeWidth(4);
                    
                    // Add pulsing animation
                    ScaleTransition pulse = new ScaleTransition(Duration.millis(300), nodeCircle);
                    pulse.setFromX(1.0); pulse.setFromY(1.0);
                    pulse.setToX(1.3); pulse.setToY(1.3);
                    pulse.setAutoReverse(true); pulse.setCycleCount(2);
                    pulse.play();
                }
                common.sortingStatusLabel.setText("DFS: Visiting node " + step.nodeId + " (depth: " + step.depth + ")");
                updateGraphExplanation("DFS VISIT: Enter node " + step.nodeId + " (depth=" + step.depth + ")");
                break;
                
            case EXPLORE_EDGE:
                // Highlight the edge being explored
                EdgeRecord edgeToHighlight = findEdge(step.nodeId, step.targetNodeId);
                if(edgeToHighlight != null) {
                    edgeToHighlight.line.setStroke(Color.web("#f39c12")); // Orange for exploration
                    edgeToHighlight.line.setStrokeWidth(4);
                }
                common.sortingStatusLabel.setText("DFS: Exploring edge " + step.nodeId + " → " + step.targetNodeId);
                updateGraphExplanation("DFS EXPLORE: From " + step.nodeId + " to " + step.targetNodeId);
                break;
                
            case BACKTRACK:
                // Show backtracking with different color
                EdgeRecord backtrackEdge = findEdge(step.nodeId, step.targetNodeId);
                if(backtrackEdge != null) {
                    backtrackEdge.line.setStroke(Color.web("#9b59b6")); // Purple for backtrack
                    backtrackEdge.line.setStrokeWidth(3);
                }
                common.sortingStatusLabel.setText("DFS: Backtracking from " + step.targetNodeId + " to " + step.nodeId);
                updateGraphExplanation("DFS BACKTRACK: Return to " + step.nodeId + " from " + step.targetNodeId);
                break;
            case EDGE_RESET:
                // Edge to an already visited node: change from exploration orange to relaxed purple
                EdgeRecord resetEdge = findEdge(step.nodeId, step.targetNodeId);
                if(resetEdge != null) {
                    resetEdge.line.setStroke(Color.web("#9b59b6")); // purple relaxed
                    resetEdge.line.setStrokeWidth(3);
                }
                common.sortingStatusLabel.setText("DFS: Already visited — edge " + step.nodeId + " → " + step.targetNodeId + " relaxed");
                updateGraphExplanation("DFS SKIP: Neighbor " + step.targetNodeId + " already visited");
                break;
                
            case FINISH:
                if(nodeCircle != null) {
                    nodeCircle.setFill(Color.web("#27ae60")); // Green for finished
                    nodeCircle.setStroke(Color.web("#229954"));
                    nodeCircle.setStrokeWidth(3);
                }
                common.sortingStatusLabel.setText("DFS: Finished processing node " + step.nodeId);
                updateGraphExplanation("DFS FINISH: Complete node " + step.nodeId);
                break;
        }
    }
    
    private EdgeRecord findEdge(String nodeA, String nodeB) {
        for(EdgeRecord er : edges) {
            if((er.a.equals(nodeA) && er.b.equals(nodeB)) ||
               (!isDirected && er.b.equals(nodeA) && er.a.equals(nodeB))) {
                return er;
            }
        }
        return null;
    }
    
    private void visualizeDijkstra(GraphNode start){
        // Reset all node colors first
        resetNodeColors();
	    // Clear previous labels & create fresh ∞ labels
	    resetDijkstraLabels();
	    for(GraphNode gn: graphNodes.values()) ensureDijkstraLabel(gn);
	    updateGraphExplanation("Dijkstra INIT: All distances set to ∞ except source.");
	    
	    final int BASE_STEP_MS = 1200;          // Increased from 600 for slower visualization
	    final int CHECK_EDGE_OFFSET = 300;      // Increased from 150 for more clear edge checking
	    final int RELAX_OFFSET = 600;           // Increased from 300 for better relaxation visualization  
	    final int SETTLE_OFFSET = 900;          // Increased from 450 for clearer node settling         
        
        Map<String, Double> dist = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        Set<String> visited = new HashSet<>();
        
        // Initialize distances
        for(String id: graphNodes.keySet()) {
            dist.put(id, Double.POSITIVE_INFINITY);
        }
        dist.put(start.getId(), 0.0);
        
        PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparingDouble(dist::get));
        pq.add(start.getId());
        
        Timeline tl = new Timeline();
        int step = 0;
        
        // Highlight starting node with enhanced visibility
        Circle startCircle = start.getCircle();
        tl.getKeyFrames().add(new KeyFrame(Duration.millis(getAnimationDelay(step * BASE_STEP_MS)), e -> {
            startCircle.setFill(Color.web("#e74c3c")); // Red for start
            startCircle.setStroke(Color.web("#c0392b"));
            startCircle.setStrokeWidth(6); // Thicker stroke for source node
            common.sortingStatusLabel.setText("Dijkstra: Starting from node " + start.getId() + " (distance: 0)");
            Label lbl = dijkstraDistLabels.get(start.getId()); if(lbl!=null) lbl.setText("0");
            updateGraphExplanation("Source set: distance["+start.getId()+"] = 0");
        }));
        step++;
        
        while(!pq.isEmpty()) {
            String currentId = pq.poll();
            if(visited.contains(currentId)) continue;
            visited.add(currentId);
            
            GraphNode current = graphNodes.get(currentId);
            Circle currentCircle = current.getCircle();
            if(currentCircle == null) continue;
            
            final int currentStep = step;
            final double currentDist = dist.get(currentId);
            
            // Highlight current node being processed
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(getAnimationDelay(currentStep * BASE_STEP_MS)), e -> {
                // Reset all edge colors to default first for better step-by-step visualization
                for(EdgeRecord resetEdge : edges) {
                    if(resetEdge.line != null) {
                        resetEdge.line.setStroke(Color.web("#00ffff"));
                        resetEdge.line.setStrokeWidth(1.0);
                    }
                }
                
                currentCircle.setFill(Color.web("#9b59b6")); // Purple for processing
                currentCircle.setStroke(Color.web("#8e44ad"));
                currentCircle.setStrokeWidth(4);
                common.sortingStatusLabel.setText("Dijkstra: Processing node " + currentId + " (distance: " + formatDist(currentDist) + ")");
                updateGraphExplanation("EXTRACT MIN: " + currentId + " with distance " + formatDist(currentDist));
            }));
            
            // Check all neighbors for relaxation
            for(EdgeRecord er: edges) {
                String neighborId = null;
                double weight = er.weight;
                
                if(er.a.equals(currentId)) {
                    neighborId = er.b;
                } else if(!isDirected && er.b.equals(currentId)) {
                    neighborId = er.a;
                }
                
                if(neighborId != null && !visited.contains(neighborId)) {
                    double newDist = dist.get(currentId) + weight;
                    
                    if(newDist < dist.get(neighborId)) {
                        dist.put(neighborId, newDist);
                        parent.put(neighborId, currentId);
                        pq.remove(neighborId);
                        pq.add(neighborId);
                        
                        final String finalNeighborId = neighborId;
                        final double finalNewDist = newDist;
                        final Line edge = er.line;
                        
                        // Highlight edge being relaxed with better visibility
                        tl.getKeyFrames().add(new KeyFrame(Duration.millis(getAnimationDelay(currentStep * BASE_STEP_MS + CHECK_EDGE_OFFSET)), e -> {
                            edge.setStroke(Color.web("#f39c12")); // Orange for relaxation attempt
                            edge.setStrokeWidth(5); // Increased width for better visibility
                            updateGraphExplanation("CHECK edge " + currentId + " → " + finalNeighborId + " (w=" + formatDist(weight) + ")");
                        }));

                        // Update neighbor distance (successful relaxation) with enhanced visual feedback
                        tl.getKeyFrames().add(new KeyFrame(Duration.millis(getAnimationDelay(currentStep * BASE_STEP_MS + RELAX_OFFSET)), e -> {
                            Circle nc = graphNodes.get(finalNeighborId).getCircle();
                            if(nc != null) {
                                nc.setFill(Color.web("#3498db")); // Blue for updated
                                nc.setStroke(Color.web("#2980b9"));
                                nc.setStrokeWidth(4); // Increased for better visibility
                            }
                            // Make the relaxed edge green to show successful relaxation
                            edge.setStroke(Color.web("#27ae60")); // Green for successful relaxation
                            edge.setStrokeWidth(4);
                            
                            Label lbl = dijkstraDistLabels.get(finalNeighborId); if(lbl!=null) lbl.setText(formatDist(finalNewDist));
                            common.sortingStatusLabel.setText("Dijkstra: Updated distance to " + finalNeighborId + ": " + formatDist(finalNewDist));
                            updateGraphExplanation("RELAX: distance["+finalNeighborId+"] = " + formatDist(finalNewDist) + " via " + currentId);
                        }));
                    } else {
                        final String skipNeighbor = neighborId;
                        final double altDist = newDist;
                        final Line edge2 = er.line;
                        // Show edge being checked but not relaxed with better visual feedback
                        tl.getKeyFrames().add(new KeyFrame(Duration.millis(getAnimationDelay(currentStep * BASE_STEP_MS + CHECK_EDGE_OFFSET)), e -> {
                            edge2.setStroke(Color.web("#e74c3c")); // Red for no improvement
                            edge2.setStrokeWidth(3); // Thicker for better visibility
                            updateGraphExplanation("NO IMPROVEMENT: edge " + currentId + " → " + skipNeighbor + " alt=" + formatDist(altDist) + " ≥ current " + formatDist(dist.get(skipNeighbor)));
                        }));
                    }
                }
            }
            
            // Mark current node as completed with enhanced visual feedback
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(getAnimationDelay(currentStep * BASE_STEP_MS + SETTLE_OFFSET)), e -> {
                currentCircle.setFill(Color.web("#27ae60")); // Green for completed
                currentCircle.setStroke(Color.web("#229954"));
                currentCircle.setStrokeWidth(5); // Thicker stroke for settled nodes
                updateGraphExplanation("SETTLED: " + currentId + " final distance = " + formatDist(dist.get(currentId)));
            }));
            
            step++;
        }
        
        // Final completion message
	    tl.getKeyFrames().add(new KeyFrame(Duration.millis(getAnimationDelay(step * BASE_STEP_MS)), e -> {
	    	common.sortingStatusLabel.setText("Dijkstra: Shortest path algorithm complete!");
	            updateGraphExplanation("DIJKSTRA COMPLETE");
	    }));
        
        // Store timeline for potential stopping and play
        currentVisualizationTimeline = tl;
        enablePauseButton();
        tl.play();
    }
    
    private void resetDijkstraLabels(){
        if(graphCanvas!=null){
            for(Label t: dijkstraDistLabels.values()) graphCanvas.getChildren().remove(t);
        }
        dijkstraDistLabels.clear();
    }
    
    private void ensureDijkstraLabel(GraphNode node){
        if(node==null) return;
        if(dijkstraDistLabels.containsKey(node.getId())) return;
        Circle c = node.getCircle(); if(c==null || graphCanvas==null) return;
        Label box = new Label("∞");
        box.setAlignment(Pos.CENTER);
        box.setMinSize(30, 18);
        box.setPrefSize(32, 18);
        box.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #0d2533, #091a24);" +
            "-fx-text-fill:#e0faff; -fx-font-size:11px; -fx-font-weight:700;" +
            "-fx-padding:2 6; -fx-background-radius:8; -fx-border-radius:8;" +
            "-fx-border-color:#00ffff99; -fx-border-width:1;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,255,255,0.35),4,0,0,0);"
        );
        positionDijkstraLabel(node, box);
        graphCanvas.getChildren().add(box);
        dijkstraDistLabels.put(node.getId(), box);
    }

    private void positionDijkstraLabel(GraphNode node, Label box){
        Circle c = node.getCircle(); if(c==null) return;
        double r = c.getRadius();
        // place to upper-right of the circle
        box.setLayoutX(c.getCenterX() + r + 4);
        box.setLayoutY(c.getCenterY() - r - 4); // slight shift upward to feel anchored
    }

    private void updateAllDijkstraLabelPositions(){
        for(Map.Entry<String,Label> e: dijkstraDistLabels.entrySet()){
            GraphNode gn = graphNodes.get(e.getKey());
            positionDijkstraLabel(gn, e.getValue());
        }
    }
    
    private String formatDist(double d){
        if(Double.isInfinite(d)) return "∞";
        if(d==Math.rint(d)) return String.format("%.0f", d);
        return String.format("%.1f", d);
    }
    
    private void clearGraphExplanation() {
        if (graphExplanationArea != null) graphExplanationArea.clear();
        lastGraphExplanation = "";
    }
    
    private void resetEdgeColors() {
        for (EdgeRecord edge : edges) {
            if (edge.line != null) {
                edge.line.setStroke(Color.web("#00ffff"));
                edge.line.setStrokeWidth(1.0);
            }
        }
    }
    
    private void restoreEdgeWeightLabels() {
        // Restore visibility of all edge weight labels that may have been hidden during MST algorithms
        for (EdgeRecord edge : edges) {
            if (edge.weightLabel != null) {
                edge.weightLabel.setVisible(true);
            }
        }
    }
    
    private void clearVisualizationLabels() {
        // Clear Dijkstra distance labels
        for (Label label : dijkstraDistLabels.values()) {
            if (label != null && label.getParent() != null) {
                ((Pane) label.getParent()).getChildren().remove(label);
            }
        }
        dijkstraDistLabels.clear();
        
        // Clear Bellman-Ford distance labels
        for (Label label : bellmanFordDistLabels.values()) {
            if (label != null && label.getParent() != null) {
                ((Pane) label.getParent()).getChildren().remove(label);
            }
        }
        bellmanFordDistLabels.clear();
    }
    
    private void showErrorInExplanation(String text) {
        // For now, we'll use the regular updateGraphExplanation method
        // In a more advanced implementation, we could style the text area to show red text
        updateGraphExplanation(text);
    }
    
    private void visualizeBellmanFord(GraphNode start){
        System.out.println("DEBUG: visualizeBellmanFord called with start=" + (start != null ? start.getId() : "null"));
        System.out.println("DEBUG: isWeighted=" + isWeighted + ", edges.size()=" + edges.size() + ", graphNodes.size()=" + graphNodes.size());
        
        clearGraphExplanation(); // Clear previous explanations
        
        // Check prerequisites and show detailed error messages with clear red styling
        if (!isWeighted) {
            System.out.println("DEBUG: Not weighted - showing error");
            showErrorInExplanation("BELLMAN-FORD ALGORITHM ERROR!");
            showErrorInExplanation("This algorithm is NOT APPLICABLE for unweighted graphs!");
            showErrorInExplanation("");
            showErrorInExplanation("WHY THIS ERROR OCCURRED:");
            showErrorInExplanation("   • Bellman-Ford is designed for weighted graphs only");
            showErrorInExplanation("   • It finds shortest paths based on edge weights");
            showErrorInExplanation("   • Unweighted graphs should use BFS instead");
            showErrorInExplanation("");
            showErrorInExplanation("HOW TO FIX:");
            showErrorInExplanation("   1. Enable 'Weighted' option in graph settings");
            showErrorInExplanation("   2. Add weights to your edges");
            showErrorInExplanation("   3. OR use BFS for unweighted shortest paths");
            showErrorInExplanation("");
            showErrorInExplanation("TIP: For unweighted graphs, all edges have weight 1,");
            showErrorInExplanation("   so BFS finds shortest paths more efficiently!");
            return;
        }
        
        if (edges.isEmpty()) {
            System.out.println("DEBUG: No edges - showing error");
            showErrorInExplanation("BELLMAN-FORD ALGORITHM ERROR!");
            showErrorInExplanation("This algorithm is NOT APPLICABLE - No edges found!");
            showErrorInExplanation("");
            showErrorInExplanation("WHY THIS ERROR OCCURRED:");
            showErrorInExplanation("   • Bellman-Ford requires edges to find shortest paths");
            showErrorInExplanation("   • Cannot compute distances without connections");
            showErrorInExplanation("");
            showErrorInExplanation("HOW TO FIX:");
            showErrorInExplanation("   1. Add edges between nodes in your graph");
            showErrorInExplanation("   2. Make sure edges have weights");
            showErrorInExplanation("   3. Then try running Bellman-Ford again");
            return;
        }
        
        if (graphNodes.size() < 2) {
            System.out.println("DEBUG: Not enough nodes - showing error");
            showErrorInExplanation("BELLMAN-FORD ALGORITHM ERROR!");
            showErrorInExplanation("This algorithm is NOT APPLICABLE - Need at least 2 nodes!");
            showErrorInExplanation("");
            showErrorInExplanation("WHY THIS ERROR OCCURRED:");
            showErrorInExplanation("   • Bellman-Ford finds shortest paths between nodes");
            showErrorInExplanation("   • Need at least 2 nodes to have meaningful paths");
            showErrorInExplanation("");
            showErrorInExplanation("HOW TO FIX:");
            showErrorInExplanation("   1. Add more nodes to your graph");
            showErrorInExplanation("   2. Connect them with weighted edges");
            showErrorInExplanation("   3. Then try running Bellman-Ford again");
            return;
        }
        
        // Algorithm is applicable - proceed with normal execution
        System.out.println("DEBUG: Starting normal Bellman-Ford execution");
        updateGraphExplanation("Starting Bellman-Ford from " + start.getId() + " (Source=0, Others=∞)");
        
        Map<String, Double> dist = new HashMap<>(); 
        
        // Clear any existing Bellman-Ford distance labels
        for (Label label : bellmanFordDistLabels.values()) {
            if (label != null && label.getParent() != null) {
                ((Pane) label.getParent()).getChildren().remove(label);
            }
        }
        bellmanFordDistLabels.clear();
        
        // Initialize distances and create distance labels
        for(String id: graphNodes.keySet()) {
            dist.put(id, Double.POSITIVE_INFINITY);
            
            // Create distance label for each node with cyan styling for Bellman-Ford
            GraphNode node = graphNodes.get(id);
            Label distLabel = new Label();
            distLabel.setStyle(
                "-fx-text-fill: white;" +
                "-fx-font-size: 13px;" +
                "-fx-font-weight: 650;" +
                "-fx-background-color: rgba(0, 255, 255, 0.85);" +  // Cyan background
                "-fx-background-radius: 3;" +
                "-fx-padding: 2 5;" +
                "-fx-border-color: #00ffff;" +  // Cyan border
                "-fx-border-width: 0.5;" +
                "-fx-border-radius: 3;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 6, 0.5, 1, 1);"
            );
            distLabel.setManaged(true);
            distLabel.setMouseTransparent(true);
            distLabel.setVisible(true);
            
            // Position label above the node, centered horizontally and a bit lower
            distLabel.layoutXProperty().bind(node.getCircle().centerXProperty().subtract(distLabel.widthProperty().divide(2)));
            distLabel.layoutYProperty().bind(node.getCircle().centerYProperty().subtract(60));  // Reduced from 75 to make it lower
            
            // Set initial distance display
            if (id.equals(start.getId())) {
                distLabel.setText("0");
            } else {
                distLabel.setText("∞");
            }
            
            bellmanFordDistLabels.put(id, distLabel);
            graphCanvas.getChildren().add(distLabel);
        }
        
        dist.put(start.getId(), 0.0);
        
        Timeline tl=new Timeline(); 
        int step=0; 
        int n=graphNodes.size();
        
        updateGraphExplanation("Will run " + (n-1) + " iterations to relax all edges");
        
        // Main relaxation loop (ALWAYS V-1 iterations - no early termination)
        for(int i=0;i<n-1;i++){
            final int iteration = i + 1;
            
            // Show iteration header
            tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                updateGraphExplanation("Iteration " + iteration + "/" + (n-1) + " - Checking all edges for relaxation");
            }));
            
            boolean[] anyRelaxedInIteration = {false};
            
            for(EdgeRecord er: edges){ 
                double w=er.weight;
                
                // Show which edge we're examining and highlight both nodes and edge
                tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                    updateGraphExplanation("Examining edge " + er.a + "→" + er.b + " (weight " + w + ")");
                    // Highlight the edge being examined
                    er.line.setStroke(Color.web("#FFA500")); // Orange highlight
                    er.line.setStrokeWidth(4);
                    // Highlight both nodes involved in the edge
                    graphNodes.get(er.a).getCircle().setFill(Color.web("#E67E22")); // Orange for source
                    graphNodes.get(er.b).getCircle().setFill(Color.web("#F39C12")); // Lighter orange for target
                }));
                
                if(dist.get(er.a)!=Double.POSITIVE_INFINITY && dist.get(er.a)+w<dist.get(er.b)){ 
                    double oldDist = dist.get(er.b);
                    dist.put(er.b, dist.get(er.a)+w); 
                    anyRelaxedInIteration[0] = true;
                    final String toNode = er.b;
                    final double newDist = dist.get(er.a)+w;
                    final Label distLabel = bellmanFordDistLabels.get(toNode);
                    int delay= getAnimationDelay(step++*600);
                    Circle c = graphNodes.get(er.b).getCircle();
                    tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(delay), e-> {
                        c.setFill(Color.web("#27AE60")); // Green for successful relaxation
                        // Update distance label with animation
                        distLabel.setText(String.format("%.1f", newDist));
                        distLabel.setStyle(distLabel.getStyle().replaceAll("-fx-background-color: rgba\\([^)]+\\);", "-fx-background-color: rgba(39, 174, 96, 0.9);")); // Green background when updated
                        updateGraphExplanation("✓ Relaxed! " + er.b + ": " + String.format("%.1f", oldDist) + " → " + String.format("%.1f", newDist));
                    }));
                    
                    // Add a pause to show the result before moving to next edge
                    tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(delay + 600), e-> {
                        updateGraphExplanation("Edge " + er.a + "→" + er.b + " processing complete");
                    }));
                } else {
                    // Edge not relaxed
                    tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                        if (dist.get(er.a) == Double.POSITIVE_INFINITY) {
                            updateGraphExplanation("✗ No relaxation: " + er.a + " unreachable");
                        } else {
                            double currentPath = dist.get(er.a) + w;
                            double existingDist = dist.get(er.b);
                            String existingStr = existingDist == Double.POSITIVE_INFINITY ? "∞" : String.format("%.1f", existingDist);
                            updateGraphExplanation("✗ No relaxation: " + String.format("%.1f", currentPath) + " ≥ " + existingStr);
                        }
                    }));
                    
                    // Add a pause to show the result before moving to next edge
                    tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step*600 + 300)), e-> {
                        updateGraphExplanation("Edge " + er.a + "→" + er.b + " processing complete");
                    }));
                    step++; // Increment step for next edge
                }
                
                // Reset edge and node colors after processing is complete, with delay
                tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                    // Reset edge color and width
                    er.line.setStroke(Color.WHITE);
                    er.line.setStrokeWidth(2);
                    // Reset node colors to default (but keep distance labels as they are)
                    graphNodes.get(er.a).getCircle().setFill(Color.web("#3498db"));
                    graphNodes.get(er.b).getCircle().setFill(Color.web("#3498db"));
                }));
                
                // Handle undirected graphs
                if(!isDirected && dist.get(er.b)!=Double.POSITIVE_INFINITY && dist.get(er.b)+w<dist.get(er.a)){ 
                    double oldDist = dist.get(er.a);
                    dist.put(er.a, dist.get(er.b)+w); 
                    anyRelaxedInIteration[0] = true;
                    final String toNode = er.a;
                    final double newDist = dist.get(er.b)+w;
                    final Label distLabel = bellmanFordDistLabels.get(toNode);
                    int delay= getAnimationDelay(step++*600);
                    Circle c2 = graphNodes.get(er.a).getCircle();
                    tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(delay), e-> {
                        c2.setFill(Color.web("#27AE60")); // Green for successful relaxation
                        // Update distance label
                        distLabel.setText(String.format("%.1f", newDist));
                        distLabel.setStyle(distLabel.getStyle().replaceAll("-fx-background-color: rgba\\([^)]+\\);", "-fx-background-color: rgba(39, 174, 96, 0.9);")); // Green background when updated
                        updateGraphExplanation("✓ Relaxed! " + er.a + ": " + String.format("%.1f", oldDist) + " → " + String.format("%.1f", newDist));
                    }));
                    
                    // Add a pause for undirected edge result
                    tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(delay + 600), e-> {
                        updateGraphExplanation("Reverse edge " + er.b + "→" + er.a + " also processed");
                    }));
                }
                
                // Add a clear separation before moving to the next edge
                tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                    updateGraphExplanation("---"); // Visual separator
                }));
            }
            
            // Show iteration summary
            tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                if (anyRelaxedInIteration[0]) {
                    updateGraphExplanation("Iteration " + iteration + " complete: Distances improved");
                } else {
                    updateGraphExplanation("Iteration " + iteration + " complete: No improvements");
                }
            }));
            
            // Reset all distance label backgrounds to cyan after each iteration
            tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                // Reset distance label backgrounds to cyan
                for(Label label : bellmanFordDistLabels.values()) {
                    label.setStyle(label.getStyle().replaceAll("-fx-background-color: rgba\\([^)]+\\);", "-fx-background-color: rgba(0, 255, 255, 0.85);"));
                }
                updateGraphExplanation("Ready for next iteration...");
            }));
        }
        
        // Check for negative cycles
        tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
            updateGraphExplanation("Phase 2: Checking for negative cycles");
        }));
        
        boolean[] hasNegativeCycle = {false};
        for(EdgeRecord er: edges){ 
            double w=er.weight; 
            
            tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                updateGraphExplanation("Checking edge " + er.a + "→" + er.b + " for relaxation");
                er.line.setStroke(Color.web("#E74C3C")); // Red highlight during cycle check
                er.line.setStrokeWidth(4);
                // Highlight nodes involved
                graphNodes.get(er.a).getCircle().setFill(Color.web("#E74C3C"));
                graphNodes.get(er.b).getCircle().setFill(Color.web("#C0392B"));
            }));
            
            if(dist.get(er.a)!=Double.POSITIVE_INFINITY && dist.get(er.a)+w<dist.get(er.b)){ 
                hasNegativeCycle[0] = true;
                tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                    er.line.setStroke(Color.web("#E74C3C"));
                    er.line.setStrokeWidth(6);
                    updateGraphExplanation("⚠ NEGATIVE CYCLE DETECTED! Edge " + er.a + "→" + er.b + " can still relax");
                }));
            } else {
                tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                    updateGraphExplanation("✓ Edge " + er.a + "→" + er.b + " cannot relax further");
                    er.line.setStroke(Color.web("#27AE60")); // Green for good edge
                    er.line.setStrokeWidth(3);
                }));
            }
            
            // Reset colors after processing each edge in negative cycle detection
            tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                if (!hasNegativeCycle[0]) {
                    // Only reset if no negative cycle was detected (keep red if cycle found)
                    er.line.setStroke(Color.WHITE);
                    er.line.setStrokeWidth(2);
                }
                graphNodes.get(er.a).getCircle().setFill(Color.web("#3498db"));
                graphNodes.get(er.b).getCircle().setFill(Color.web("#3498db"));
            }));
            
            if(!isDirected && dist.get(er.b)!=Double.POSITIVE_INFINITY && dist.get(er.b)+w<dist.get(er.a)){ 
                hasNegativeCycle[0] = true;
                tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                    er.line.setStroke(Color.web("#E74C3C"));
                    er.line.setStrokeWidth(6);
                    updateGraphExplanation("⚠ NEGATIVE CYCLE DETECTED! Edge " + er.b + "→" + er.a + " can still relax");
                }));
            }
        }
        
        // Final completion message
        tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
            if (hasNegativeCycle[0]) {
                updateGraphExplanation("❌ RESULT: Negative cycle found - no shortest paths exist");
            } else {
                updateGraphExplanation("✅ RESULT: Bellman-Ford completed - shortest paths found");
            }
        }));
        
        // Store timeline for potential stopping and play
        currentVisualizationTimeline = tl;
        enablePauseButton();
        tl.play();
    }
    
    private void visualizeFloydWarshall(){
        clearGraphExplanation();
        int n = graphNodes.size(); 
        if (n == 0) {
            updateGraphExplanation("FLOYD-WARSHALL ERROR: No nodes found!");
            return; 
        }
        
        if (!isWeighted) {
            updateGraphExplanation("FLOYD-WARSHALL ERROR: Algorithm requires weighted graph!");
            updateGraphExplanation("Enable 'Weighted' option and add edge weights to continue.");
            return;
        }
        
        List<String> ids = new ArrayList<>(graphNodes.keySet());
        double[][] dist = new double[n][n]; 
        
        // Initialize distance matrix
        updateGraphExplanation("FLOYD-WARSHALL INITIALIZATION:");
        updateGraphExplanation("Setting up distance matrix...");
        
        for(int i = 0; i < n; i++) {
            Arrays.fill(dist[i], Double.POSITIVE_INFINITY); 
        }
        for(int i = 0; i < n; i++) {
            dist[i][i] = 0;
        }
        
        // Set edge weights
        for(EdgeRecord er : edges){ 
            int ai = ids.indexOf(er.a);
            int bi = ids.indexOf(er.b); 
            dist[ai][bi] = Math.min(dist[ai][bi], er.weight); 
            if(!isDirected) {
                dist[bi][ai] = Math.min(dist[bi][ai], er.weight); 
            }
        }
        
        // Setup distance labels using Dijkstra's style
        resetFloydWarshallLabels();
        for(GraphNode gn : graphNodes.values()) {
            ensureFloydWarshallLabel(gn);
        }
        
        updateGraphExplanation("Matrix initialized with direct edge weights");
        updateGraphExplanation("Starting triple-nested loop algorithm...");
        
        Timeline tl = new Timeline(); 
        int stepDelay = 0;
        
        for(int k = 0; k < n; k++){ 
            String intermediateNode = ids.get(k);
            final int fk = k;
            
            // Highlight intermediate node
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(getAnimationDelay(stepDelay++ * 600)), e -> {
                updateGraphExplanation("PHASE " + (fk + 1) + ": Using node " + intermediateNode + " as intermediate");
                GraphNode intermediate = graphNodes.get(intermediateNode);
                if (intermediate != null) {
                    intermediate.getCircle().setFill(Color.web("#e74c3c")); // Red for intermediate
                }
            }));
            
            for(int i = 0; i < n; i++) {
                for(int j = 0; j < n; j++){ 
                    if(i != j && dist[i][k] + dist[k][j] < dist[i][j]){ 
                        double oldDist = dist[i][j];
                        dist[i][j] = dist[i][k] + dist[k][j]; 
                        final double newDist = dist[i][j];
                        final String fromNode = ids.get(i);
                        final String toNode = ids.get(j);
                        
                        tl.getKeyFrames().add(new KeyFrame(Duration.millis(getAnimationDelay(stepDelay++ * 600)), e -> {
                            updateGraphExplanation("Improved path " + fromNode + " → " + toNode + ": " + 
                                formatDist(oldDist) + " → " + formatDist(newDist));
                            
                            // Highlight source and destination
                            GraphNode source = graphNodes.get(fromNode);
                            GraphNode dest = graphNodes.get(toNode);
                            if (source != null) source.getCircle().setFill(Color.web("#3498db")); // Blue for source
                            if (dest != null) dest.getCircle().setFill(Color.web("#2ecc71")); // Green for destination
                            
                            // Update distance label
                            Label label = floydWarshallDistLabels.get(toNode);
                            if (label != null) {
                                label.setText(formatDist(newDist));
                            }
                        }));
                        
                        // Reset colors after a brief delay
                        tl.getKeyFrames().add(new KeyFrame(Duration.millis(getAnimationDelay(stepDelay * 600 + 300)), e -> {
                            resetNodeColors();
                        }));
                    } 
                } 
            }
        }
        
        // Final completion message
        tl.getKeyFrames().add(new KeyFrame(Duration.millis(getAnimationDelay(stepDelay * 600)), e -> {
            updateGraphExplanation("FLOYD-WARSHALL COMPLETE: All shortest paths computed!");
            resetNodeColors();
        }));
        
        // Store timeline for potential stopping and play
        currentVisualizationTimeline = tl;
        enablePauseButton();
        tl.play();
    }
    
    // Floyd-Warshall distance labels (same style as Dijkstra)
    private Map<String, Label> floydWarshallDistLabels = new HashMap<>();
    
    private void resetFloydWarshallLabels(){
        if(graphCanvas != null){
            for(Label label : floydWarshallDistLabels.values()) {
                graphCanvas.getChildren().remove(label);
            }
        }
        floydWarshallDistLabels.clear();
    }
    
    private void ensureFloydWarshallLabel(GraphNode node){
        if(node == null) return;
        if(floydWarshallDistLabels.containsKey(node.getId())) return;
        Circle c = node.getCircle(); 
        if(c == null || graphCanvas == null) return;
        
        Label box = new Label("∞");
        box.setAlignment(Pos.CENTER);
        box.setMinSize(30, 18);
        box.setPrefSize(32, 18);
        box.setStyle(
            "-fx-text-fill: white;" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: 650;" +
            "-fx-background-color: rgba(50, 205, 50, 0.85);" +  // Light green background
            "-fx-background-radius: 3;" +
            "-fx-padding: 2 5;" +
            "-fx-border-color: #32cd32;" +  // Light green border
            "-fx-border-width: 0.5;" +
            "-fx-border-radius: 3;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 6, 0.5, 1, 1);"
        );
        
        positionFloydWarshallLabel(node, box);
        graphCanvas.getChildren().add(box);
        floydWarshallDistLabels.put(node.getId(), box);
    }
    
    private void positionFloydWarshallLabel(GraphNode node, Label box){
        Circle c = node.getCircle(); 
        if(c == null) return;
        double r = c.getRadius();
        // Place to upper-right of the circle (same as Dijkstra)
        box.setLayoutX(c.getCenterX() + r + 4);
        box.setLayoutY(c.getCenterY() - r - 4);
    }
    
    private boolean isGraphConnected() {
        if (graphNodes.isEmpty()) return false;
        if (graphNodes.size() == 1) return true;
        
        // Use DFS to check if all nodes are reachable from any starting node
        String startNodeId = graphNodes.keySet().iterator().next();
        Set<String> visited = new HashSet<>();
        dfsConnectivityCheck(startNodeId, visited);
        
        // Graph is connected if we visited all nodes
        return visited.size() == graphNodes.size();
    }
    
    private void dfsConnectivityCheck(String nodeId, Set<String> visited) {
        visited.add(nodeId);
        
        // Visit all neighbors through edges
        for (EdgeRecord edge : edges) {
            String neighborId = null;
            
            // For undirected graphs, check both directions
            if (edge.a.equals(nodeId)) {
                neighborId = edge.b;
            } else if (edge.b.equals(nodeId)) {
                neighborId = edge.a;
            }
            
            // If we found an unvisited neighbor, recursively visit it
            if (neighborId != null && !visited.contains(neighborId)) {
                dfsConnectivityCheck(neighborId, visited);
            }
        }
    }
    
    private void visualizePrim(){ 
        if(isDirected) {
            showErrorInExplanation("PRIM'S ALGORITHM ERROR!");
            showErrorInExplanation("This algorithm is NOT APPLICABLE for directed graphs!");
            showErrorInExplanation("");
            showErrorInExplanation("WHY THIS ERROR OCCURRED:");
            showErrorInExplanation("   • Prim's algorithm finds Minimum Spanning Tree (MST)");
            showErrorInExplanation("   • MST concept only applies to undirected graphs");
            showErrorInExplanation("   • Directed graphs don't have traditional spanning trees");
            showErrorInExplanation("");
            showErrorInExplanation("HOW TO FIX:");
            showErrorInExplanation("   1. Disable 'Directed' option in graph settings");
            showErrorInExplanation("   2. Make sure graph is undirected");
            showErrorInExplanation("   3. Then try running Prim's algorithm again");
            return;
        }
        
        if (!isWeighted) {
            showErrorInExplanation("PRIM'S ALGORITHM ERROR!");
            showErrorInExplanation("This algorithm is NOT APPLICABLE for unweighted graphs!");
            showErrorInExplanation("");
            showErrorInExplanation("WHY THIS ERROR OCCURRED:");
            showErrorInExplanation("   • Prim's algorithm finds Minimum Spanning Tree based on edge weights");
            showErrorInExplanation("   • Without weights, all edges would be considered equal");
            showErrorInExplanation("   • The concept of 'minimum' requires weighted edges");
            showErrorInExplanation("");
            showErrorInExplanation("HOW TO FIX:");
            showErrorInExplanation("   1. Enable 'Weighted' option in graph settings");
            showErrorInExplanation("   2. Add weights to your edges");
            showErrorInExplanation("   3. Then try running Prim's algorithm again");
            return;
        }
        
        if(graphNodes.isEmpty()) {
            showErrorInExplanation("PRIM'S ALGORITHM ERROR!");
            showErrorInExplanation("This algorithm is NOT APPLICABLE - No nodes found!");
            showErrorInExplanation("");
            showErrorInExplanation("WHY THIS ERROR OCCURRED:");
            showErrorInExplanation("   • Prim's algorithm needs nodes to create spanning tree");
            showErrorInExplanation("   • Cannot build MST without graph vertices");
            showErrorInExplanation("");
            showErrorInExplanation("HOW TO FIX:");
            showErrorInExplanation("   1. Add nodes to your graph");
            showErrorInExplanation("   2. Connect them with weighted edges");
            showErrorInExplanation("   3. Then try running Prim's algorithm again");
            return;
        }
        
        if (edges.isEmpty()) {
            showErrorInExplanation("PRIM'S ALGORITHM ERROR!");
            showErrorInExplanation("This algorithm is NOT APPLICABLE - No edges found!");
            showErrorInExplanation("");
            showErrorInExplanation("WHY THIS ERROR OCCURRED:");
            showErrorInExplanation("   • Prim's algorithm needs edges to create spanning tree");
            showErrorInExplanation("   • Cannot connect nodes without edges");
            showErrorInExplanation("");
            showErrorInExplanation("HOW TO FIX:");
            showErrorInExplanation("   1. Add weighted edges between nodes");
            showErrorInExplanation("   2. Make sure graph is connected");
            showErrorInExplanation("   3. Then try running Prim's algorithm again");
            return;
        }
        
        // Check if graph is connected (required for MST algorithms)
        if (!isGraphConnected()) {
            showErrorInExplanation("PRIM'S ALGORITHM ERROR!");
            showErrorInExplanation("This algorithm is NOT APPLICABLE - Graph is NOT CONNECTED!");
            showErrorInExplanation("");
            showErrorInExplanation("WHY THIS ERROR OCCURRED:");
            showErrorInExplanation("   • Prim's algorithm requires a connected graph");
            showErrorInExplanation("   • Some nodes are unreachable from other nodes");
            showErrorInExplanation("   • Cannot create spanning tree without connectivity");
            showErrorInExplanation("");
            showErrorInExplanation("CONNECTIVITY REQUIREMENT:");
            showErrorInExplanation("   • Every node must be reachable from every other node");
            showErrorInExplanation("   • Graph must have no isolated components");
            showErrorInExplanation("   • All nodes should be in one connected component");
            showErrorInExplanation("");
            showErrorInExplanation("HOW TO FIX:");
            showErrorInExplanation("   1. Add edges to connect all isolated nodes");
            showErrorInExplanation("   2. Ensure every node has a path to every other node");
            showErrorInExplanation("   3. Remove any isolated components");
            showErrorInExplanation("   4. Then try running Prim's algorithm again");
            return;
        }
        
        clearGraphExplanation();
        
        // Algorithm is applicable - proceed with educational Prim's implementation
        GraphNode start = bfsSource != null ? bfsSource : graphNodes.values().iterator().next();
        updateGraphExplanation("PRIM'S MINIMUM SPANNING TREE ALGORITHM");
        updateGraphExplanation("======================================");
        updateGraphExplanation("Starting from node: " + start.getId());
        updateGraphExplanation("Goal: Find minimum weight tree connecting all nodes");
        updateGraphExplanation("");
        updateGraphExplanation("ALGORITHM CONCEPT:");
        updateGraphExplanation("   • Start with any node (we chose " + start.getId() + ")");
        updateGraphExplanation("   • Repeatedly add the cheapest edge that connects");
        updateGraphExplanation("     a node IN the tree to a node NOT in the tree");
        updateGraphExplanation("   • Continue until all nodes are connected");
        updateGraphExplanation("");
        
        Set<String> inMST = new HashSet<>();
        Set<EdgeRecord> mstEdges = new HashSet<>();
        Timeline tl = new Timeline();
        int step = 0;
        
        // Create total cost label for smooth animation
        Label totalCostLabel = new Label("MST Cost: 0.0");
        totalCostLabel.setStyle(
            "-fx-background-color: rgba(0, 0, 0, 0.8); -fx-text-fill: #90EE90; " +
            "-fx-font-weight: bold; -fx-font-size: 16px; -fx-background-radius: 10px; " +
            "-fx-padding: 8px 12px; -fx-border-color: #90EE90; -fx-border-width: 2px; " +
            "-fx-border-radius: 10px;"
        );
        totalCostLabel.setLayoutX(graphCanvas.getWidth() - 180);
        totalCostLabel.setLayoutY(20); // Top right position
        totalCostLabel.setMouseTransparent(true);
        graphCanvas.getChildren().add(totalCostLabel);
        
        // Step 1: Initialize - Add starting node to MST
        inMST.add(start.getId());
        tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
            start.getCircle().setFill(Color.web("#9932cc")); // Purple for MST nodes
            start.getCircle().setStroke(Color.web("#8b008b"));
            start.getCircle().setStrokeWidth(4);
            
            updateGraphExplanation("INITIALIZATION COMPLETE");
            updateGraphExplanation("   Added node " + start.getId() + " to MST");
            updateGraphExplanation("   Nodes in MST: {" + start.getId() + "}");
            updateGraphExplanation("   Total MST cost: 0.0");
            updateGraphExplanation("");
        }));
        
        double totalCost = 0.0;
        int iteration = 1;
        
        // Main Prim's algorithm loop
        while(inMST.size() < graphNodes.size()) {
            EdgeRecord bestEdge = null;
            double bestWeight = Double.POSITIVE_INFINITY;
            String bestFromNode = null;
            String bestToNode = null;
            
            final int currentIteration = iteration;
            tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                updateGraphExplanation("ITERATION " + currentIteration + " - FINDING CHEAPEST CUT EDGE");
                updateGraphExplanation("   Looking for minimum weight edge connecting:");
                updateGraphExplanation("   • A node IN the MST: " + String.join(", ", inMST));
                updateGraphExplanation("   • A node NOT in MST");
                updateGraphExplanation("");
            }));
            
            // Examine all edges to find the minimum cut edge
            List<EdgeRecord> candidateEdges = new ArrayList<>();
            for(EdgeRecord er : edges) {
                boolean aInMST = inMST.contains(er.a);
                boolean bInMST = inMST.contains(er.b);
                
                // This edge crosses the cut (one node in MST, one not)
                if(aInMST ^ bInMST) {
                    candidateEdges.add(er);
                    if(er.weight < bestWeight) {
                        bestWeight = er.weight;
                        bestEdge = er;
                        bestFromNode = aInMST ? er.a : er.b; // Node in MST
                        bestToNode = aInMST ? er.b : er.a;   // Node not in MST
                    }
                }
            }
            
            if(bestEdge == null) {
                tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                    updateGraphExplanation("GRAPH IS NOT CONNECTED!");
                    updateGraphExplanation("   No edges found connecting MST to remaining nodes");
                    updateGraphExplanation("   Cannot complete Minimum Spanning Tree");
                }));
                break;
            }
            
            // Show all candidate edges being examined
            for(int i = 0; i < candidateEdges.size(); i++) {
                final EdgeRecord candidate = candidateEdges.get(i);
                final int candidateNum = i + 1;
                final int totalCandidates = candidateEdges.size();
                tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                    candidate.line.setStroke(Color.web("#ffa500")); // Orange for examination
                    candidate.line.setStrokeWidth(3);
                    String fromNode = inMST.contains(candidate.a) ? candidate.a : candidate.b;
                    String toNode = inMST.contains(candidate.a) ? candidate.b : candidate.a;
                    updateGraphExplanation("   🔎 Examining candidate " + candidateNum + "/" + totalCandidates + ": " + 
                                          fromNode + "→" + toNode + " (weight: " + String.format("%.1f", candidate.weight) + ")");
                }));
                
                // Reset color after examination
                tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                    candidate.line.setStroke(Color.WHITE);
                    candidate.line.setStrokeWidth(2);
                }));
            }
            
            // Highlight and add the best edge
            final EdgeRecord finalBestEdge = bestEdge;
            final String finalBestFromNode = bestFromNode;
            final String finalBestToNode = bestToNode;
            final double finalBestWeight = bestWeight;
            
            totalCost += bestWeight;
            final double finalTotalCost = totalCost;
            
            tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                // Highlight the chosen edge
                finalBestEdge.line.setStroke(Color.web("#90EE90")); // Light green for MST edges (same as Kruskal)
                finalBestEdge.line.setStrokeWidth(5);
                
                // Add new node to MST and color it
                GraphNode newNode = graphNodes.get(finalBestToNode);
                newNode.getCircle().setFill(Color.web("#9932cc")); // Purple for MST nodes
                newNode.getCircle().setStroke(Color.web("#8b008b"));
                newNode.getCircle().setStrokeWidth(4);
                
                // Smooth total cost animation
                Timeline costAnimation = new Timeline();
                double startCost = finalTotalCost - finalBestWeight;
                for(int i = 0; i <= 20; i++) {
                    final double animatedCost = startCost + (finalBestWeight * i / 20.0);
                    costAnimation.getKeyFrames().add(new KeyFrame(
                        javafx.util.Duration.millis(i * 30), // 30ms per frame for smooth animation
                        ev -> totalCostLabel.setText(String.format("MST Cost: %.1f", animatedCost))
                    ));
                }
                costAnimation.play();
                
                updateGraphExplanation("   BEST EDGE FOUND: " + finalBestFromNode + "→" + finalBestToNode + 
                                      " (weight: " + String.format("%.1f", finalBestWeight) + ")");
                updateGraphExplanation("   Added node " + finalBestToNode + " to MST");
                updateGraphExplanation("   Edge cost: " + String.format("%.1f", finalBestWeight));
                updateGraphExplanation("   Total MST cost so far: " + String.format("%.1f", finalTotalCost));
                
                // Show current MST composition
                Set<String> currentMST = new HashSet<>(inMST);
                currentMST.add(finalBestToNode);
                updateGraphExplanation("   Nodes in MST: {" + String.join(", ", currentMST) + "}");
                updateGraphExplanation("");
            }));
            
            inMST.add(bestToNode);
            mstEdges.add(bestEdge);
            iteration++;
        }
        
        // Final completion message and cleanup
        final double finalTotalCostFinal = totalCost;
        final int finalMSTSize = mstEdges.size();
        final Set<EdgeRecord> finalMSTEdges = new HashSet<>(mstEdges);
        tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
            // Remove edges that are NOT in the MST
            List<EdgeRecord> edgesToRemove = new ArrayList<>();
            for(EdgeRecord edge : edges) {
                if(!finalMSTEdges.contains(edge)) {
                    edgesToRemove.add(edge);
                    // Fade out non-MST edges
                    edge.line.setStroke(Color.TRANSPARENT);
                    // Also remove the weight label for non-MST edges
                    if(edge.weightLabel != null) {
                        edge.weightLabel.setVisible(false);
                    }
                }
            }
            
            updateGraphExplanation("CLEANUP: Removed " + edgesToRemove.size() + " edges not in MST");
            updateGraphExplanation("======================================");
            updateGraphExplanation("PRIM'S ALGORITHM COMPLETED SUCCESSFULLY!");
            updateGraphExplanation("Minimum Spanning Tree constructed!");
            updateGraphExplanation("");
            updateGraphExplanation("FINAL MST STATISTICS:");
            updateGraphExplanation("   Total nodes: " + graphNodes.size());
            updateGraphExplanation("   MST edges: " + finalMSTSize);
            updateGraphExplanation("   Total MST cost: " + String.format("%.1f", finalTotalCostFinal));
            updateGraphExplanation("");
            updateGraphExplanation("MST EDGES SELECTED:");
            int edgeNum = 1;
            for(EdgeRecord mstEdge : finalMSTEdges) {
                updateGraphExplanation("   " + edgeNum + ". " + mstEdge.a + "↔" + mstEdge.b + " (weight: " + String.format("%.1f", mstEdge.weight) + ")");
                edgeNum++;
            }
            updateGraphExplanation("");
            updateGraphExplanation("PROPERTIES OF THIS MST:");
            updateGraphExplanation("   Connects all " + graphNodes.size() + " nodes");
            updateGraphExplanation("   Uses exactly " + (graphNodes.size()-1) + " edges");
            updateGraphExplanation("   Has minimum possible total weight");
            updateGraphExplanation("   Contains no cycles");
            updateGraphExplanation("======================================");
        }));
        
        // Store timeline for potential stopping and play
        currentVisualizationTimeline = tl;
        enablePauseButton();
        tl.play(); 
    }
    private void visualizeKruskal(){ 
        if(isDirected) {
            showErrorInExplanation("KRUSKAL'S ALGORITHM ERROR!");
            showErrorInExplanation("This algorithm is NOT APPLICABLE for directed graphs!");
            showErrorInExplanation("");
            showErrorInExplanation("WHY THIS ERROR OCCURRED:");
            showErrorInExplanation("   • Kruskal's algorithm finds Minimum Spanning Tree (MST)");
            showErrorInExplanation("   • MST concept only applies to undirected graphs");
            showErrorInExplanation("   • Directed graphs don't have traditional spanning trees");
            showErrorInExplanation("");
            showErrorInExplanation("HOW TO FIX:");
            showErrorInExplanation("   1. Disable 'Directed' option in graph settings");
            showErrorInExplanation("   2. Make sure graph is undirected");
            showErrorInExplanation("   3. Then try running Kruskal's algorithm again");
            return;
        }
        
        if (!isWeighted) {
            showErrorInExplanation("🚨 KRUSKAL'S ALGORITHM ERROR!");
            showErrorInExplanation("❌ This algorithm is NOT APPLICABLE for unweighted graphs!");
            showErrorInExplanation("");
            showErrorInExplanation("📋 WHY THIS ERROR OCCURRED:");
            showErrorInExplanation("   • Kruskal's algorithm finds Minimum Spanning Tree based on edge weights");
            showErrorInExplanation("   • Without weights, all edges would be considered equal");
            showErrorInExplanation("   • The concept of 'minimum' requires weighted edges");
            showErrorInExplanation("");
            showErrorInExplanation("🔧 HOW TO FIX:");
            showErrorInExplanation("   1. Enable 'Weighted' option in graph settings");
            showErrorInExplanation("   2. Add weights to your edges");
            showErrorInExplanation("   3. Then try running Kruskal's algorithm again");
            return;
        }
        
        if(graphNodes.isEmpty()) {
            showErrorInExplanation("🚨 KRUSKAL'S ALGORITHM ERROR!");
            showErrorInExplanation("❌ This algorithm is NOT APPLICABLE - No nodes found!");
            showErrorInExplanation("");
            showErrorInExplanation("📋 WHY THIS ERROR OCCURRED:");
            showErrorInExplanation("   • Kruskal's algorithm needs nodes to create spanning tree");
            showErrorInExplanation("   • Cannot build MST without graph vertices");
            showErrorInExplanation("");
            showErrorInExplanation("🔧 HOW TO FIX:");
            showErrorInExplanation("   1. Add nodes to your graph");
            showErrorInExplanation("   2. Connect them with weighted edges");
            showErrorInExplanation("   3. Then try running Kruskal's algorithm again");
            return;
        }
        
        if (edges.isEmpty()) {
            showErrorInExplanation("KRUSKAL'S ALGORITHM ERROR!");
            showErrorInExplanation("This algorithm is NOT APPLICABLE - No edges found!");
            showErrorInExplanation("");
            showErrorInExplanation("WHY THIS ERROR OCCURRED:");
            showErrorInExplanation("   • Kruskal's algorithm needs edges to create spanning tree");
            showErrorInExplanation("   • Cannot connect nodes without edges");
            showErrorInExplanation("");
            showErrorInExplanation("HOW TO FIX:");
            showErrorInExplanation("   1. Add weighted edges between nodes");
            showErrorInExplanation("   2. Make sure graph is connected");
            showErrorInExplanation("   3. Then try running Kruskal's algorithm again");
            return;
        }
        
        // Check if graph is connected (required for MST algorithms)
        if (!isGraphConnected()) {
            showErrorInExplanation("KRUSKAL'S ALGORITHM ERROR!");
            showErrorInExplanation("This algorithm is NOT APPLICABLE - Graph is NOT CONNECTED!");
            showErrorInExplanation("");
            showErrorInExplanation("WHY THIS ERROR OCCURRED:");
            showErrorInExplanation("   • Kruskal's algorithm requires a connected graph");
            showErrorInExplanation("   • Some nodes are unreachable from other nodes");
            showErrorInExplanation("   • Cannot create spanning tree without connectivity");
            showErrorInExplanation("");
            showErrorInExplanation("CONNECTIVITY REQUIREMENT:");
            showErrorInExplanation("   • Every node must be reachable from every other node");
            showErrorInExplanation("   • Graph must have no isolated components");
            showErrorInExplanation("   • All nodes should be in one connected component");
            showErrorInExplanation("");
            showErrorInExplanation("HOW TO FIX:");
            showErrorInExplanation("   1. Add edges to connect all isolated nodes");
            showErrorInExplanation("   2. Ensure every node has a path to every other node");
            showErrorInExplanation("   3. Remove any isolated components");
            showErrorInExplanation("   4. Then try running Kruskal's algorithm again");
            return;
        }
        
        clearGraphExplanation();
        
        // Algorithm is applicable - proceed with educational Kruskal's implementation
        updateGraphExplanation("KRUSKAL'S MINIMUM SPANNING TREE ALGORITHM");
        updateGraphExplanation("======================================");
        updateGraphExplanation("Goal: Find minimum weight tree connecting all nodes");
        updateGraphExplanation("");
        updateGraphExplanation("ALGORITHM CONCEPT:");
        updateGraphExplanation("   • Sort ALL edges by weight (ascending order)");
        updateGraphExplanation("   • Consider edges one by one from cheapest to most expensive");
        updateGraphExplanation("   • Add edge to MST if it doesn't create a cycle");
        updateGraphExplanation("   • Use Union-Find to detect cycles efficiently");
        updateGraphExplanation("");
        
        // Sort edges by weight
        List<EdgeRecord> sortedEdges = new ArrayList<>(edges);
        sortedEdges.sort(Comparator.comparingDouble(e -> e.weight));
        
        // Union-Find data structure
        Map<String, String> parent = new HashMap<>();
        Map<String, Integer> rank = new HashMap<>();
        for(String id : graphNodes.keySet()) {
            parent.put(id, id);
            rank.put(id, 0);
        }
        
        // Find function with path compression
        java.util.function.Function<String, String> find = new java.util.function.Function<String, String>() {
            public String apply(String x) {
                if (!parent.get(x).equals(x)) {
                    parent.put(x, apply(parent.get(x))); // Path compression
                }
                return parent.get(x);
            }
        };
        
        // Union function with union by rank
        java.util.function.BiConsumer<String, String> union = (x, y) -> {
            String rootX = find.apply(x);
            String rootY = find.apply(y);
            if (!rootX.equals(rootY)) {
                int rankX = rank.get(rootX);
                int rankY = rank.get(rootY);
                if (rankX < rankY) {
                    parent.put(rootX, rootY);
                } else if (rankX > rankY) {
                    parent.put(rootY, rootX);
                } else {
                    parent.put(rootY, rootX);
                    rank.put(rootX, rankX + 1);
                }
            }
        };
        
        Set<EdgeRecord> mstEdges = new HashSet<>();
        Timeline tl = new Timeline();
        int step = 0;
        double totalCost = 0.0;
        
        // Create total cost label for smooth animation
        Label totalCostLabel = new Label("MST Cost: 0.0");
        totalCostLabel.setStyle(
            "-fx-background-color: rgba(0, 0, 0, 0.8); -fx-text-fill: #90EE90; " +
            "-fx-font-weight: bold; -fx-font-size: 16px; -fx-background-radius: 10px; " +
            "-fx-padding: 8px 12px; -fx-border-color: #90EE90; -fx-border-width: 2px; " +
            "-fx-border-radius: 10px;"
        );
        totalCostLabel.setLayoutX(graphCanvas.getWidth() - 180);
        totalCostLabel.setLayoutY(20); // Top right position
        totalCostLabel.setMouseTransparent(true);
        graphCanvas.getChildren().add(totalCostLabel);
        
        // Step 1: Show sorted edges with more animation
        tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
            updateGraphExplanation("STEP 1: SORT ALL EDGES BY WEIGHT");
            updateGraphExplanation("   📋 Kruskal's algorithm processes edges in weight order");
            updateGraphExplanation("");
        }));
        
        // Highlight each edge in sorted order
        for(int i = 0; i < sortedEdges.size(); i++) {
            final EdgeRecord edge = sortedEdges.get(i);
            final int edgeNum = i + 1;
            tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                edge.line.setStroke(Color.web("#FFD700")); // Gold color for sorted list
                edge.line.setStrokeWidth(3);
                updateGraphExplanation("   " + edgeNum + ". " + edge.a + "↔" + edge.b + " (weight: " + String.format("%.1f", edge.weight) + ")");
            }));
            
            // Reset color after showing
            tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                edge.line.setStroke(Color.WHITE);
                edge.line.setStrokeWidth(2);
            }));
        }
        
        tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
            updateGraphExplanation("");
            updateGraphExplanation("🔄 STEP 2: PROCESS EDGES ONE BY ONE");
            updateGraphExplanation("   Each node starts disconnected (distance = ∞)");
            updateGraphExplanation("   Goal: Connect all nodes with minimum total cost");
            updateGraphExplanation("");
        }));
        
        // Process each edge in sorted order
        int edgeNumber = 1;
        for(EdgeRecord currentEdge : sortedEdges) {
            final int finalEdgeNumber = edgeNumber;
            final int totalEdges = sortedEdges.size();
            
            // Highlight current edge being considered with pulsing animation
            tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                currentEdge.line.setStroke(Color.web("#FF6B6B")); // Bright red for consideration
                currentEdge.line.setStrokeWidth(4);
                currentEdge.line.getStrokeDashArray().setAll(5.0, 5.0); // Dashed line for emphasis
                updateGraphExplanation("EXAMINING EDGE " + finalEdgeNumber + "/" + totalEdges + ": " + 
                                      currentEdge.a + "↔" + currentEdge.b + " (weight: " + String.format("%.1f", currentEdge.weight) + ")");
                updateGraphExplanation("   ❓ Checking if nodes are in different components...");
            }));
            
            // Check if adding this edge would create a cycle
            String rootA = find.apply(currentEdge.a);
            String rootB = find.apply(currentEdge.b);
            
            if (!rootA.equals(rootB)) {
                // No cycle - add edge to MST
                mstEdges.add(currentEdge);
                totalCost += currentEdge.weight;
                final double currentTotalCost = totalCost;
                final int finalMSTSize = mstEdges.size();
                
                tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                    // Accept the edge with bright green
                    currentEdge.line.setStroke(Color.web("#90EE90")); // Light green for MST edges
                    currentEdge.line.setStrokeWidth(5);
                    currentEdge.line.getStrokeDashArray().clear(); // Remove dashes
                    
                    // Smooth total cost animation
                    Timeline costAnimation = new Timeline();
                    double startCost = currentTotalCost - currentEdge.weight;
                    for(int i = 0; i <= 20; i++) {
                        final double animatedCost = startCost + (currentEdge.weight * i / 20.0);
                        costAnimation.getKeyFrames().add(new KeyFrame(
                            javafx.util.Duration.millis(i * 30), // 30ms per frame for smooth animation
                            ev -> totalCostLabel.setText(String.format("MST Cost: %.1f", animatedCost))
                        ));
                    }
                    costAnimation.play();
                    
                    updateGraphExplanation("EDGE ACCEPTED! " + currentEdge.a + "↔" + currentEdge.b + " (weight: " + String.format("%.1f", currentEdge.weight) + ")");
                    updateGraphExplanation("   Nodes " + currentEdge.a + " and " + currentEdge.b + " are now connected in MST");
                    updateGraphExplanation("   Edge cost: " + String.format("%.1f", currentEdge.weight));
                    updateGraphExplanation("   Total MST cost so far: " + String.format("%.1f", currentTotalCost));
                    updateGraphExplanation("   📊 MST edges: " + finalMSTSize + "/" + (graphNodes.size()-1));
                }));
                
                // Update Union-Find
                union.accept(currentEdge.a, currentEdge.b);
                
                // Add completion check
                tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                    if(finalMSTSize == graphNodes.size() - 1) {
                        updateGraphExplanation("");
                        updateGraphExplanation("MST COMPLETE!");
                        updateGraphExplanation("   💎 Final minimum spanning tree cost: " + String.format("%.1f", currentTotalCost));
                        updateGraphExplanation("   🔗 All " + graphNodes.size() + " nodes are now connected!");
                        updateGraphExplanation("   ✨ Used " + finalMSTSize + " edges to connect the graph");
                    } else {
                        updateGraphExplanation("");
                    }
                }));
                
            } else {
                // Cycle detected - reject edge with more dramatic animation
                tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                    currentEdge.line.setStroke(Color.web("#FF4444")); // Darker red for rejected
                    currentEdge.line.setStrokeWidth(3);
                    currentEdge.line.getStrokeDashArray().setAll(10.0, 10.0); // Larger dashes for rejected
                    
                    updateGraphExplanation("❌ EDGE REJECTED! " + currentEdge.a + "↔" + currentEdge.b + " (weight: " + String.format("%.1f", currentEdge.weight) + ")");
                    updateGraphExplanation("   🔄 Both nodes are already in the same component");
                    updateGraphExplanation("   ⚠️ Adding this edge would create a CYCLE");
                    updateGraphExplanation("   ⏭️ Skipping and continuing with next edge...");
                    updateGraphExplanation("");
                }));
                
                // Fade out rejected edge
                tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
                    currentEdge.line.setOpacity(0.3);
                    currentEdge.line.setStroke(Color.GRAY);
                    currentEdge.line.setStrokeWidth(1);
                    currentEdge.line.getStrokeDashArray().clear();
                }));
            }
            
            edgeNumber++;
            
            // Check if MST is complete
            if(mstEdges.size() == graphNodes.size() - 1) {
                break; // MST is complete
            }
        }
        
        // Final completion message and cleanup
        final double finalTotalCost = totalCost;
        final int finalMSTSize = mstEdges.size();
        final Set<EdgeRecord> finalMSTEdges = new HashSet<>(mstEdges);
        tl.getKeyFrames().add(new KeyFrame(javafx.util.Duration.millis(getAnimationDelay(step++*600)), e-> {
            // Remove edges that are NOT in the MST (make them transparent)
            for(EdgeRecord edge : edges) {
                if(!finalMSTEdges.contains(edge)) {
                    edge.line.setStroke(Color.TRANSPARENT);
                    if(edge.weightLabel != null) {
                        edge.weightLabel.setVisible(false);
                    }
                }
            }
            
            updateGraphExplanation("CLEANUP: Removed " + (edges.size() - finalMSTSize) + " edges not in MST");
            updateGraphExplanation("======================================");
            updateGraphExplanation("KRUSKAL'S ALGORITHM COMPLETED SUCCESSFULLY!");
            updateGraphExplanation("Minimum Spanning Tree constructed using Union-Find!");
            updateGraphExplanation("");
            updateGraphExplanation("FINAL MST STATISTICS:");
            updateGraphExplanation("   Total nodes: " + graphNodes.size());
            updateGraphExplanation("   MST edges: " + finalMSTSize);
            updateGraphExplanation("   Total MST cost: " + String.format("%.1f", finalTotalCost));
            updateGraphExplanation("");
            updateGraphExplanation("MST EDGES SELECTED (in order processed):");
            int edgeNum = 1;
            for(EdgeRecord mstEdge : finalMSTEdges) {
                updateGraphExplanation("   " + edgeNum + ". " + mstEdge.a + "↔" + mstEdge.b + " (weight: " + String.format("%.1f", mstEdge.weight) + ")");
                edgeNum++;
            }
            updateGraphExplanation("");
            updateGraphExplanation("KRUSKAL vs PRIM COMPARISON:");
            updateGraphExplanation("   Kruskal: Processes edges globally by weight");
            updateGraphExplanation("   Prim: Grows tree locally from a starting node");
            updateGraphExplanation("   Both find the same MST (but in different ways)");
            updateGraphExplanation("   Kruskal better for sparse graphs, Prim for dense");
            updateGraphExplanation("======================================");
        }));
        
        // Store timeline for potential stopping and play
        currentVisualizationTimeline = tl;
        enablePauseButton();
        tl.play(); 
    }
    
    private void checkConfigurationComplete() {
        // Check if both graph type (directed/undirected) and weight type (weighted/unweighted) are selected
        boolean directionSelected = (directedRadio != null && directedRadio.isSelected()) || 
                                  (undirectedRadio != null && undirectedRadio.isSelected());
        boolean weightSelected = (weightedRadio != null && weightedRadio.isSelected()) || 
                               (unweightedRadio != null && unweightedRadio.isSelected());
        
        isConfigurationComplete = directionSelected && weightSelected;
        
        // Enable ADD EDGE button only when configuration is complete
        if (addEdgeButtonRef != null) {
            if (!isConfigurationComplete) ButtonManager.setCompactButtonDisabled(addEdgeButtonRef); 
            else ButtonManager.setCompactButtonEnabled(addEdgeButtonRef);
        }
    }
    
    private void validateGraphForAlgorithms() {
        if (graphAlgoChecks==null || graphAlgoChecks.isEmpty()) return;
        List<String> issues = new ArrayList<>();
        boolean anyNegative = edges.stream().anyMatch(e -> e.weightLabel!=null && parseWeight(e.weightLabel.getText()) < 0);
        boolean weightedNeeded = isSelected("Dijkstra")||isSelected("Bellman-Ford")||isSelected("Floyd-Warshall")||isSelected("Prim")||isSelected("Kruskal");
        if (isSelected("Prim") && isDirected) issues.add("Prim needs UNDIRECTED");
        if (isSelected("Kruskal") && isDirected) issues.add("Kruskal needs UNDIRECTED");
        if (isSelected("Dijkstra") && anyNegative) issues.add("No negative weights for Dijkstra");
        if ((isSelected("Prim")||isSelected("Kruskal")||isSelected("Dijkstra")||isSelected("Bellman-Ford")||isSelected("Floyd-Warshall")) && !isWeighted) issues.add("Enable weights");
        if (weightedNeeded && edges.isEmpty()) issues.add("Add edges");
        if (graphNodes.isEmpty()) issues.add("Add nodes");
        if (!issues.isEmpty() && common.sortingStatusLabel!=null) common.sortingStatusLabel.setText(String.join(" | ", issues));
    }
    
    private double parseWeight(String t){ 
    	try { 
    		return Double.parseDouble(t.trim()); 
    	} 
    	catch(Exception ex){ 
    		return 0; 
    	} 
    }
    
    private Pane createGraphCanvas() {
        graphCanvas = new Pane();
        graphCanvas.setPrefSize(900, 520);
        graphCanvas.setMinSize(700, 420);
        graphCanvas.setStyle("-fx-background-color: rgba(15, 15, 18, 0.95); " +
                "-fx-background-radius: 20; " +
                "-fx-border-color: linear-gradient(to bottom right,#00ffff55,#4ecdc455); " +
                "-fx-border-width: 2; -fx-border-radius: 20; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 25,0,0,8);");
    
	    // Add a subtle visual boundary to show the safe zone for node creation
	    Rectangle safeZone = new Rectangle(
	        BOUNDARY_MARGIN, 
	        BOUNDARY_MARGIN, 
	        900 - 2 * BOUNDARY_MARGIN, 
	        520 - 2 * BOUNDARY_MARGIN
	    );
	    safeZone.setFill(Color.TRANSPARENT);
	    safeZone.setStroke(Color.web("#00ffff", 0.15)); // Very subtle cyan border
	    safeZone.setStrokeWidth(1);
	    safeZone.getStrokeDashArray().addAll(5.0, 5.0); // Dashed line
	    safeZone.setMouseTransparent(true); // Don't interfere with clicks
	    safeZone.setArcWidth(15); // Rounded corners to match canvas
	    safeZone.setArcHeight(15);
	    graphCanvas.getChildren().add(safeZone);
	    
	    // Clip to keep edges fully inside rounded area
	    Rectangle clip = new Rectangle(900, 520);
	    clip.setArcWidth(40);
	    clip.setArcHeight(40);
	    graphCanvas.setClip(clip);
	    // Add node only if user has armed add mode by pressing ADD NODE
	    graphCanvas.setOnMouseClicked(e -> {
	            if (!addNodeArmed) return;
	            // Use the same boundary margin as defined above
	            double x = common.clamp(e.getX(), BOUNDARY_MARGIN, graphCanvas.getWidth() - BOUNDARY_MARGIN);
	            double y = common.clamp(e.getY(), BOUNDARY_MARGIN, graphCanvas.getHeight() - BOUNDARY_MARGIN);
	            boolean tooClose = graphCanvas.getChildren().stream().anyMatch(n -> n instanceof Circle c &&
	                    Math.hypot(c.getCenterX() - x, c.getCenterY() - y) < (NODE_RADIUS * 2 + 6));
	            if (!tooClose) {
	                String id = String.valueOf(nextNodeId++);
	                addNodeAtPosition(id, x, y);
	            }
	        });
        return graphCanvas;
    }
    
    private void addNodeAtPosition(String nodeId, double x, double y) {
        if (graphNodes.containsKey(nodeId)) return;

	    GraphNode node = new GraphNode(nodeId, x, y);
	    graphNodes.put(nodeId, node);
	    nodeOrder.add(nodeId);
	
	    Circle circle = new Circle(x, y, NODE_RADIUS);
	    // Darker inner color so bright label contrasts strongly
	    circle.setFill(Color.web("#0097a7", 0.9));
	    circle.setStroke(Color.web("#00ffff"));
	    circle.setStrokeWidth(2.0);
	    circle.setEffect(new DropShadow(16, Color.web("#00ffff55")));
	    
	    // CRUCIAL: Set the circle on the GraphNode object
	    node.setCircle(circle);
	    nodeCircles.put(nodeId, circle);
	    // High-contrast numbering using Text with stroke for guaranteed visibility
	    javafx.scene.text.Text numText = new javafx.scene.text.Text(nodeId);
	    numText.setFill(Color.WHITE);
	    numText.setStroke(Color.web("#002b36"));
	    numText.setStrokeWidth(1.5);
	    numText.setStyle("-fx-font-size: 18px; -fx-font-weight: 800;");
	    DropShadow txtShadow = new DropShadow();
	    txtShadow.setColor(Color.web("#000000", 0.9));
	    txtShadow.setRadius(4);
	    txtShadow.setSpread(0.5);
	    numText.setEffect(txtShadow);
	    numText.setManaged(false);
	    numText.setMouseTransparent(true);
	    // Center text relative to circle; recompute when its bounds change
	    Runnable centerText = () -> {
	        double w = numText.getLayoutBounds().getWidth();
	        double h = numText.getLayoutBounds().getHeight();
	        numText.setX(circle.getCenterX() - w / 2);
	        numText.setY(circle.getCenterY() + h / 4 - 1); // vertical optical adjustment
	    };
	    centerText.run();
	    numText.layoutBoundsProperty().addListener((obs, o, n) -> centerText.run());
	    circle.centerXProperty().addListener((obs, o, n) -> centerText.run());
	    circle.centerYProperty().addListener((obs, o, n) -> centerText.run());

        // Make nodes draggable
        circle.setOnMousePressed(e -> {
            if (addEdgeArmed) {
                // Start dragging edge from this node
                startEdgeDrag(nodeId, e.getSceneX(), e.getSceneY());
                e.consume();
            }
        });

        circle.setOnMouseDragged(e -> {
            if (isDraggingEdge) {
                // Update drag line position
                updateDragLine(e.getSceneX(), e.getSceneY());
                e.consume();
            } else {
                // Regular node dragging (repositioning)
                double nx = common.clamp(e.getX(), BOUNDARY_MARGIN, graphCanvas.getWidth() - BOUNDARY_MARGIN);
                double ny = common.clamp(e.getY(), BOUNDARY_MARGIN, graphCanvas.getHeight() - BOUNDARY_MARGIN);
                circle.setCenterX(nx);
                circle.setCenterY(ny);
                centerText.run();
                node.setX(nx);
                node.setY(ny);
            }
        });

        circle.setOnMouseReleased(e -> {
            if (isDraggingEdge) {
                // Check if released over another node
                finishEdgeDrag(e.getSceneX(), e.getSceneY());
                e.consume();
            }
        });

        circle.setOnMouseClicked(e -> {
            if (!addEdgeArmed) {
                // Non-edge mode: handle other node interactions (like source selection)
                handleNodeClickForNonEdgeMode(nodeId);
                e.consume();
            }
        });
	    graphCanvas.getChildren().addAll(circle, numText);
	    
	    // Check if algorithm checkboxes should be enabled
	    enableAlgorithmCheckboxesIfReady();
    }
    
    private void startEdgeDrag(String sourceNodeId, double sceneX, double sceneY) {
        isDraggingEdge = true;
        dragSourceNodeId = sourceNodeId;
        
        // Highlight source node
        highlightNode(sourceNodeId, true);
        
        // Create drag line
        dragLine = new Line();
        dragLine.setStroke(Color.web("#00d8ff"));
        dragLine.setStrokeWidth(3);
        dragLine.setOpacity(0.8);
        dragLine.getStrokeDashArray().addAll(5.0, 5.0); // Dashed line
        
        // Convert scene coordinates to canvas coordinates
        Circle sourceCircle = nodeCircles.get(sourceNodeId);
        if (sourceCircle != null) {
            dragLine.setStartX(sourceCircle.getCenterX());
            dragLine.setStartY(sourceCircle.getCenterY());
            dragLine.setEndX(sourceCircle.getCenterX());
            dragLine.setEndY(sourceCircle.getCenterY());
            
            graphCanvas.getChildren().add(dragLine);
        }
    }
    
    private void updateDragLine(double sceneX, double sceneY) {
        if (dragLine != null && dragSourceNodeId != null) {
            // Convert scene coordinates to canvas local coordinates
            javafx.geometry.Point2D localPoint = graphCanvas.sceneToLocal(sceneX, sceneY);
            dragLine.setEndX(localPoint.getX());
            dragLine.setEndY(localPoint.getY());
        }
    }
    
    private void finishEdgeDrag(double sceneX, double sceneY) {
        if (!isDraggingEdge || dragSourceNodeId == null) return;
        
        // Find if released over a node
        String targetNodeId = findNodeAtPosition(sceneX, sceneY);
        
        if (targetNodeId != null && !targetNodeId.equals(dragSourceNodeId)) {
            // Valid target found - create edge
            createEdge(dragSourceNodeId, targetNodeId);
        }
        
        // Clean up drag state
        cleanupEdgeDrag();
    }
    
    private String findNodeAtPosition(double sceneX, double sceneY) {
        // Convert scene coordinates to canvas coordinates
        javafx.geometry.Point2D localPoint = graphCanvas.sceneToLocal(sceneX, sceneY);
        
        for (Map.Entry<String, Circle> entry : nodeCircles.entrySet()) {
            Circle circle = entry.getValue();
            double centerX = circle.getCenterX();
            double centerY = circle.getCenterY();
            double distance = Math.sqrt(Math.pow(localPoint.getX() - centerX, 2) + 
                                      Math.pow(localPoint.getY() - centerY, 2));
            
            if (distance <= NODE_RADIUS) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    private void createEdge(String id1, String id2) {
        if (id1 == null || id2 == null || id1.equals(id2)) return;

        // For directed graphs preserve the drag direction (id1 -> id2).
        // For undirected graphs normalize ordering so A-B and B-A are treated as the same.
        String a;
        String b;
        String key;
        if (isDirected) {
            a = id1; // source
            b = id2; // target
            key = a + "->" + b;
        } else {
            a = id1.compareTo(id2) < 0 ? id1 : id2;
            b = id1.compareTo(id2) < 0 ? id2 : id1;
            key = a + "|" + b;
        }
        if (edgeKeys.contains(key)) return; // already exists (respecting direction if directed)
        Circle c1 = nodeCircles.get(id1);
        Circle c2 = nodeCircles.get(id2);
        if (c1 == null || c2 == null) return;
        
        System.out.println("Creating edge between " + id1 + " and " + id2 + ". isWeighted = " + isWeighted);
        
        double weight = 1.0; // default weight
        if (isWeighted) {
            // Show inline overlay instead of popup dialog
            Circle c1Ref = c1; Circle c2Ref = c2;
            showInlineWeightInput(id1, id2, c1Ref, c2Ref, (val, canceled) -> {
                if (canceled) return; // aborted
                double w = (val==null?1.0:val);
                finalizeEdgeCreation(a,b,key,c1Ref,c2Ref,w);
            });
            return; // Defer creation until weight chosen
        }
        // Unweighted path continues directly
        finalizeEdgeCreation(a,b,key,c1,c2,weight);
    }
    
    private void cleanupEdgeDrag() {
        isDraggingEdge = false;
        
        // Remove highlight from source node
        if (dragSourceNodeId != null) {
            highlightNode(dragSourceNodeId, false);
            dragSourceNodeId = null;
        }
        
        // Remove drag line
        if (dragLine != null) {
            graphCanvas.getChildren().remove(dragLine);
            dragLine = null;
        }
    }
    
    private void handleNodeClickForNonEdgeMode(String nodeId) {
        // Handle non-edge interactions like source selection for algorithms
        if (bfsSource == null && isSourceSelectionMode()) {
            setSourceNode(nodeId);
        }
    }
    
    private boolean isSourceSelectionMode() {
        // Check if any algorithm is selected and we're waiting for source selection
        return (isSelected("BFS") || isSelected("DFS") || 
                isSelected("Dijkstra") || isSelected("Bellman-Ford")) && bfsSource == null;
    }
    
    private void setSourceNode(String nodeId) {
        GraphNode node = graphNodes.get(nodeId);
        if (node != null) {
            bfsSource = node;
            highlightSourceNode(node.getCircle());
            enableRunButton();
            common.sortingStatusLabel.setText("Source node set: " + nodeId + ". Click RUN to start algorithm.");
        }
    }
    
    private void enableRunButton() {
        if (runGraphAlgosBtnRef != null) {
            runGraphAlgosBtnRef.setDisable(false);
        }
    }
    
    private void showInlineWeightInput(String from, String to, Circle c1, Circle c2, BiConsumer<Double,Boolean> consumer){
        if(graphCanvas==null) { consumer.accept(1.0,false); return; }
        // Clean any existing overlay
        if(currentWeightOverlay!=null) graphCanvas.getChildren().remove(currentWeightOverlay);
        if(outsideWeightClickHandler!=null) graphCanvas.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideWeightClickHandler);

        double midX = (c1.getCenterX()+c2.getCenterX())/2.0;
        double midY = (c1.getCenterY()+c2.getCenterY())/2.0;

        HBox box = new HBox(6);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(6,10,6,10));
        box.setStyle("-fx-background-color: rgba(5,18,28,0.92); -fx-background-radius:12; -fx-border-radius:12; -fx-border-color:#00ffff99; -fx-border-width:1.2; -fx-effect: dropshadow(gaussian, rgba(0,255,255,0.28),10,0,0,0);");
        Label lbl = new Label("Weight "+from+"→"+to+":");
        lbl.setStyle("-fx-text-fill:#66d9ff;-fx-font-size:11px;-fx-font-weight:600;");
        TextField tf = new TextField("1.0");
        tf.setPrefColumnCount(4);
        tf.setStyle("-fx-background-color: rgba(255,255,255,0.07); -fx-text-fill:#e8faff; -fx-font-size:12px; -fx-font-weight:600; -fx-background-radius:8; -fx-border-radius:8; -fx-border-color:#00ffff55; -fx-border-width:1; -fx-padding:3 6;");
        Button ok = new Button("ADD");
        ok.setStyle("-fx-background-color:#00b4d8;-fx-text-fill:white;-fx-font-weight:700;-fx-background-radius:8;-fx-padding:4 12; -fx-font-size:11px;");
        Button cancel = new Button("X");
        cancel.setStyle("-fx-background-color:#374151;-fx-text-fill:#eeeeee;-fx-font-weight:600;-fx-background-radius:8;-fx-padding:4 10; -fx-font-size:11px;");
        box.getChildren().addAll(lbl, tf, ok, cancel);
        box.setLayoutX(midX - 90);
        box.setLayoutY(midY - 28);
        currentWeightOverlay = box;
        graphCanvas.getChildren().add(box);
        tf.requestFocus();

        Runnable commit = () -> {
            Double val = 1.0;
            try { val = Double.parseDouble(tf.getText().trim()); } catch(Exception ex) { /* fallback 1.0 */ }
            cleanupInlineWeight();
            consumer.accept(val,false);
        };
        Runnable cancelRun = () -> { cleanupInlineWeight(); consumer.accept(null,true); };

        ok.setOnAction(e-> commit.run());
        cancel.setOnAction(e-> cancelRun.run());
        tf.setOnAction(e-> commit.run());
        tf.textProperty().addListener((o,ov,nv)-> {
            try { Double.parseDouble(nv.trim()); ok.setDisable(false);} catch(Exception ex){ ok.setDisable(true);} });
        tf.setOnKeyPressed(e-> { if(e.getCode()==KeyCode.ESCAPE) cancelRun.run(); });

        outsideWeightClickHandler = evt -> {
            if(!box.localToScene(box.getBoundsInLocal()).contains(evt.getSceneX(), evt.getSceneY())){
                // Click outside -> cancel
                cancelRun.run();
            }
        };
        graphCanvas.addEventFilter(MouseEvent.MOUSE_PRESSED, outsideWeightClickHandler);
    }
    
    private void cleanupInlineWeight(){
        if(currentWeightOverlay!=null) graphCanvas.getChildren().remove(currentWeightOverlay);
        currentWeightOverlay = null;
        if(outsideWeightClickHandler!=null) graphCanvas.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideWeightClickHandler);
        outsideWeightClickHandler = null;
    }
    
    private boolean hasNegativeWeights() {
        for (EdgeRecord edge : edges) {
            if (edge.weight < 0) {
                return true;
            }
        }
        return false;
    }
    
    private void enableAlgorithmCheckboxesIfReady() {
        if (graphAlgoChecks != null && !graphNodes.isEmpty() && !edges.isEmpty() && !graphAlgoSelectionLocked) {
            boolean hasNegativeEdges = hasNegativeWeights();
            
            // Enable all algorithms except handle Dijkstra specially
            graphAlgoChecks.values().forEach(cb -> {
                if (cb.getText().equals("Dijkstra") && hasNegativeEdges) {
                    cb.setDisable(true);
                    cb.setSelected(false); // Uncheck if it was selected
                    cb.setStyle("-fx-text-fill:#888888;-fx-font-size:11px;"); // Grayed out style
                    // Add tooltip to explain why it's disabled
                    Tooltip tooltip = new Tooltip("Dijkstra's algorithm cannot handle negative edge weights.\nUse Bellman-Ford algorithm instead.");
                    tooltip.setStyle("-fx-background-color: #2b2b2b; -fx-text-fill: #ffffff; -fx-font-size: 11px;");
                    cb.setTooltip(tooltip);
                } else {
                    cb.setDisable(false);
                    cb.setStyle("-fx-text-fill:#e0e0e0;-fx-font-size:11px;"); // Normal style
                    cb.setTooltip(null); // Remove tooltip
                }
            });
            
            if (hasNegativeEdges) {
                common.sortingStatusLabel.setText("Graph ready! Note: Dijkstra disabled due to negative weights.");
            } else {
                common.sortingStatusLabel.setText("Graph ready! Select algorithms to continue.");
            }
        }
    }
    
    private void finalizeEdgeCreation(String a, String b, String key, Circle c1, Circle c2, double weight){
        final double finalWeight = weight;
        
        Line line = new Line();
        line.setStroke(Color.web("#00d8ff"));
        line.setStrokeWidth(3);
        line.setOpacity(0.75);
        line.setEffect(new DropShadow(10, Color.web("#00ffff66")));
        
        // Calculate edge points on circumference
        Runnable updateLinePosition = () -> {
            double c1X = c1.getCenterX();
            double c1Y = c1.getCenterY();
            double c2X = c2.getCenterX();
            double c2Y = c2.getCenterY();
            
            // Calculate direction vector from c1 to c2
            double dx = c2X - c1X;
            double dy = c2Y - c1Y;
            double distance = Math.sqrt(dx * dx + dy * dy);
            
            if (distance > 0) {
                // Normalize direction vector
                dx /= distance;
                dy /= distance;
                
                // Calculate edge start and end points on circumference
                double startX = c1X + dx * NODE_RADIUS;
                double startY = c1Y + dy * NODE_RADIUS;
                double endX = c2X - dx * NODE_RADIUS;
                double endY = c2Y - dy * NODE_RADIUS;
                
                line.setStartX(startX);
                line.setStartY(startY);
                line.setEndX(endX);
                line.setEndY(endY);
            }
        };
        
	    // Bind line position updates to circle position changes
	    c1.centerXProperty().addListener((obs, o, n) -> updateLinePosition.run());
	    c1.centerYProperty().addListener((obs, o, n) -> updateLinePosition.run());
	    c2.centerXProperty().addListener((obs, o, n) -> updateLinePosition.run());
	    c2.centerYProperty().addListener((obs, o, n) -> updateLinePosition.run());
	    updateLinePosition.run(); // Initial positioning
	    // Add line immediately so subsequent label layout can measure relative to scene
	    graphCanvas.getChildren().add(0, line);
        
        Label weightLabel = null;
        if (isWeighted) {
            System.out.println("Creating weight label for weighted graph with weight: " + finalWeight);
            final Label wLabel = new Label(String.valueOf(finalWeight));
            wLabel.setStyle(
                "-fx-text-fill: white;" +
                "-fx-font-size: 14px;" +
                "-fx-font-weight: 700;" +
                "-fx-background-color: rgba(0, 43, 54, 0.85);" +
                "-fx-background-radius: 4;" +
                "-fx-padding: 2 6;" +
                "-fx-border-color: #00ffff;" +
                "-fx-border-width: 0.5;" +
                "-fx-border-radius: 4;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 8, 0.7, 2, 2), " +
                           "dropshadow(gaussian, rgba(0,0,0,0.9), 3, 0.8, 1, 1);"
            );
            // Use managed=true to let JavaFX handle layout
            wLabel.setManaged(true);
            wLabel.setMouseTransparent(true);
            wLabel.setVisible(true);
            
            weightLabel = wLabel;
            System.out.println("Weight label created. Text: " + wLabel.getText());

            Runnable updateWeightPos = () -> {
                double midX = (line.getStartX() + line.getEndX()) / 2.0;
                double midY = (line.getStartY() + line.getEndY()) / 2.0;
                
                // Find a non-overlapping position for the weight label
                double[] adjustedPos = findNonOverlappingPosition(midX, midY, wLabel);
                wLabel.setLayoutX(adjustedPos[0]);
                wLabel.setLayoutY(adjustedPos[1]);
                System.out.println("Positioned weight label at layoutX=" + wLabel.getLayoutX() + ", layoutY=" + wLabel.getLayoutY());
            };
            
            // Add label to canvas after setting up positioning
            graphCanvas.getChildren().add(wLabel);
            System.out.println("Weight label added to canvas");
            
            // Listeners for repositioning - also update positions of all other labels to avoid new overlaps
            line.startXProperty().addListener((o,ov,nv)->{
                updateWeightPos.run();
                // Update positions of other weight labels that might now overlap
                repositionAllWeightLabels();
            });
            line.startYProperty().addListener((o,ov,nv)->{
                updateWeightPos.run();
                repositionAllWeightLabels();
            });
            line.endXProperty().addListener((o,ov,nv)->{
                updateWeightPos.run();
                repositionAllWeightLabels();
            });
            line.endYProperty().addListener((o,ov,nv)->{
                updateWeightPos.run();
                repositionAllWeightLabels();
            });
            
            // Initial positioning
            updateWeightPos.run();
        }
        
        // Create arrow head for directed graphs
        Polygon arrowHead = null;
        if (isDirected) {
            final Polygon arrow = new Polygon();
            arrow.setFill(Color.web("#00d8ff"));
            arrow.setStroke(Color.web("#00d8ff"));
            arrowHead = arrow;
            
            // Update arrow position based on line end
            Runnable updateArrow = () -> {
                double startX = line.getStartX();
                double startY = line.getStartY();
                double endX = line.getEndX();
                double endY = line.getEndY();
                
                // Calculate direction vector
                double dx = endX - startX;
                double dy = endY - startY;
                double length = Math.sqrt(dx * dx + dy * dy);
                
                if (length > 0) {
                    // Normalize direction
                    dx /= length;
                    dy /= length;
                    
                    // Arrow position at edge end
                    double arrowX = endX;
                    double arrowY = endY;
                    
                    // Arrow size
                    double arrowSize = 12;
                    
                    // Arrow points
                    double perpX = -dy * arrowSize * 0.5;
                    double perpY = dx * arrowSize * 0.5;
                    
                    arrow.getPoints().clear();
                    arrow.getPoints().addAll(new Double[]{
                        arrowX, arrowY,  // tip
                        arrowX - dx * arrowSize + perpX, arrowY - dy * arrowSize + perpY,  // left wing
                        arrowX - dx * arrowSize - perpX, arrowY - dy * arrowSize - perpY   // right wing
                    });
                }
            };
            
            line.startXProperty().addListener((obs, o, n) -> updateArrow.run());
            line.startYProperty().addListener((obs, o, n) -> updateArrow.run());
            line.endXProperty().addListener((obs, o, n) -> updateArrow.run());
            line.endYProperty().addListener((obs, o, n) -> updateArrow.run());
            updateArrow.run();
        }
        
        // (Line already added earlier). Add arrow head above line, ensure weight label stays top.
        if (arrowHead != null) {
            graphCanvas.getChildren().add(arrowHead);
        }
        if (weightLabel != null) {
            weightLabel.toFront();
        }
        
	    // Store edge record. For directed graphs 'a' is the true source and 'b' the target.
	    edges.add(new EdgeRecord(line, a, b, finalWeight, weightLabel, arrowHead));
	    edgeKeys.add(key);
        
        // Check if algorithm checkboxes should be enabled
        enableAlgorithmCheckboxesIfReady();
    }
    
    private void repositionAllWeightLabels() {
        // Delay repositioning to avoid excessive updates during animation
        Platform.runLater(() -> {
            for (EdgeRecord edge : edges) {
                if (edge.weightLabel != null && edge.weightLabel.isVisible()) {
                    // Calculate preferred position based on edge midpoint
                    double midX = (edge.line.getStartX() + edge.line.getEndX()) / 2.0;
                    double midY = (edge.line.getStartY() + edge.line.getEndY()) / 2.0;
                    
                    // Find non-overlapping position
                    double[] adjustedPos = findNonOverlappingPosition(midX, midY, edge.weightLabel);
                    edge.weightLabel.setLayoutX(adjustedPos[0]);
                    edge.weightLabel.setLayoutY(adjustedPos[1]);
                }
            }
        });
    }
    
    private double[] findNonOverlappingPosition(double preferredX, double preferredY, Label newLabel) {
        // Force layout calculation to get accurate dimensions
        newLabel.applyCss();
        newLabel.layout();
        
        final double LABEL_WIDTH = Math.max(40, newLabel.getWidth() + 10);  // Actual width + padding
        final double LABEL_HEIGHT = Math.max(20, newLabel.getHeight() + 5); // Actual height + padding
        
        // Try the preferred position first
        double bestX = preferredX - LABEL_WIDTH / 2;
        double bestY = preferredY - LABEL_HEIGHT / 2;
        
        // Check if this position overlaps with any existing weight labels
        boolean hasOverlap = false;
        for (EdgeRecord edge : edges) {
            if (edge.weightLabel != null && edge.weightLabel != newLabel && edge.weightLabel.isVisible()) {
                double existingX = edge.weightLabel.getLayoutX();
                double existingY = edge.weightLabel.getLayoutY();
                
                // Get actual dimensions of existing label
                edge.weightLabel.applyCss();
                edge.weightLabel.layout();
                double existingWidth = Math.max(40, edge.weightLabel.getWidth() + 10);
                double existingHeight = Math.max(20, edge.weightLabel.getHeight() + 5);
                
                // Check for rectangle overlap using actual bounds
                boolean xOverlap = (bestX < existingX + existingWidth) && (bestX + LABEL_WIDTH > existingX);
                boolean yOverlap = (bestY < existingY + existingHeight) && (bestY + LABEL_HEIGHT > existingY);
                
                if (xOverlap && yOverlap) {
                    hasOverlap = true;
                    break;
                }
            }
        }
        
        // If no overlap, use the preferred position
        if (!hasOverlap) {
            return new double[]{bestX, bestY};
        }
        
        // Try alternative positions in a spiral pattern around the preferred location
        double[][] offsets = {
            // Close positions first
            {0, -35}, {35, 0}, {0, 35}, {-35, 0},
            // Diagonal positions
            {30, -30}, {30, 30}, {-30, 30}, {-30, -30},
            // Further positions
            {0, -50}, {50, 0}, {0, 50}, {-50, 0},
            {40, -40}, {40, 40}, {-40, 40}, {-40, -40},
            // Even further if needed
            {0, -65}, {65, 0}, {0, 65}, {-65, 0}
        };
        
        for (double[] offset : offsets) {
            double tryX = preferredX - LABEL_WIDTH / 2 + offset[0];
            double tryY = preferredY - LABEL_HEIGHT / 2 + offset[1];
            
            boolean positionClear = true;
            for (EdgeRecord edge : edges) {
                if (edge.weightLabel != null && edge.weightLabel != newLabel && edge.weightLabel.isVisible()) {
                    double existingX = edge.weightLabel.getLayoutX();
                    double existingY = edge.weightLabel.getLayoutY();
                    
                    // Get actual dimensions of existing label
                    edge.weightLabel.applyCss();
                    edge.weightLabel.layout();
                    double existingWidth = Math.max(40, edge.weightLabel.getWidth() + 10);
                    double existingHeight = Math.max(20, edge.weightLabel.getHeight() + 5);
                    
                    // Check for rectangle overlap using actual bounds
                    boolean xOverlap = (tryX < existingX + existingWidth) && (tryX + LABEL_WIDTH > existingX);
                    boolean yOverlap = (tryY < existingY + existingHeight) && (tryY + LABEL_HEIGHT > existingY);
                    
                    if (xOverlap && yOverlap) {
                        positionClear = false;
                        break;
                    }
                }
            }
            
            if (positionClear) {
                return new double[]{tryX, tryY};
            }
        }
        
        // If all positions are blocked, just use the preferred position (fallback)
        return new double[]{bestX, bestY};
    }
    
    private void resetGraphUIState() {
        // Stop any running visualization immediately
        stopCurrentVisualization();
        
        animationSpeed = 1.0;
        if (graphSpeedSlider != null) graphSpeedSlider.setValue(1.0);
        clearGraphExplanation();
    }
}

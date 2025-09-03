package algobot;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.text.TextAlignment;
import javafx.scene.layout.*;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import java.util.*;

public class MergeSortManager {
	private Timeline mergeSortTimeline;
	private Timeline mergeSortPulseTimeline;
	private boolean mergeSortRunning = false;
	private boolean mergeSortPaused = false;
	private MergeSortTreeNode mergeSortRoot;
	
    private List<Integer> originalMergeArray = new ArrayList<>();
    private Queue<MergeSortTreeNode> divideQueue = new LinkedList<>();
    private Stack<MergeSortTreeNode> mergeStack = new Stack<>(); // kept for compatibility (unused)
    private Queue<MergeAction> mergeActionQueue = new LinkedList<>(); // post-order: leaf, leaf, merge parent; ... up to root
    private MergeSortTreeNode currentNode;
    private String currentPhase = ""; // "DIVIDE" or "MERGE"
    
    // Step tracking for pause/resume and previous step functionality
    private List<MergeSortStep> allMergeSortSteps = new ArrayList<>();
    private int currentMergeSortStepIndex = -1;
    
    // Merge sort specific components
    private Canvas mergeSortCanvas;
    private TextField mergeSortInputField;
    private Label mergeSortStatusLabel;
    private Button startMergeSortBtn, resetMergeSortBtn, pauseResumeMergeSortBtn, previousStepMergeSortBtn;
    private TextArea mergeSortExplanationArea;
    
    private double mergePulse = 0.0;
    private Slider mergeSortSpeedSlider;
    private Label mergeSortSpeedLabel;
    private int mergeSortSpeed = 1500; 
    
    private static class MergeSortTreeNode {
        List<Integer> array;
        int left, right; // original indices in the full array
        int level; // depth in the tree
        boolean isLeaf;
        boolean isDividing; // true during divide phase
        boolean isMerging; // true during merge phase
        boolean isCompleted; // merge completed
        MergeSortTreeNode leftChild, rightChild;
        String currentAction = ""; // current action description

        double posX;
        double posY;
        
        MergeSortTreeNode(List<Integer> arr, int l, int r, int level) {
            this.array = new ArrayList<>(arr);
            this.left = l; this.right = r; this.level = level;
            this.isLeaf = (l == r);
        }
    }
    
    private static class MergeAction {
        enum Type { VISIT, MERGE }
        final Type type;
        final MergeSortTreeNode node;
        MergeAction(Type type, MergeSortTreeNode node) {
            this.type = type; this.node = node;
        }
    }
	
	public void createMergeSortPage() {
    	common.mergeSortContainer = new VBox(15);
    	common.mergeSortContainer.setAlignment(Pos.TOP_CENTER);
    	common.mergeSortContainer.setPadding(new Insets(80, 20, 20, 20));
        
        // Match project's dark gradient theme
        LinearGradient gradient = new LinearGradient(0, 0, 1, 1, true, null,
            new Stop(0, Color.web("#0c0c0c")),
            new Stop(0.3, Color.web("#141e30")),
            new Stop(0.6, Color.web("#243b55")),
            new Stop(1, Color.web("#0f051d"))
        );
        
        common.mergeSortContainer.setBackground(new Background(new BackgroundFill(gradient, null, null)));

        // Title with back button
        HBox title = ButtonManager.createTitleWithBackButton("MERGE SORT RECURSIVE TREE");
        if (!title.getChildren().isEmpty() && title.getChildren().get(0) instanceof Button b) {
            b.setOnAction(e -> {
                clearMergeSortState();
                common.transitionToPage(common.homeContainer);
            });
        }
        
        common.mergeSortContainer.getChildren().add(title);

        // Input section
        VBox inputSection = new VBox(8);
        inputSection.setAlignment(Pos.CENTER);
        Label prompt = new Label("Enter space-separated numbers to visualize recursive merge sort");
        prompt.setTextFill(Color.WHITE);
        prompt.setFont(Font.font("SF Pro Text", FontWeight.MEDIUM, 14));
        
	    mergeSortInputField = new TextField();
	    mergeSortInputField.setPromptText("e.g., 64 34 25 12 22 11 90");
	    // Make the input box a bit smaller
	    mergeSortInputField.setPrefWidth(240);
	    mergeSortInputField.setStyle("-fx-background-radius:8; -fx-background-color: rgba(0,255,255,0.10); -fx-text-fill: white; -fx-padding:8 12; -fx-border-color: rgba(0,255,255,0.75); -fx-border-radius:8; -fx-border-width:1; -fx-font-size:14px; -fx-font-weight:600;");
        
        HBox inputControls = new HBox(12);
        inputControls.setAlignment(Pos.CENTER);
        startMergeSortBtn = ButtonManager.createStyledButton("START VISUALIZATION", "#00ffff");
        pauseResumeMergeSortBtn = ButtonManager.createStyledButton("PAUSE", "#00ffff");
        previousStepMergeSortBtn = ButtonManager.createStyledButton("PREVIOUS STEP", "#00ffff");
        resetMergeSortBtn = ButtonManager.createStyledButton("RESET", "#00ffff");
        
        // Initially disable pause and previous step buttons
        pauseResumeMergeSortBtn.setDisable(true);
        previousStepMergeSortBtn.setDisable(true);
        
        inputControls.getChildren().addAll(startMergeSortBtn, pauseResumeMergeSortBtn, previousStepMergeSortBtn, resetMergeSortBtn);
        
        inputSection.getChildren().addAll(prompt, mergeSortInputField, inputControls);
        common.mergeSortContainer.getChildren().add(inputSection);

        // Main visualization area - split into canvas and explanation
        HBox mainSplit = new HBox(18);
        mainSplit.setAlignment(Pos.TOP_CENTER);
        mainSplit.setFillHeight(true);

        // Left side - Tree canvas + controls
        VBox leftPane = new VBox(8);
        leftPane.setAlignment(Pos.TOP_CENTER);
        
        // Canvas for recursive tree visualization (slightly smaller)
        mergeSortCanvas = new Canvas(1000, 680);
        StackPane canvasWrapper = new StackPane(mergeSortCanvas);
        canvasWrapper.setPadding(new Insets(8));
        // cyan-ish subtle background
        canvasWrapper.setStyle("-fx-background-color: rgba(0,255,255,0.07); -fx-background-radius:12; -fx-border-radius:12; -fx-border-color: rgba(0,255,255,0.25); -fx-border-width:1;");
        leftPane.getChildren().add(canvasWrapper);

        // Status and speed controls
        mergeSortStatusLabel = new Label("Enter array and start visualization");
        mergeSortStatusLabel.setTextFill(Color.web("#cccccc"));
        mergeSortStatusLabel.setFont(Font.font("SF Pro Text", FontWeight.NORMAL, 12));
        
        HBox speedControls = new HBox(12);
        speedControls.setAlignment(Pos.CENTER);
        mergeSortSpeedSlider = new Slider(500, 3000, mergeSortSpeed);
        mergeSortSpeedSlider.setStyle("-fx-accent: #00ffff;");
        mergeSortSpeedSlider.setPrefWidth(200);
        mergeSortSpeedLabel = new Label("Speed: " + mergeSortSpeed + "ms");
        mergeSortSpeedLabel.setTextFill(Color.web("#dddddd"));
        mergeSortSpeedLabel.setFont(Font.font("SF Pro Text", 11));
	    speedControls.getChildren().addAll(mergeSortSpeedSlider, mergeSortSpeedLabel);
	
	    // Quick speed presets
	    HBox speedBar = new HBox(8);
	    speedBar.setAlignment(Pos.CENTER);
	    Button slowBtn = ButtonManager.createStyledButton("SLOW", "#00ffff");
	    Button normalBtn = ButtonManager.createStyledButton("NORMAL", "#00ffff");
	    Button fastBtn = ButtonManager.createStyledButton("FAST", "#00ffff");
	    slowBtn.setOnAction(ev -> mergeSortSpeedSlider.setValue(2200));
	    normalBtn.setOnAction(ev -> mergeSortSpeedSlider.setValue(1500));
	    fastBtn.setOnAction(ev -> mergeSortSpeedSlider.setValue(700));
	        
	    leftPane.getChildren().addAll(mergeSortStatusLabel, speedControls, speedBar);
	    speedBar.getChildren().addAll(slowBtn, normalBtn, fastBtn);


        // Right side - Step explanation
        VBox rightPane = new VBox(8);
        rightPane.setAlignment(Pos.TOP_LEFT);
        rightPane.setPrefWidth(340);
        Label explLabel = new Label("RECURSIVE TREE EXPLANATION");
        explLabel.setTextFill(Color.web("#66d9ff"));
        explLabel.setFont(Font.font("SF Pro Text", FontWeight.SEMI_BOLD, 12));
        
        mergeSortExplanationArea = new TextArea();
        mergeSortExplanationArea.setEditable(false);
        mergeSortExplanationArea.setWrapText(true);
        mergeSortExplanationArea.setPrefWidth(340);
        // Decrease height while keeping width unchanged
        mergeSortExplanationArea.setPrefHeight(440);
        mergeSortExplanationArea.setMaxWidth(340);
        mergeSortExplanationArea.setStyle(
            "-fx-control-inner-background: rgba(15,18,26,0.92);" +
            "-fx-background-color: rgba(15,18,26,0.92);" +
            "-fx-text-fill: #f5f7fa;" +
            "-fx-highlight-fill: #3d6ef7;" +
            "-fx-highlight-text-fill: white;" +
            "-fx-font-family: 'SF Pro Text';" +
            "-fx-font-size: 14px;" +
            "-fx-prompt-text-fill: rgba(255,255,255,0.75);" +
            "-fx-background-radius:12; -fx-border-radius:12;" +
            "-fx-border-color: rgba(255,255,255,0.25); -fx-border-width:1;" +
            "-fx-padding:8;"
        );
        mergeSortExplanationArea.setPromptText("Divide and merge steps will appear here...");
        // Keep the explanation box at its preferred height (do not grow vertically)
        VBox.setVgrow(mergeSortExplanationArea, Priority.NEVER);
        rightPane.getChildren().addAll(explLabel, mergeSortExplanationArea);

        HBox.setHgrow(leftPane, Priority.ALWAYS);
        mainSplit.getChildren().addAll(leftPane, rightPane);
        common.mergeSortContainer.getChildren().add(mainSplit);

        // Event handlers
        startMergeSortBtn.setOnAction(e -> startMergeSortVisualization());
        pauseResumeMergeSortBtn.setOnAction(e -> toggleMergeSortPause());
        previousStepMergeSortBtn.setOnAction(e -> goToPreviousMergeSortStep());
        resetMergeSortBtn.setOnAction(e -> clearMergeSortState());
        
        mergeSortSpeedSlider.valueProperty().addListener((obs, o, n) -> {
            mergeSortSpeed = n.intValue();
            mergeSortSpeedLabel.setText("Speed: " + mergeSortSpeed + "ms");
            if (mergeSortTimeline != null && mergeSortRunning) {
                // Update timeline speed
                mergeSortTimeline.stop();
                createMergeSortTimeline();
            }
        });
        
        // Allow Enter key to start visualization
        mergeSortInputField.setOnAction(e -> startMergeSortVisualization());
    }
	
    public void clearMergeSortState() {
        if (mergeSortTimeline != null) {
            mergeSortTimeline.stop();
        }
        
        stopMergeSortPulse();
        
        mergeSortRunning = false;
        mergeSortPaused = false;
        mergeSortRoot = null;
        originalMergeArray.clear();
        divideQueue.clear();
        mergeStack.clear();
        mergeActionQueue.clear();
        allMergeSortSteps.clear();
        currentMergeSortStepIndex = -1;
        currentNode = null;
        currentPhase = "";
        
        if (mergeSortInputField != null) {
            mergeSortInputField.clear();
            mergeSortInputField.setDisable(false);
        }
        if (mergeSortStatusLabel != null) {
            mergeSortStatusLabel.setText("Enter array and start visualization");
        }
        if (mergeSortExplanationArea != null) {
            mergeSortExplanationArea.clear();
        }
        if (startMergeSortBtn != null) {
            startMergeSortBtn.setDisable(false);
        }
        if (pauseResumeMergeSortBtn != null) {
            pauseResumeMergeSortBtn.setDisable(true);
            pauseResumeMergeSortBtn.setText("PAUSE");
        }
        if (previousStepMergeSortBtn != null) {
            previousStepMergeSortBtn.setDisable(true);
        }
        if (mergeSortCanvas != null) {
            GraphicsContext g = mergeSortCanvas.getGraphicsContext2D();
            g.clearRect(0, 0, mergeSortCanvas.getWidth(), mergeSortCanvas.getHeight());
        }
    }
	
	private void stopMergeSortPulse() {
        if (mergeSortPulseTimeline != null) {
            mergeSortPulseTimeline.stop();
            mergeSortPulseTimeline = null;
        }
    }
	
	private static class MergeSortStep {
        String phase; // "DIVIDE" or "MERGE"
        MergeSortTreeNode activeNode;
        Queue<MergeSortTreeNode> divideQueueSnapshot;
        Queue<MergeAction> mergeActionQueueSnapshot;
        String explanation;
        String statusText;
        
        MergeSortStep(String phase, MergeSortTreeNode activeNode, 
                     Queue<MergeSortTreeNode> divideQueue, 
                     Queue<MergeAction> mergeActionQueue,
                     String explanation, String statusText) {
            this.phase = phase;
            this.activeNode = activeNode;
            this.divideQueueSnapshot = new LinkedList<>(divideQueue);
            this.mergeActionQueueSnapshot = new LinkedList<>(mergeActionQueue);
            this.explanation = explanation;
            this.statusText = statusText;
        }
    }
	
	private void startMergeSortVisualization() {
        if (mergeSortRunning) return;
        
	    if (mergeSortTimeline != null) { 
	    	mergeSortTimeline.stop(); 
	    	mergeSortTimeline = null; 
	    }
	    
	    stopMergeSortPulse();
	    currentNode = null;
	    currentPhase = "";
	    
	    if (mergeSortExplanationArea != null) mergeSortExplanationArea.clear();

        String text = mergeSortInputField.getText().trim();
        if (text.isEmpty()) {
            mergeSortStatusLabel.setText("Please enter some numbers.");
            return;
        }
        
        String[] parts = text.split("\\s+");
        List<Integer> values = new ArrayList<>();
        try {
            for (String p : parts) {
                if (!p.isBlank()) {
                    values.add(Integer.parseInt(p));
                }
            }
        } catch (NumberFormatException ex) {
            mergeSortStatusLabel.setText("Invalid number detected.");
            return;
        }
        
        if (values.isEmpty()) {
            mergeSortStatusLabel.setText("No valid numbers entered.");
            return;
        }
        
        if (values.size() > 16) {
            mergeSortStatusLabel.setText("Too many elements (max 16 for clear visualization).");
            return;
        }
        
        // Initialize merge sort visualization
        // Clear input so we start fresh and avoid accidental reuse
        originalMergeArray = new ArrayList<>(values);
        buildMergeSortTree();
        
        // Precompute all steps for pause/resume and previous step functionality
        precomputeAllMergeSortSteps();
        
        mergeSortRunning = true;
        mergeSortPaused = false;
        currentMergeSortStepIndex = -1;
        currentPhase = "DIVIDE";
        
	    // Clear the text box on start as requested and disable it during run
	    mergeSortInputField.clear();
	    startMergeSortBtn.setDisable(true);
	    mergeSortInputField.setDisable(true);
	    pauseResumeMergeSortBtn.setDisable(false);
	    pauseResumeMergeSortBtn.setText("PAUSE");
	    previousStepMergeSortBtn.setDisable(false);
	        
	    mergeSortStatusLabel.setText("Starting divide phase...");
	    updateMergeSortExplanation("MERGE SORT VISUALIZATION STARTED\n" +
	          "Original array: " + values + "\n" +
	          "Phase 1: DIVIDE - Breaking array into single elements\n" +
	          "Phase 2: MERGE - Combining sorted subarrays\n\n");
	        
	    createMergeSortTimeline();
	    startMergeSortPulse();
    }
	
	private void buildMergeSortTree() {
        mergeSortRoot = new MergeSortTreeNode(originalMergeArray, 0, originalMergeArray.size() - 1, 0);
        divideQueue.clear();
        mergeStack.clear();
        mergeActionQueue.clear();
        
        // Build recursive call order (pre-order for divide, post-order for merge)
        buildRecursiveCallOrder(mergeSortRoot);
        // Build merge action order: post-order (visit children then merge parent)
        buildMergeActionQueue();
    }
	
	private void buildRecursiveCallOrder(MergeSortTreeNode node) {
        if (node == null) return;
        
        // Add current node to divide queue (pre-order: current, left, right)
        divideQueue.add(node);
        
        if (!node.isLeaf) {
            int mid = (node.left + node.right) / 2;
            
            // Create children for recursive calls
            List<Integer> leftArray = originalMergeArray.subList(node.left, mid + 1);
            List<Integer> rightArray = originalMergeArray.subList(mid + 1, node.right + 1);
            
            node.leftChild = new MergeSortTreeNode(leftArray, node.left, mid, node.level + 1);
            node.rightChild = new MergeSortTreeNode(rightArray, mid + 1, node.right, node.level + 1);
            
            // Recursive calls in order: left first, then right
            buildRecursiveCallOrder(node.leftChild);
            buildRecursiveCallOrder(node.rightChild);
        }
    }
	
	private void buildMergeActionQueue() {
        mergeActionQueue.clear();
        buildMergeActions(mergeSortRoot);
    }
	
	private void buildMergeActions(MergeSortTreeNode node) {
	     if (node == null) return;
	     
	     if (node.isLeaf) {
	         mergeActionQueue.add(new MergeAction(MergeAction.Type.VISIT, node));
	         return;
	     }
	     
	     buildMergeActions(node.leftChild);
	     buildMergeActions(node.rightChild);
	     
	     mergeActionQueue.add(new MergeAction(MergeAction.Type.MERGE, node));
	}
	 
	private void precomputeAllMergeSortSteps() {
	        allMergeSortSteps.clear();
	        
	        // Create temporary copies for simulation
	        Queue<MergeSortTreeNode> tempDivideQueue = new LinkedList<>(divideQueue);
	        Queue<MergeAction> tempMergeActionQueue = new LinkedList<>(mergeActionQueue);
	        
	        // Simulate DIVIDE phase
	        while (!tempDivideQueue.isEmpty()) {
	            MergeSortTreeNode node = tempDivideQueue.poll();
	            String rangeStr = node.isLeaf ? 
	                "[" + node.array.get(0) + "]" :
	                node.array.toString();
	            
	            String explanation;
	            String statusText;
	            
	            if (node.isLeaf) {
	                explanation = "✓ Leaf reached: " + rangeStr + " (single element)\n";
	                statusText = "Divide phase: reached leaf " + rangeStr;
	            } 
	            else {
	                explanation = "→ Dividing: " + rangeStr + " into smaller parts\n";
	                statusText = "Divide phase: dividing " + rangeStr;
	            }
	            
	            MergeSortStep step = new MergeSortStep("DIVIDE", node, tempDivideQueue, tempMergeActionQueue, explanation, statusText);
	            allMergeSortSteps.add(step);
	        }
	        
	        // Add transition step
	        MergeSortStep transitionStep = new MergeSortStep("TRANSITION", null, new LinkedList<>(), tempMergeActionQueue,
	            "\n--- DIVIDE PHASE COMPLETE ---\nNow starting MERGE phase (combining sorted subarrays)...\n\n",
	            "Divide complete. Starting merge phase...");
	        allMergeSortSteps.add(transitionStep);
	        
	        // Simulate MERGE phase
	        while (!tempMergeActionQueue.isEmpty()) {
	            MergeAction action = tempMergeActionQueue.poll();
	            MergeSortTreeNode node = action.node;
	            
	            String explanation;
	            String statusText;
	            if (action.type == MergeAction.Type.VISIT) {
	                explanation = "• Visiting leaf: " + node.array + "\n";
	                statusText = "Merge phase: visiting leaf " + node.array;
	            } else { // MERGE
	                if (node.leftChild != null && node.rightChild != null) {
	                    List<Integer> merged = mergeTwoArrays(node.leftChild.array, node.rightChild.array);
	                    explanation = "⟳ Merging: " + node.leftChild.array + " + " +
	                        node.rightChild.array + " = " + merged + "\n";
	                    statusText = "Merge phase: merging " + node.leftChild.array + " + " + node.rightChild.array;
	                } else {
	                    explanation = "• Processing merge step\n";
	                    statusText = "Merge phase: processing";
	                }
	            }
	            
	            MergeSortStep step = new MergeSortStep("MERGE", node, new LinkedList<>(), tempMergeActionQueue, explanation, statusText);
	            allMergeSortSteps.add(step);
	        }
	        
	        // Add completion step
	        MergeSortStep completionStep = new MergeSortStep("COMPLETE", null, new LinkedList<>(), new LinkedList<>(),
	            "\n🎉 MERGE SORT COMPLETE!\nFinal sorted array: " + originalMergeArray + "\n",
	            "Merge sort complete! Final sorted array: " + originalMergeArray);
	        allMergeSortSteps.add(completionStep);
	 }
	 
	 private List<Integer> mergeTwoArrays(List<Integer> left, List<Integer> right) {
	     List<Integer> merged = new ArrayList<>();
	     int i = 0, j = 0;
	        
	     while (i < left.size() && j < right.size()) {
	         if (left.get(i) <= right.get(j)) {
	             merged.add(left.get(i));
	             i++;
	         } 
	         else {
	             merged.add(right.get(j));
	             j++;
	         }
	     }
	        
	     while (i < left.size()) merged.add(left.get(i++));
	     while (j < right.size()) merged.add(right.get(j++));
	        
	     return merged;
	 }
	 
	 private void updateMergeSortExplanation(String text) {
	     // Append and robustly scroll to bottom (defer caret/scroll until after layout pass)
	     mergeSortExplanationArea.appendText(text);
	     
         Platform.runLater(() -> {
	         mergeSortExplanationArea.positionCaret(mergeSortExplanationArea.getLength());
	         mergeSortExplanationArea.setScrollTop(Double.MAX_VALUE);
	     });
	 }
	 
	 private void createMergeSortTimeline() {
	     if (mergeSortTimeline != null) mergeSortTimeline.stop();
	        
	     mergeSortTimeline = new Timeline(new KeyFrame(Duration.millis(mergeSortSpeed), e -> {
	         if (!mergeSortPaused) {
	             stepMergeSortVisualization();
	         }
	     }));
	     mergeSortTimeline.setCycleCount(Timeline.INDEFINITE);
	     mergeSortTimeline.play();
	 }
	 
	// Lightweight pulsing animation to highlight the current node
	private void startMergeSortPulse() {
	     if (mergeSortPulseTimeline != null) {
	         mergeSortPulseTimeline.stop();
	     }
	     mergePulse = 0.0;
	     mergeSortPulseTimeline = new Timeline(new KeyFrame(Duration.millis(33), e -> {
	         mergePulse += 0.18;
	         if (mergePulse > Math.PI * 2) mergePulse -= Math.PI * 2;
	         // Only redraw when we have an active node
	         if (mergeSortRoot != null && currentNode != null) {
	             drawMergeSortTree();
	         }
	     }));
	     mergeSortPulseTimeline.setCycleCount(Timeline.INDEFINITE);
	     mergeSortPulseTimeline.play();
	}
	
	private void stepMergeSortVisualization() {
        if (mergeSortPaused) return;
        
        currentMergeSortStepIndex++;
        if (currentMergeSortStepIndex >= allMergeSortSteps.size()) {
            completeMergeSortVisualization();
            return;
        }
        
        executeStepAtIndex(currentMergeSortStepIndex);
    }
	
	private void completeMergeSortVisualization() {
        if (mergeSortTimeline != null)  mergeSortTimeline.stop();
     
        stopMergeSortPulse();
        mergeSortRunning = false;
        mergeSortPaused = false;
        startMergeSortBtn.setDisable(false);
        mergeSortInputField.setDisable(false);
        pauseResumeMergeSortBtn.setDisable(true);
        pauseResumeMergeSortBtn.setText("PAUSE");
        previousStepMergeSortBtn.setDisable(true);
    }
	
	private void executeStepAtIndex(int stepIndex) {
        if (stepIndex < 0 || stepIndex >= allMergeSortSteps.size()) return;
        
        MergeSortStep step = allMergeSortSteps.get(stepIndex);
        currentPhase = step.phase;
        
        if (step.phase.equals("DIVIDE")) {
            currentNode = step.activeNode;
            if (currentNode != null)  currentNode.isDividing = true;
            
            updateMergeSortExplanation(step.explanation);
            mergeSortStatusLabel.setText(step.statusText);
            drawMergeSortTree();
        } 
        else if (step.phase.equals("TRANSITION")) {
            updateMergeSortExplanation(step.explanation);
            mergeSortStatusLabel.setText(step.statusText);
        } 
        else if (step.phase.equals("MERGE")) {
            currentNode = step.activeNode;
            if (currentNode != null) {
                // Reset transient flags
                clearMergingFlags(mergeSortRoot);
                
                // Find the corresponding action in the original merge queue
                MergeAction correspondingAction = findCorrespondingAction(step);
                if (correspondingAction != null) {
                    if (correspondingAction.type == MergeAction.Type.VISIT) {
                        currentNode.isMerging = true;
                    } else { // MERGE
                        if (currentNode.leftChild != null) currentNode.leftChild.isMerging = true;
                        if (currentNode.rightChild != null) currentNode.rightChild.isMerging = true;
                        currentNode.isMerging = true;
                        
                        if (currentNode.leftChild != null && currentNode.rightChild != null) {
                            List<Integer> merged = mergeTwoArrays(currentNode.leftChild.array, currentNode.rightChild.array);
                            currentNode.array = merged;
                        }
                        currentNode.isCompleted = true;
                    }
                }
            }
            updateMergeSortExplanation(step.explanation);
            mergeSortStatusLabel.setText(step.statusText);
            drawMergeSortTree();
        } 
        else if (step.phase.equals("COMPLETE")) {
            // Finalize visuals
            clearMergingFlags(mergeSortRoot);
            if (mergeSortRoot != null) {
                mergeSortRoot.isCompleted = true;
            }
            currentNode = null;
            drawMergeSortTree();
            updateMergeSortExplanation(step.explanation);
            mergeSortStatusLabel.setText(step.statusText);
            completeMergeSortVisualization();
        }
    }
	
	private void drawMergeSortTree() {
	    GraphicsContext g = mergeSortCanvas.getGraphicsContext2D();
	    // Project-wide dark blue gradient theme (avoid greenish tint)
	    double w = mergeSortCanvas.getWidth(), h = mergeSortCanvas.getHeight();
	    Stop[] stops = new Stop[]{
	        new Stop(0.0, Color.web("#0c0c0c")),
	        new Stop(0.30, Color.web("#141e30")),
	        new Stop(0.65, Color.web("#243b55")),
	        new Stop(1.0, Color.web("#0f051d"))
	    };
	    LinearGradient lg = new LinearGradient(0, 0, 1, 1, true, null, stops);
	    g.setFill(lg);
	    g.fillRect(0, 0, w, h);
	    if (mergeSortRoot == null) return;

	    // Compute node positions so leaves fit the canvas and parents are centered above
	    layoutMergeSortTreePositions();

	    // Draw connections first (behind the nodes)
	    renderMergeSortEdges(g, mergeSortRoot);
	    // Draw nodes on top
	    renderMergeSortNodes(g, mergeSortRoot);
	 }
	
	private void layoutMergeSortTreePositions() {
        if (mergeSortRoot == null) return;
        int maxDepth = calculateTreeDepth(mergeSortRoot);
        double cw = mergeSortCanvas.getWidth();
        double ch = mergeSortCanvas.getHeight();
        double leftMargin = 60, rightMargin = 60, topMargin = 70, bottomMargin = 60;
        double availableWidth = Math.max(100, cw - leftMargin - rightMargin);
        double verticalSpacing = maxDepth > 1 ? (ch - topMargin - bottomMargin) / (maxDepth - 1) : 0;

        // Set Y for all nodes based on level
        setPosYByLevel(mergeSortRoot, topMargin, verticalSpacing);

        // Collect leaves in left-to-right order
        List<MergeSortTreeNode> leaves = new ArrayList<>();
        collectLeavesInOrder(mergeSortRoot, leaves);
        int n = Math.max(1, leaves.size());
        double step = availableWidth / n;
        // Place leaves evenly across available width
        for (int i = 0; i < leaves.size(); i++) {
            leaves.get(i).posX = leftMargin + (i + 0.5) * step;
        }
        // Compute parent X as average of children
        assignParentX(mergeSortRoot);
    }
	
	private int calculateTreeDepth(MergeSortTreeNode node) {
        if (node == null) return 0;
        if (node.isLeaf) return 1;
        
        int leftDepth = calculateTreeDepth(node.leftChild);
        int rightDepth = calculateTreeDepth(node.rightChild);
        return 1 + Math.max(leftDepth, rightDepth);
    }
	
	private void setPosYByLevel(MergeSortTreeNode node, double topMargin, double vspace) {
        if (node == null) return;
        node.posY = topMargin + node.level * vspace;
        setPosYByLevel(node.leftChild, topMargin, vspace);
        setPosYByLevel(node.rightChild, topMargin, vspace);
    }
	
	private void collectLeavesInOrder(MergeSortTreeNode node, List<MergeSortTreeNode> leaves) {
        if (node == null) return;
        if (node.leftChild != null) collectLeavesInOrder(node.leftChild, leaves);
        if (node.isLeaf) leaves.add(node);
        if (node.rightChild != null) collectLeavesInOrder(node.rightChild, leaves);
    }
	
	private void assignParentX(MergeSortTreeNode node) {
        if (node == null) return;
        assignParentX(node.leftChild);
        assignParentX(node.rightChild);
        if (!node.isLeaf) {
            if (node.leftChild != null && node.rightChild != null) {
                node.posX = (node.leftChild.posX + node.rightChild.posX) / 2.0;
            } 
            else if (node.leftChild != null) {
                node.posX = node.leftChild.posX;
            } 
            else if (node.rightChild != null) {
                node.posX = node.rightChild.posX;
            }
        }
    }
	
	private void renderMergeSortEdges(GraphicsContext g, MergeSortTreeNode node) {
        if (node == null) return;
        
        g.setStroke(Color.web("#4a90e2"));
        g.setLineWidth(3.5);
        
        if (node.leftChild != null) {
            g.strokeLine(node.posX, node.posY + 18, node.leftChild.posX, node.leftChild.posY - 18);
            renderMergeSortEdges(g, node.leftChild);
        }
        
        if (node.rightChild != null) {
            g.strokeLine(node.posX, node.posY + 18, node.rightChild.posX, node.rightChild.posY - 18);
            renderMergeSortEdges(g, node.rightChild);
        }
    }
	
	private void renderMergeSortNodes(GraphicsContext g, MergeSortTreeNode node) {
        if (node == null) return;
        // Draw left subtree nodes first so parent renders on top visually
        renderMergeSortNodes(g, node.leftChild);
        renderMergeSortNodes(g, node.rightChild);

        // Determine node color based on state
        Color fillColor = Color.web("#2c3e50"); // default
        Color borderColor = Color.web("#00ffff");
        if (node.isDividing && node == currentNode) {
            fillColor = Color.web("#f39c12");
            borderColor = Color.web("#ffffff");
        } 
        else if (node.isMerging && node == currentNode) {
            fillColor = Color.web("#e74c3c");
            borderColor = Color.web("#ffffff");
        } 
        else if (node.isCompleted) {
            // Keep only the root in green per earlier requirement; others use theme-friendly cyan/blue
            fillColor = (node == mergeSortRoot) ? Color.web("#27ae60") : Color.web("#00bcd4");
        } 
        else if (node.isDividing) {
            fillColor = Color.web("#95a5a6");
        }

	    // Dynamic node size based on text width so all elements remain visible
	    String arrayText = node.array.toString(); // full content, no truncation
	    Font nodeFont = Font.font("SF Pro Text", FontWeight.SEMI_BOLD, 11);
	    javafx.scene.text.Text measure = new javafx.scene.text.Text(arrayText);
	    measure.setFont(nodeFont);
	    double textWidth = measure.getLayoutBounds().getWidth();
	    double paddingX = 26; // horizontal padding inside the box
	    double minWidth = 60; // for single element nodes
	    double nodeWidth = Math.max(minWidth, textWidth + paddingX);
	    double nodeHeight = 36;

        // Draw node at computed position
        double x = node.posX, y = node.posY;
        // Pulsing glow behind the current node
        if (node == currentNode) {
            double t = 0.5 * (Math.sin(mergePulse) + 1.0); // 0..1
            double glowExpand = 10 + 10 * t;
            double glowW = nodeWidth + glowExpand;
            double glowH = nodeHeight + glowExpand;
            g.setFill(Color.web("#00ffff", 0.18 + 0.12 * t));
            g.fillRoundRect(x - glowW/2, y - glowH/2, glowW, glowH, 12, 12);
            g.setStroke(Color.web("#00ffff", 0.45));
            g.setLineWidth(1.5 + 1.5 * t);
            g.strokeRoundRect(x - glowW/2, y - glowH/2, glowW, glowH, 12, 12);
        }
        g.setFill(fillColor);
        g.fillRoundRect(x - nodeWidth/2, y - nodeHeight/2, nodeWidth, nodeHeight, 8, 8);
        g.setStroke(borderColor);
        g.setLineWidth(node == currentNode ? 3 : 2);
        g.strokeRoundRect(x - nodeWidth/2, y - nodeHeight/2, nodeWidth, nodeHeight, 8, 8);

        // Draw text
        g.setFill(Color.WHITE);
        g.setFont(nodeFont);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(javafx.geometry.VPos.CENTER);
        g.fillText(arrayText, x, y);
    }
	
	private void clearMergingFlags(MergeSortTreeNode node) {
        if (node == null) return;
        node.isMerging = false;
        clearMergingFlags(node.leftChild);
        clearMergingFlags(node.rightChild);
    }
	
	private MergeAction findCorrespondingAction(MergeSortStep step) {
        // This is a simplified approach - in a real implementation you might need more sophisticated matching
        // For now, we'll create the action based on the step information
        if (step.activeNode == null) return null;
        
        // Determine action type based on the explanation text
        if (step.explanation.contains("Visiting leaf")) {
            return new MergeAction(MergeAction.Type.VISIT, step.activeNode);
        } else if (step.explanation.contains("Merging")) {
            return new MergeAction(MergeAction.Type.MERGE, step.activeNode);
        }
        return null;
    }
	
	private void toggleMergeSortPause() {
        if (!mergeSortRunning) return;
        
        mergeSortPaused = !mergeSortPaused;
        if (mergeSortPaused) {
            pauseResumeMergeSortBtn.setText("RESUME");
            mergeSortStatusLabel.setText("Visualization paused");
        } else {
            pauseResumeMergeSortBtn.setText("PAUSE");
            // Resume with current step's status
            if (currentMergeSortStepIndex >= 0 && currentMergeSortStepIndex < allMergeSortSteps.size()) {
                MergeSortStep currentStep = allMergeSortSteps.get(currentMergeSortStepIndex);
                mergeSortStatusLabel.setText(currentStep.statusText);
            }
        }
    }
	
	private void goToPreviousMergeSortStep() {
        if (!mergeSortRunning || currentMergeSortStepIndex <= 0) return;
        
        // Go back one step
        currentMergeSortStepIndex--;
        
        // Reset the visualization state
        resetVisualizationState();
        
        // Execute all steps up to the previous step to rebuild the correct state
        for (int i = 0; i <= currentMergeSortStepIndex; i++) {
            executeStepForReplay(i);
        }
        
        // Update UI to reflect current state
        if (currentMergeSortStepIndex >= 0 && currentMergeSortStepIndex < allMergeSortSteps.size()) {
            MergeSortStep currentStep = allMergeSortSteps.get(currentMergeSortStepIndex);
            mergeSortStatusLabel.setText("Previous step: " + currentStep.statusText);
        }
        
        drawMergeSortTree();
    }
	
	private void resetVisualizationState() {
        // Reset all node states
        resetNodeStates(mergeSortRoot);
        currentNode = null;
        
        // Clear explanation area and rebuild it
        if (mergeSortExplanationArea != null) {
            mergeSortExplanationArea.clear();
            updateMergeSortExplanation("MERGE SORT VISUALIZATION STARTED\n" +
                "Original array: " + originalMergeArray + "\n" +
                "Phase 1: DIVIDE - Breaking array into single elements\n" +
                "Phase 2: MERGE - Combining sorted subarrays\n\n");
        }
    }
	
	private void resetNodeStates(MergeSortTreeNode node) {
        if (node == null) return;
        
        node.isDividing = false;
        node.isMerging = false;
        node.isCompleted = false;
        
        // Reset array to original state for this node range
        node.array = new ArrayList<>(originalMergeArray.subList(node.left, node.right + 1));
        
        resetNodeStates(node.leftChild);
        resetNodeStates(node.rightChild);
    }
	
	private void executeStepForReplay(int stepIndex) {
        if (stepIndex < 0 || stepIndex >= allMergeSortSteps.size()) return;
        
        MergeSortStep step = allMergeSortSteps.get(stepIndex);
        currentPhase = step.phase;
        
        if (step.phase.equals("DIVIDE")) {
            currentNode = step.activeNode;
            if (currentNode != null) {
                currentNode.isDividing = true;
            }
            updateMergeSortExplanation(step.explanation);
        } 
        else if (step.phase.equals("TRANSITION")) {
            updateMergeSortExplanation(step.explanation);
        } 
        else if (step.phase.equals("MERGE")) {
            currentNode = step.activeNode;
            
            if (currentNode != null) {
                MergeAction correspondingAction = findCorrespondingAction(step);
                if (correspondingAction != null) {
                    if (correspondingAction.type == MergeAction.Type.VISIT) 
                        currentNode.isMerging = true;
                    else { // MERGE
                        if (currentNode.leftChild != null && currentNode.rightChild != null) {
                            List<Integer> merged = mergeTwoArrays(currentNode.leftChild.array, currentNode.rightChild.array);
                            currentNode.array = merged;
                        }
                        currentNode.isCompleted = true;
                    }
                }
            }
            
            updateMergeSortExplanation(step.explanation);
        } 
        else if (step.phase.equals("COMPLETE")) {
            if (mergeSortRoot != null) mergeSortRoot.isCompleted = true;
            currentNode = null;
            updateMergeSortExplanation(step.explanation);
        }
    }
}

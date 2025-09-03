package algobot;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.*;

public class SortingManager {
	private Timeline sortingTimeline;
	private List<Integer> sortingValues = new ArrayList<>();
	private String activeSortingAlgo = null;
    private int si = 0, sj = 0; // generic indices
    private int in_max = 0, in_min = 100; // flag indices
    private int passOrOuter = 0; // bubble pass / selection outer / insertion outer
    private int auxIndex = -1;
    private boolean flag = false;
    private boolean sortingRunning = false;
    private boolean sortingFinished = false;
    private boolean barEditingEnabled = false;
    private TextField sortingInputField; // legacy free-form input (deprecated in new workflow)
    private TextField elementCountField; // new: number of elements
    private TextField maxElementField;
    private Button bubbleBtn, insertionBtn, selectionBtn, quickBtn, radixBtn;
    private Button selectedAlgoButton; // remember which algorithm was chosen
    private Button pauseResumeBtn;
    private TextArea explanationArea;
    private String lastExplanation = "";
    private Canvas sortingCanvas;
    private Button resetArrayButton;
    private javafx.scene.control.Slider speedSlider; // speed control
    private Label speedValueLabel; // shows ms value
    private int speedMillis = 1500;
    private int declaredMaxElement = 100;
    private boolean paused = false;
    private Set<Integer> sortedSet = new HashSet<>();
    private Set<Integer> specialSet = new HashSet<>();
    private List<SortStep> precomputedSteps = null;
    private int preStepIndex = 0;
    private boolean usingPrecomputed = false;
	
	public void createSortingPage() {
    	common.sortingContainer = new VBox(15);
    	common.sortingContainer.setAlignment(Pos.TOP_CENTER);
    	// Increase top padding to move back button and title lower from the top border
    	common.sortingContainer.setPadding(new Insets(80, 20, 20, 20));
        // Match graph page dark gradient theme
        LinearGradient gradient = new LinearGradient(0, 0, 1, 1, true, null,
            new Stop(0, Color.web("#0c0c0c")),
            new Stop(0.3, Color.web("#141e30")),
            new Stop(0.6, Color.web("#243b55")),
            new Stop(1, Color.web("#0f051d"))
        );
        
        common.sortingContainer.setBackground(new Background(new BackgroundFill(gradient, null, null)));

        HBox title = ButtonManager.createTitleWithBackButton("SORTING VISUALIZATION");
        // Override back button action to also clear sorting state
        if (!title.getChildren().isEmpty() && title.getChildren().get(0) instanceof Button b) {
            b.setOnAction(e -> {
                clearSortingState(); // will also clear explanation
                common.transitionToPage(common.homeContainer);
            });
        }
        
        common.sortingContainer.getChildren().add(title);

        // New parameter-driven input section (count + max). User then drags bars vertically.
	    VBox inputSection = new VBox(6);
	    inputSection.setAlignment(Pos.CENTER);
	    Label prompt = new Label("Enter count & max, create bars, drag to adjust heights, then run.");
	    prompt.setTextFill(Color.WHITE);
	    prompt.setFont(Font.font("SF Pro Text", FontWeight.MEDIUM, 14));
	    elementCountField = new TextField();
	    elementCountField.setPromptText("Count");
	    elementCountField.setPrefWidth(90);
	    elementCountField.setStyle("-fx-background-radius:8; -fx-background-color: rgba(255,255,255,0.12); -fx-text-fill: white; -fx-padding:6 10; -fx-border-color: rgba(0,255,255,0.55); -fx-border-radius:8; -fx-border-width:1; -fx-font-size:13px; -fx-font-weight:600;");
	    maxElementField = new TextField();
	    maxElementField.setPromptText("Max");
	    maxElementField.setPrefWidth(90);
	    maxElementField.setStyle("-fx-background-radius:8; -fx-background-color: rgba(255,255,255,0.12); -fx-text-fill: white; -fx-padding:6 10; -fx-border-color: rgba(0,255,255,0.55); -fx-border-radius:8; -fx-border-width:1; -fx-font-size:13px; -fx-font-weight:600;");
	    Button createBarsBtn = ButtonManager.createStyledButton("CREATE BARS", "#00ffff");
	    resetArrayButton = ButtonManager.createStyledButton("RESET", "#00ffff");
	    resetArrayButton.setDisable(false);
	    HBox params = new HBox(12, new Label("n:"), elementCountField, new Label("max:"), maxElementField, createBarsBtn, resetArrayButton);
	    params.setAlignment(Pos.CENTER);
	    params.getChildren().forEach(n -> { if(n instanceof Label l){ l.setTextFill(Color.web("#e0ffff")); l.setFont(Font.font("SF Pro Text", 12)); }});
	    // Legacy free-form hidden but retained (optional future toggle)
	    sortingInputField = new TextField();
	    sortingInputField.setVisible(false);
	    inputSection.getChildren().addAll(prompt, params);
	    common.sortingContainer.getChildren().add(inputSection);

        // MAIN VISUAL + EXPLANATION SPLIT AREA
        // Left: canvas + controls stacked. Right: large explanation panel.
        HBox mainSplit = new HBox(18);
        mainSplit.setAlignment(Pos.TOP_CENTER);
        mainSplit.setFillHeight(true);

        // Left side (visualization + controls)
        VBox leftPane = new VBox(8);
        leftPane.setAlignment(Pos.TOP_CENTER);

        // Canvas (reduced height to make space for buttons)
        sortingCanvas = new Canvas(680, 350);
        StackPane canvasWrapper = new StackPane(sortingCanvas);
        canvasWrapper.setPadding(new Insets(8));
        canvasWrapper.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-background-radius:12; -fx-border-radius:12; -fx-border-color: rgba(255,255,255,0.15); -fx-border-width:1;");
        leftPane.getChildren().add(canvasWrapper);

        // Status label (smaller font)
        common.sortingStatusLabel = new Label("Awaiting input...");
        common.sortingStatusLabel.setTextFill(Color.web("#cccccc"));
        common.sortingStatusLabel.setFont(Font.font("SF Pro Text", FontWeight.NORMAL, 11));
        leftPane.getChildren().add(common.sortingStatusLabel);

        // Control row (speed + pause) with smaller fonts
        HBox controlRow = new HBox(12);
        controlRow.setAlignment(Pos.CENTER);
	    // Expanded range for slower maximum speed (higher ms means slower)
	    speedSlider = new javafx.scene.control.Slider(300, 5000, speedMillis);
        speedSlider.setPrefWidth(200);
        speedSlider.setShowTickMarks(false);
        speedSlider.setStyle("-fx-padding:2 0 0 0;");
        speedValueLabel = new Label("Speed: " + speedMillis + "ms");
        speedValueLabel.setTextFill(Color.web("#dddddd"));
        speedValueLabel.setFont(Font.font("SF Pro Text", 11));
        pauseResumeBtn = ButtonManager.createStyledButton("PAUSE", "#00ffff");
        pauseResumeBtn.setFont(Font.font("SF Pro Text", 11));
        pauseResumeBtn.setDisable(true);
        controlRow.getChildren().addAll(speedSlider, speedValueLabel, pauseResumeBtn);
        leftPane.getChildren().add(controlRow);

        // Algorithm buttons responsive grid (moved higher with reduced spacing)
        FlowPane algoPane = new FlowPane();
        algoPane.setHgap(6);
        algoPane.setVgap(6);
        algoPane.setAlignment(Pos.CENTER);
        algoPane.setPrefWrapLength(660); // wrap within canvas width
        algoPane.setPadding(new Insets(5, 0, 0, 0)); // minimal top padding
        bubbleBtn = ButtonManager.createStyledButton("BUBBLE", "#00ffff");
        insertionBtn = ButtonManager.createStyledButton("INSERTION", "#00ffff");
        selectionBtn = ButtonManager.createStyledButton("SELECTION", "#00ffff");
        quickBtn = ButtonManager.createStyledButton("QUICK", "#00ffff");
        radixBtn = ButtonManager.createStyledButton("RADIX", "#00ffff");
        for (Button bAlgo : List.of(bubbleBtn,insertionBtn,selectionBtn,quickBtn,radixBtn)) {
            bAlgo.setDisable(true);
            bAlgo.setFont(Font.font("SF Pro Text", 11));
            bAlgo.setPrefHeight(28); // slightly smaller height
            bAlgo.setPrefWidth(95); // slightly smaller width
        }
        algoPane.getChildren().addAll(bubbleBtn, insertionBtn, selectionBtn, quickBtn, radixBtn);
        leftPane.getChildren().add(algoPane);

	    VBox rightPane = new VBox(8);
	    rightPane.setAlignment(Pos.TOP_LEFT);
	    // Match sorting explanation panel size
	    rightPane.setPrefWidth(340);
        Label explLabel = new Label("STEP EXPLANATION");
        explLabel.setTextFill(Color.web("#66d9ff"));
        explLabel.setFont(Font.font("SF Pro Text", FontWeight.SEMI_BOLD, 12));
        explanationArea = new javafx.scene.control.TextArea();
        explanationArea.setEditable(false);
        explanationArea.setWrapText(true);
        explanationArea.setPrefWidth(340);
        explanationArea.setPrefHeight(520); // larger vertical space
        explanationArea.setStyle(
            "-fx-control-inner-background: rgba(15,18,26,0.92);" +
            "-fx-background-color: rgba(15,18,26,0.92);" +
            "-fx-text-fill: #f5f7fa;" +
            "-fx-highlight-fill: #3d6ef7;" +
            "-fx-highlight-text-fill: white;" +
            "-fx-font-family: 'SF Pro Text';" +
            "-fx-font-size: 16px;" + // larger font per request
            "-fx-prompt-text-fill: rgba(255,255,255,0.75);" +
            "-fx-background-radius:12; -fx-border-radius:12;" +
            "-fx-border-color: rgba(255,255,255,0.25); -fx-border-width:1;" +
            "-fx-padding:8;"
        );
        explanationArea.setPromptText("Step-by-step explanation will appear here...");
        VBox.setVgrow(explanationArea, Priority.ALWAYS);
        rightPane.getChildren().addAll(explLabel, explanationArea);

        HBox.setHgrow(leftPane, Priority.ALWAYS);
        mainSplit.getChildren().addAll(leftPane, rightPane);
        common.sortingContainer.getChildren().add(mainSplit);

        createBarsBtn.setOnAction(e -> buildInteractiveArray());
        resetArrayButton.setOnAction(e -> {
            clearSortingState();
            drawBars(sortingCanvas, Collections.emptyList());
            common.sortingStatusLabel.setText("Reset. Enter count & max, then CREATE BARS.");
        });

        bubbleBtn.setOnAction(e -> startSortingVisualization("BUBBLE"));
        insertionBtn.setOnAction(e -> startSortingVisualization("INSERTION"));
        selectionBtn.setOnAction(e -> startSortingVisualization("SELECTION"));
        quickBtn.setOnAction(e -> startSortingVisualization("QUICK"));
        radixBtn.setOnAction(e -> startSortingVisualization("RADIX"));

        pauseResumeBtn.setOnAction(e -> togglePause());
        // sound toggle removed
        speedSlider.valueProperty().addListener((obs,o,n)->{
            speedMillis = n.intValue();
            speedValueLabel.setText("Speed: " + speedMillis + "ms");
            if (sortingRunning && !paused) updateTimelineSpeed();
        });
        
        flag = false;
        
        // Mouse interactions for draggable bar height editing (only before starting visualization)
        sortingCanvas.setOnMousePressed(e -> {
            if((!barEditingEnabled || sortingRunning) && !paused) return;
            int idx = barIndexAt(e.getX());
            if(idx>=0) updateBarValueFromY(idx, e.getY());
            
            if(paused) {
            	flag = true; 
            	in_min = Math.min(in_min, idx);
            }
        });
        sortingCanvas.setOnMouseDragged(e -> {
        	if((!barEditingEnabled || sortingRunning) && !paused) return;
            int idx = barIndexAt(e.getX());
            if(idx>=0) updateBarValueFromY(idx, e.getY());
            
            if(paused) {
            	flag = true; 
            	in_min = Math.min(in_min, idx);
            }
        });
    }
	
	private void clearSortingState() {
        if (sortingTimeline != null) sortingTimeline.stop();
        sortingValues.clear(); 
        
        activeSortingAlgo = null; 
        sortingRunning = false; 
        sortingFinished = false; 
        barEditingEnabled = false;
        si = sj = passOrOuter = 0; 
        auxIndex = -1;
        
        if (sortingInputField != null) sortingInputField.clear();
        if (elementCountField != null) elementCountField.clear();
        if (maxElementField != null) maxElementField.clear();
        
        enableAlgoButtons(false);
        
        if (common.sortingStatusLabel != null) common.sortingStatusLabel.setText("Awaiting input...");
        if (pauseResumeBtn != null) { 
        	pauseResumeBtn.setDisable(true); 
        	pauseResumeBtn.setText("PAUSE"); 
        }
        
        clearExplanation();
        
        if (sortingCanvas != null) {
            GraphicsContext g = sortingCanvas.getGraphicsContext2D();
            g.clearRect(0,0,sortingCanvas.getWidth(), sortingCanvas.getHeight());
        }
        // restore algorithm button styles
        if (selectedAlgoButton != null) {
            Object original = selectedAlgoButton.getUserData();
            if (original instanceof String s) selectedAlgoButton.setStyle(s);
            selectedAlgoButton = null;
        }
    }
	
	private void enableAlgoButtons(boolean enable) {
        for (Button b : List.of(bubbleBtn,insertionBtn,selectionBtn,quickBtn,radixBtn)) {
            if (b != null) {
                if (selectedAlgoButton != null && b == selectedAlgoButton) continue; // keep locked
                b.setDisable(!enable);
            }
        }
    }
	
	private void clearExplanation() {
        if (explanationArea != null) explanationArea.clear();
        lastExplanation = "";
    }
	
	private void buildInteractiveArray(){
        if (sortingRunning) return;
        
        Integer count = parsePositiveInt(elementCountField.getText());
        Integer maxv = parsePositiveInt(maxElementField.getText());
        
        if(count==null){ common.sortingStatusLabel.setText("Invalid count"); return; }
        if(maxv==null){ common.sortingStatusLabel.setText("Invalid max"); return; }
        if(count<2){ common.sortingStatusLabel.setText("Need at least 2 elements"); return; }
        if(count>120){ common.sortingStatusLabel.setText("Count too large (<=120)"); return; }
        if(maxv<2){ common.sortingStatusLabel.setText("Max must be >=2"); return; }
        if(maxv>100000){ common.sortingStatusLabel.setText("Max too large"); return; }
        
        declaredMaxElement = maxv;
        sortingValues.clear();
        
        int mid = Math.max(1, maxv/2);
        for(int i=0;i<count;i++) sortingValues.add(mid);
        
        barEditingEnabled = true;
        sortingFinished = false; 
        activeSortingAlgo = null; 
        paused = false; 
        selectedAlgoButton = null;
        
        drawBars(sortingCanvas, sortingValues);
        enableAlgoButtons(true);
        common.sortingStatusLabel.setText("Bars created. Drag to set heights (1.."+maxv+"). Choose algorithm to start.");
        if (pauseResumeBtn != null) pauseResumeBtn.setDisable(true);
    }
	
	// ----------- Interactive bar creation helpers -----------
    private Integer parsePositiveInt(String s){
        if(s==null || s.isBlank()) 
        	return null;
        
        try { 
        	int v = Integer.parseInt(s.trim()); 
        	return v>0? v: null; 
        } 
        catch(Exception ex){ 
        	return null; 
        }
    }
    
    private void drawBars(Canvas canvas, List<Integer> values) {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0,0,w,h);
        if (values.isEmpty()) return;
        int max = (barEditingEnabled || paused)? declaredMaxElement : values.stream().max(Integer::compareTo).orElse(1);
        int n = values.size();
        double gap = Math.max(2, w * 0.01); // small gap relative to width
        double totalGap = gap * (n + 1);
        double barWidth = Math.max(6, (w - totalGap) / n);
        double usableHeight = h * 0.9; // leave top margin
        double baseY = h * 0.95;
        for (int i = 0; i < n; i++) {
            int val = values.get(i);
            double normalized = max == 0 ? 0 : (double) val / max;
            double barHeight = normalized * usableHeight;
            double x = gap + i * (barWidth + gap);
            double y = baseY - barHeight;
            // Determine color based on algorithm highlighting
            Color top = Color.web("#1e90ff");
            Color bottom = Color.web("#104e8b");
            if (sortingRunning && !sortingFinished) {
                if (activeSortingAlgo != null) {
                    if (activeSortingAlgo.equals("BUBBLE")) {
                        int sortedFrom = values.size() - passOrOuter; // passOrOuter holds current pass count
                        if (i >= sortedFrom) { top = Color.web("#27ae60"); bottom = Color.web("#1e8449"); }
                    }
                    if (activeSortingAlgo.equals("SELECTION")) {
                        if (i < passOrOuter) { top = Color.web("#27ae60"); bottom = Color.web("#1e8449"); }
                        if (i == auxIndex) { top = Color.web("#f1c40f"); bottom = Color.web("#d4ac0d"); }
                    }
                    if (activeSortingAlgo.equals("INSERTION")) {
                        if (i < passOrOuter) { top = Color.web("#27ae60"); bottom = Color.web("#1e8449"); }
                        if (i == sj) { top = Color.web("#e74c3c"); bottom = Color.web("#c0392b"); }
                        if (i == passOrOuter) { top = Color.web("#f39c12"); bottom = Color.web("#d68910"); }
                    }
                }
                if (i == si || i == sj) { // comparison highlighting overrides
                    top = Color.web("#e74c3c"); bottom = Color.web("#c0392b");
                }
            }
            javafx.scene.paint.LinearGradient lg = new javafx.scene.paint.LinearGradient(0,0,0,1,true, null,
                new Stop(0, top),
                new Stop(1, bottom)
            );
            g.setFill(lg);
            g.fillRoundRect(x, y, barWidth, barHeight, 6, 6);
            g.setStroke(Color.web("#ffffff30"));
            g.setLineWidth(1);
            g.strokeRoundRect(x, y, barWidth, barHeight, 6, 6);
            // Always show value (adjust placement for tiny bars)
            if (barWidth >= 10) {
                g.setFont(Font.font("SF Pro Text", 10));
                String valStr = String.valueOf(val);
                double tw = valStr.length() * 6.0; // approximate width
                double textY;
                if (barHeight > 18) {
                    textY = y - 3; // above bar for taller bars
                    g.setFill(Color.WHITE);
                } else if (barHeight > 8) {
                    // Place inside the bar (if there is modest space)
                    textY = y + 12;
                    g.setFill(Color.WHITE);
                } else {
                    // Very small bar: draw just above base with contrasting color for visibility
                    textY = baseY + 12; // below bar area but within canvas
                    g.setFill(Color.web("#ffd54f"));
                }
                // Ensure text does not overflow canvas bottom
                double maxY = canvas.getHeight() - 2;
                if (textY > maxY) textY = maxY;
                g.fillText(valStr, x + (barWidth - tw)/2, textY);
            }
        }
    }
    
    private void startSortingVisualization(String algo) {
        if (sortingValues.isEmpty()) { common.sortingStatusLabel.setText("Create bars first."); return; }
        if (activeSortingAlgo != null) return; // already chosen
        if (sortingTimeline != null) sortingTimeline.stop();
        
        activeSortingAlgo = algo;
        lockAndHighlightAlgorithmButton(algo);
        
        si = 0; sj = 0; 
        passOrOuter = 0; 
        auxIndex = -1; 
        sortingFinished = false; 
        sortingRunning = true; 
        paused = false;
        barEditingEnabled = false; // lock editing
        
        sortedSet.clear(); 
        specialSet.clear(); 
        precomputedSteps = null; 
        usingPrecomputed = false; 
        preStepIndex = 0;
        
        clearExplanation();
        common.sortingStatusLabel.setText(algo + " sort started.");
        
        switch (algo) {
            case "BUBBLE" -> updateExplanation("Bubble Sort: adjacent elements compared; largest values move to the end each pass.");
            case "INSERTION" -> updateExplanation("Insertion Sort: build sorted prefix; insert current element into correct spot within prefix.");
            case "SELECTION" -> updateExplanation("Selection Sort: scan remaining segment to select minimum and place it at current position.");
            case "QUICK" -> updateExplanation("Quick Sort: choose pivot, partition around it, recurse on subarrays (shown via captured steps).");
            case "RADIX" -> updateExplanation("Radix Sort: distribute numbers by digit (LSD first) into buckets, recombine; repeats per digit place.");
        }
        
        if (algo.equals("INSERTION")) { passOrOuter = 1; sj = passOrOuter - 1; }
        if (algo.equals("SELECTION")) { passOrOuter = 0; auxIndex = 0; sj = passOrOuter + 1; }
        if (algo.equals("QUICK") || algo.equals("RADIX")) {
            generatePrecomputedSteps(algo);
            usingPrecomputed = precomputedSteps != null;
            if (usingPrecomputed && precomputedSteps.isEmpty()) sortingFinished = true;
        }
        
        runSortingTimeline();
        pauseResumeBtn.setDisable(false);
        pauseResumeBtn.setText("PAUSE");
    }
    
    private void lockAndHighlightAlgorithmButton(String algo) {
        List<Button> buttons = List.of(bubbleBtn,insertionBtn,selectionBtn,quickBtn,radixBtn);
        for (Button b : buttons) {
            if (b == null) continue;
            if (b.getUserData() == null) b.setUserData(b.getStyle());
            if (b.getText().equalsIgnoreCase(algo)) {
                selectedAlgoButton = b;
                b.setStyle(b.getStyle() + ";-fx-border-color:#ffffff;-fx-border-width:2;-fx-effect:dropshadow(gaussian,#ffffffaa,15,0,0,0);-fx-font-weight:900; ");
            }
            b.setDisable(true);
        }
    }
    
    private static class SortStep {
        List<Integer> values; // snapshot
        int i = -1; // comparison/primary index
        int j = -1; // secondary index
        int aux = -1; // auxiliary index (e.g., pivot)
        Set<Integer> sorted = new HashSet<>();
        Set<Integer> special = new HashSet<>(); // e.g., pivot, bucket elements
        String actionType; // compare, swap, write, bucket, pivot-fixed, merge-done
        String explanation;
        SortStep copyWithValues(List<Integer> vals){
            SortStep s = new SortStep();
            s.values = new ArrayList<>(vals);
            s.i = i; s.j = j; s.aux = aux;
            s.sorted = new HashSet<>(sorted);
            s.special = new HashSet<>(special);
            s.actionType = actionType; s.explanation = explanation;
            return s;
        }
    }
    
    private void updateExplanation(String text) {
        if (explanationArea == null) return;
        if (text == null) return;
        if (text.isBlank()) return; // ignore blank so history stays
        if (text.equals(lastExplanation)) return;
        String existing = explanationArea.getText();
        String combined = existing.isBlank()? text : existing + (existing.endsWith("\n")? "" : "\n") + text;
        String[] lines = combined.split("\n");
        if (lines.length > 60) {
            lines = java.util.Arrays.copyOfRange(lines, lines.length-60, lines.length);
            combined = String.join("\n", lines);
        }
        if (combined.length() > 12000) combined = combined.substring(combined.length()-12000);
        explanationArea.setText(combined);
        explanationArea.positionCaret(explanationArea.getText().length());
        lastExplanation = text;
    }
    
    private void generatePrecomputedSteps(String algo) {
        List<Integer> base = new ArrayList<>(sortingValues);
        precomputedSteps = new ArrayList<>();
        switch (algo) {
            case "QUICK" -> generateQuickSteps(base);
            case "RADIX" -> generateRadixSteps(base);
        }
    }
    
    private void generateQuickSteps(List<Integer> arr) {
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{0, arr.size()-1});
        while(!stack.isEmpty()) {
            int[] seg = stack.pop();
            int l = seg[0], r = seg[1];
            if (l >= r) {
                if (l==r) captureStep(arr,-1,-1,-1,indexRangeSet(l,l),Set.of(),"pivot-fixed","Element at "+l+" fixed.");
                continue;
            }
            int pivot = arr.get(r);
            captureStep(arr,-1,-1,r,indexRangeSet(0,-1),Set.of(r),"pivot","Choose pivot index "+r+" (value="+pivot+")");
            int i = l - 1;
            for (int j = l; j < r; j++) {
                captureStep(arr,j,r,r,new HashSet<>(),Set.of(j,r),"compare","Compare arr["+j+"] with pivot");
                if (arr.get(j) <= pivot) {
                    i++;
                    if (i != j) Collections.swap(arr, i, j);
                    captureStep(arr,i,j,r,new HashSet<>(),Set.of(i,r),"swap","Place element <= pivot at position "+i);
                }
            }
            if (i+1 != r) Collections.swap(arr, i+1, r);
            captureStep(arr,i+1,r,r,indexRangeSet(i+1,i+1),Set.of(),"pivot-fixed","Pivot positioned at index "+(i+1));
            int p = i+1;
            // push larger segment first to reduce stack depth
            int leftSize = p-1 - l; int rightSize = r - (p+1);
            if (leftSize > rightSize) {
                if (l < p-1) stack.push(new int[]{l,p-1});
                if (p+1 < r) stack.push(new int[]{p+1,r});
            } else {
                if (p+1 < r) stack.push(new int[]{p+1,r});
                if (l < p-1) stack.push(new int[]{l,p-1});
            }
        }
        captureStep(arr,-1,-1,-1,indexRangeSet(0,arr.size()-1),Set.of(),"done","Quick sort complete.");
    }

    // RADIX SORT (will be replaced later with improved version after cleanup)
    private void generateRadixSteps(List<Integer> arr) {
        if (arr.isEmpty()) return;
        int max = Collections.max(arr);
        for (int exp = 1; max/exp > 0; exp *= 10) {
            int[] count = new int[10];
            for (int v : arr) count[(v/exp)%10]++;
            for (int i=1;i<10;i++) count[i]+=count[i-1];
            int n = arr.size();
            int[] output = new int[n];
            for (int i=n-1;i>=0;i--) {
                int digit = (arr.get(i)/exp)%10;
                output[--count[digit]] = arr.get(i);
                captureStep(Arrays.stream(output).boxed().toList(), i, -1, -1, new HashSet<>(), Set.of(),"bucket","Placing value by digit="+digit+" (exp="+exp+")");
            }
            for (int i=0;i<n;i++) arr.set(i, output[i]);
            captureStep(arr,-1,-1,-1,indexRangeSet(0,n-1),Set.of(),"pass","After processing digit place exp="+exp);
        }
        captureStep(arr,-1,-1,-1,indexRangeSet(0,arr.size()-1),Set.of(),"done","Radix sort complete.");
    }
    
    private Set<Integer> indexRangeSet(int l, int r){
        Set<Integer> s = new HashSet<>();
        for(int x=l;x<=r;x++) s.add(x);
        return s;
    }
    
    private void captureStep(List<Integer> arr, int i, int j, int auxIdx, Set<Integer> sorted, Set<Integer> special, String action, String expl) {
        SortStep st = new SortStep();
        st.values = new ArrayList<>(arr);
        st.i = i; st.j = j; st.aux = auxIdx; st.sorted = new HashSet<>(sorted); st.special = new HashSet<>(special); st.actionType = action; st.explanation = expl;
        precomputedSteps.add(st);
    }
    
    private void runSortingTimeline() {
        if (sortingTimeline != null) sortingTimeline.stop();
        sortingTimeline = new Timeline(new KeyFrame(javafx.util.Duration.millis(speedMillis), e -> {
            if (sortingFinished) {
                sortingTimeline.stop();
                sortingRunning = false;
                common.sortingStatusLabel.setText(activeSortingAlgo + " completed.");
                drawBars(sortingCanvas, sortingValues);
                return;
            }
            stepAlgorithm();
            drawBars(sortingCanvas, sortingValues);
        }));
        sortingTimeline.setCycleCount(Timeline.INDEFINITE);
        sortingTimeline.play();
    }
    
    private void stepAlgorithm() {
        if (usingPrecomputed) {
            if (precomputedSteps == null || preStepIndex >= precomputedSteps.size()) { sortingFinished = true; return; }
            SortStep st = precomputedSteps.get(preStepIndex++);
            sortingValues.clear(); sortingValues.addAll(st.values);
            si = st.i; sj = st.j; auxIndex = st.aux;
            sortedSet = st.sorted; specialSet = st.special;
            updateExplanation(st.explanation == null ? "" : st.explanation);
            // sound effect removed
            if (preStepIndex >= precomputedSteps.size()) sortingFinished = true;
            return;
        }
        switch (activeSortingAlgo) {
            case "BUBBLE" -> { stepBubble(); updateExplanation(dynamicExplanation()); }
            case "INSERTION" -> { stepInsertion(); updateExplanation(dynamicExplanation()); }
            case "SELECTION" -> { stepSelection(); updateExplanation(dynamicExplanation()); }
        }
    }

    private void stepBubble() {
    	if(flag) {
    		passOrOuter = 0;
    		si = Math.min(si, in_min);
    		
    		flag = false;
    		in_min = 100;
    	}
    	
    	passOrOuter = Math.max(passOrOuter, 0);
    	
        int n = sortingValues.size();
        if (passOrOuter >= n - 1) { sortingFinished = true; return; }
        if (si >= n - 1 - passOrOuter) {
            passOrOuter++;
            si = 0;
            return;
        }
        sj = si + 1;
        if (sortingValues.get(si) > sortingValues.get(sj)) {
            Collections.swap(sortingValues, si, sj);
        }
        si++;
    }

    private void stepInsertion() {
    	if(flag) {
    		passOrOuter = 0;
    		flag = false;
    	}
    	
    	passOrOuter = Math.max(passOrOuter, 0);
    	
        int n = sortingValues.size();
        if (passOrOuter >= n) { sortingFinished = true; return; }
        // sj acts as j scanning backwards
        if (sj >= 0 && sortingValues.get(sj) > sortingValues.get(sj + 1)) {
            Collections.swap(sortingValues, sj, sj + 1);
            sj--;
        } else {
            passOrOuter++;
            sj = passOrOuter - 1;
        }
        si = sj; // for highlight
    }

    private void stepSelection() {
    	if(flag) {
    		passOrOuter = 0;
    		flag = false;
    	}
    	
    	passOrOuter = Math.max(passOrOuter, 0);
    	
        int n = sortingValues.size();
        if (passOrOuter >= n - 1) { sortingFinished = true; return; }
        // sj scans, auxIndex holds current min
        if (sj >= n) {
            // swap min into position passOrOuter
            if (auxIndex != passOrOuter) Collections.swap(sortingValues, auxIndex, passOrOuter);
            passOrOuter++;
            auxIndex = passOrOuter;
            sj = passOrOuter + 1;
            return;
        }
        if (sj < n) {
            if (sortingValues.get(sj) < sortingValues.get(auxIndex)) {
                auxIndex = sj;
            }
            sj++;
        }
        si = auxIndex; // highlight current min
    }
    
    private String dynamicExplanation() {
        return switch (activeSortingAlgo) {
            case "BUBBLE" -> "Bubble: comparing indices " + si + (sj < sortingValues.size()? (" and " + sj):"") + ", pass " + passOrOuter;
            case "INSERTION" -> "Insertion: building sorted prefix up to index " + passOrOuter + "; comparing shifting position at j=" + sj;
            case "SELECTION" -> "Selection: scanning for min in remaining; current min index=" + auxIndex + ", outer=" + passOrOuter;
            default -> "";
        };
    }
    
    private void togglePause() {
        if (sortingTimeline == null) return;
        if (paused) {
            sortingTimeline.play();
            pauseResumeBtn.setText("PAUSE");
        } else {
            sortingTimeline.pause();
            pauseResumeBtn.setText("RESUME");
        }
        paused = !paused;
    }
    
    private void updateTimelineSpeed() {
        if (sortingTimeline == null) return;
        boolean wasRunning = !paused;
        sortingTimeline.stop();
        runSortingTimeline();
        if (!wasRunning) sortingTimeline.pause();
    }
    
    private int barIndexAt(double x){
        if(sortingValues==null || sortingValues.isEmpty()) return -1;
        double w = sortingCanvas.getWidth();
        int n = sortingValues.size();
        double gap = Math.max(2, w * 0.01);
        double totalGap = gap * (n + 1);
        double barWidth = Math.max(6, (w - totalGap) / n);
        for(int i=0;i<n;i++){
            double bx = gap + i * (barWidth + gap);
            if(x >= bx && x <= bx + barWidth) return i;
        }
        return -1;
    }

    private void updateBarValueFromY(int index, double y){
        if(index<0 || index>=sortingValues.size()) return;
        double h = sortingCanvas.getHeight();
        double usableHeight = h * 0.9;
        double baseY = h * 0.95;
        y = Math.min(baseY, Math.max(baseY - usableHeight, y));
        double barHeight = baseY - y;
        int newVal = (int)Math.round((barHeight/usableHeight) * declaredMaxElement);
        newVal = Math.max(1, Math.min(declaredMaxElement, newVal));
        sortingValues.set(index, newVal);
        drawBars(sortingCanvas, sortingValues);
        common.sortingStatusLabel.setText("Editing index="+index+" value="+newVal);
    }
}

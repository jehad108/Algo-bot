package algobot;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.text.TextAlignment;
import javafx.util.Callback;
import javafx.scene.layout.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import java.util.*;

public class DataStructureManager {
	// Data Structure visualization components
    private TextArea dataStructureExplanationArea;
    private String lastDSExplanation = "";
    private Canvas dsCanvas;
    private ComboBox<String> dsSelector;
    private TextField dsValueField, dsIndexField, dsRightField; // value / index / right (for range queries)
    private HBox dsOperationButtonsBox;
    private enum DSKind { NONE, STACK, QUEUE, HEAP_MAX, HEAP_MIN, AVL, FENWICK, SEGMENT }
    private DSKind currentDSKind = DSKind.NONE;
    // Backing data structures
    private Deque<Integer> stackData = new ArrayDeque<>();
    private Deque<Integer> queueData = new ArrayDeque<>();
    private List<Integer> heapData = new ArrayList<>(); // array-based heap
    private int fenwickSize = 0; private int[] fenwickTree = null; private List<Integer> fenwickSource = new ArrayList<>();
    private int segSize = 0; private int[] segTree = null; private List<Integer> segSource = new ArrayList<>();
    
    private Slider dsSpeedSlider;
    
    private List<HeapAnimStep> heapAnimSteps = new ArrayList<>();
    private int heapAnimIndex = 0;
    private Timeline heapAnimTimeline;
    private boolean heapAnimating = false;
    private int heapHighlightA = -1, heapHighlightB = -1;
    private String currentHeapAction = null;
    
    private AVLNode avlRoot = null;
    private Timeline avlAnimationTimeline = null;
    private List<String> avlSteps = new ArrayList<>();
    private int avlStepIndex = 0;
    private AVLNode avlHighlightedNode = null;
    private List<AVLNode> avlRotationNodes = new ArrayList<>();
    
    private HBox currentHeapExtractOverlay; // reuse removal logic if multiple attempts
    private EventHandler<MouseEvent> outsideHeapExtractClickHandler;
    
    private static class AVLNode {
        int value;
        int height;
        AVLNode left, right;
        double x, y; // layout position
        boolean isAnimating = false;
        boolean isHighlighted = false;
        Color nodeColor = Color.web("#4ecdc4");
        
        AVLNode(int value) {
            this.value = value;
            this.height = 1;
        }
        
        int getBalance() {
            int leftHeight = (left != null) ? left.height : 0;
            int rightHeight = (right != null) ? right.height : 0;
            return leftHeight - rightHeight;
        }
        
        void updateHeight() {
            int leftHeight = (left != null) ? left.height : 0;
            int rightHeight = (right != null) ? right.height : 0;
            this.height = Math.max(leftHeight, rightHeight) + 1;
        }
    }
    
    private static class HeapAnimStep {
        enum Type { INSERT_PLACE, EXTRACT_START, TARGET, REPLACE, COMPARE, SWAP, REMOVE, DONE }
        Type type; int a; int b; String explanation;
        HeapAnimStep(Type t,int a,int b,String expl){ this.type=t; this.a=a; this.b=b; this.explanation=expl; }
    }
    
    public void clear() {
        currentDSKind = DSKind.NONE;
        stackData.clear();
        queueData.clear();
        heapData.clear();
        fenwickSize = 0;
        fenwickTree = null;
        fenwickSource.clear();
        segSize = 0;
        segTree = null;
        segSource.clear();
        if (dsSelector != null) {
            dsSelector.getSelectionModel().select("None");
        }
        
        stopAVLAnimation();
        avlSteps.clear();
        avlStepIndex = 0;
        
        clearDSExplanation();
        buildDSOperationButtons();
        updateDSFieldVisibility();
        redrawDS();
    }
    
	public void createDataStructurePage() {
    	common.dataStructureContainer = new VBox(12);
    	common.dataStructureContainer.setAlignment(Pos.TOP_CENTER);
    	common.dataStructureContainer.setPadding(new Insets(60, 20, 20, 20));
    	
        LinearGradient gradient = new LinearGradient(0, 0, 1, 1, true, null,
            new Stop(0, Color.web("#0c0c0c")),
            new Stop(0.3, Color.web("#141e30")),
            new Stop(0.6, Color.web("#243b55")),
            new Stop(1, Color.web("#0f051d"))
        );
        
        common.dataStructureContainer.setBackground(new Background(new BackgroundFill(gradient, null, null)));

        HBox title = ButtonManager.createTitleWithBackButton("DATA STRUCTURES");
        if (!title.getChildren().isEmpty() && title.getChildren().get(0) instanceof Button b) {
            b.setOnAction(e -> {
                // Stop any running animations and reset the UI before transitioning
                clear();
                common.transitionToPage(common.homeContainer);
            });
        }
        
        common.dataStructureContainer.getChildren().add(title);

        // Control bar: selector + fields
        HBox controlBar = new HBox(10);
        controlBar.setAlignment(Pos.CENTER_LEFT);
        controlBar.setPadding(new Insets(6,0,6,0));
        
	    dsSelector = new ComboBox<>();
	    dsSelector.getItems().addAll("None","Stack","Queue","Max Heap","Min Heap","AVL Tree","Fenwick Tree","Segment Tree");
	    dsSelector.getSelectionModel().select("None");
	    dsSelector.valueProperty().addListener((o,ov,nv)-> { changeDSKind(nv); redrawDS(); });
	    String dsBaseStyle = "-fx-background-color: linear-gradient(to bottom,#07141c,#0d2533);" +
	        "-fx-border-color:#00ffff33; -fx-border-radius:8; -fx-background-radius:8;" +
	        "-fx-text-fill:#e0ffff; -fx-padding:2 8; -fx-font-size:12px;" +
	        "-fx-font-family:'JetBrains Mono','Consolas','SF Pro Text';";
	    String dsHoverStyle = "-fx-background-color: linear-gradient(to bottom,#0a1d27,#123549);" +
	        "-fx-border-color:#00ffffaa; -fx-border-radius:8; -fx-background-radius:8;" +
	        "-fx-text-fill:#ffffff; -fx-padding:2 8; -fx-font-size:12px;" +
	        "-fx-font-family:'JetBrains Mono','Consolas','SF Pro Text'; -fx-effect:dropshadow(gaussian,#00ffff66,10,0,0,0);";
	    dsSelector.setStyle(dsBaseStyle);
	    dsSelector.setOnMouseEntered(e-> dsSelector.setStyle(dsHoverStyle));
	    dsSelector.setOnMouseExited(e-> dsSelector.setStyle(dsBaseStyle));
	    
	    // Force visible text color for selected value & dropdown cells
	    Callback<ListView<String>, ListCell<String>> dsCellFactory = new Callback<>() {
        @Override public ListCell<String> call(ListView<String> lv){
            // Set dark background for the entire dropdown list
            lv.setStyle("-fx-background-color: linear-gradient(to bottom,#0a1a24,#0f2533); -fx-border-color:#00ffff44; -fx-border-width:1; -fx-border-radius:8;");
            return new ListCell<String>(){
                @Override protected void updateItem(String item, boolean empty){
                    super.updateItem(item, empty);
                    setText(empty? null: item);
                    setFont(Font.font("JetBrains Mono", 12));
                    setTextFill(Color.web("#e0ffff"));
                    setStyle("-fx-background-color: transparent; -fx-padding:6 12;");
                }
                { // hover effect for each dropdown row
                    setOnMouseEntered(ev -> {
                        if(!isEmpty()) setStyle("-fx-background-color: linear-gradient(to right,#0d3344,#145566); -fx-text-fill:#ffffff; -fx-padding:6 12;" +
                                "-fx-effect:dropshadow(gaussian,#00ffff55,6,0,0,0);");
                    });
                    setOnMouseExited(ev -> {
                        if(!isEmpty()) setStyle("-fx-background-color: transparent; -fx-padding:6 12;");
                    });
                }
            }; }
	    };
	    
	    dsSelector.setCellFactory(dsCellFactory);
	    dsSelector.setButtonCell(new ListCell<String>() {
        @Override protected void updateItem(String item, boolean empty){
            super.updateItem(item, empty);
            setText(empty? null: item);
            setFont(Font.font("JetBrains Mono", FontWeight.BOLD, 12));
            setTextFill(Color.web("#e0ffff"));
        }
	    });

        dsValueField = new TextField(); dsValueField.setPrefWidth(80);
        dsIndexField = new TextField(); dsIndexField.setPrefWidth(70);
        dsRightField = new TextField(); dsRightField.setPrefWidth(60);
        String baseFieldStyle = "-fx-background-color: rgba(0,255,255,0.10); -fx-border-color: linear-gradient(to right,#00ffff77,#4ecdc477); -fx-border-width:1; -fx-text-fill:white; -fx-background-radius:10; -fx-border-radius:10; -fx-font-size:12px; -fx-padding:5 8; -fx-focus-color: transparent; -fx-faint-focus-color: transparent;";
        for (TextField tf : List.of(dsValueField, dsIndexField, dsRightField)) {
            tf.setStyle(baseFieldStyle);
            tf.setPromptText("");
            tf.focusedProperty().addListener((o,ov,nv)-> {
                if (nv) tf.setStyle(baseFieldStyle + " -fx-border-color: linear-gradient(to right,#00ffff,#4ecdc4); -fx-effect: dropshadow(gaussian,#00ffff55,8,0,0,0);");
                else tf.setStyle(baseFieldStyle);
            });
        }

	    dsOperationButtonsBox = new HBox(6); dsOperationButtonsBox.setAlignment(Pos.CENTER_LEFT);
	    buildDSOperationButtons();
	
	    Button dsResetBtn = new Button("RESET");
	    dsResetBtn.setFont(Font.font("SF Pro Text", 11));
	    dsResetBtn.setStyle("-fx-background-color: rgba(0,0,0,0.55); -fx-text-fill:#00ffff; -fx-border-color:#00ffff77; -fx-border-radius:8; -fx-background-radius:8; -fx-padding:6 14; -fx-cursor: hand; -fx-font-weight: bold;");
	    dsResetBtn.setOnAction(e -> clear());

	    // Speed slider for animations
	    Label speedLabel = new Label("Speed");
	    speedLabel.setTextFill(Color.WHITE);
	    dsSpeedSlider = new Slider(0.5, 3.0, 1.0); // 0.5x (slower) to 3x (faster)
	    dsSpeedSlider.setPrefWidth(100);
	    dsSpeedSlider.valueProperty().addListener((o,ov,nv)-> {
	        restartHeapAnimSpeed();
	    });
	    dsSpeedSlider.setStyle("-fx-control-inner-background: rgba(255,255,255,0.1);");
	    controlBar.getChildren().addAll(new Label("Structure:"), dsSelector, dsValueField, dsIndexField, dsRightField, dsOperationButtonsBox, dsResetBtn, speedLabel, dsSpeedSlider);
	    controlBar.getChildren().forEach(n -> { if (n instanceof Label l) { l.setTextFill(Color.WHITE); l.setStyle("-fx-font-size:12px;"); } });
	    common.dataStructureContainer.getChildren().add(controlBar);
	    updateDSFieldVisibility();

        // Main split: canvas + explanation
        HBox mainSplit = new HBox(16);
        mainSplit.setAlignment(Pos.TOP_LEFT);
        dsCanvas = new Canvas(760, 480);
        StackPane canvasWrap = new StackPane(dsCanvas);
        canvasWrap.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-background-radius:14; -fx-border-radius:14; -fx-border-color: rgba(255,255,255,0.18); -fx-border-width:1; -fx-padding:8;");
        VBox leftBox = new VBox(8, canvasWrap);
        leftBox.setAlignment(Pos.TOP_CENTER);
        HBox.setHgrow(leftBox, Priority.ALWAYS);

        VBox explBox = new VBox(8);
        explBox.setPrefWidth(320);
        Label explLabel = new Label("STEP EXPLANATION");
        explLabel.setStyle("-fx-text-fill:#ffffff;-fx-font-size:16px;-fx-font-weight:800;");
        dataStructureExplanationArea = new TextArea();
        dataStructureExplanationArea.setEditable(true);
        dataStructureExplanationArea.setWrapText(true);
        dataStructureExplanationArea.setPrefWidth(300);
        dataStructureExplanationArea.setPrefHeight(480);
        dataStructureExplanationArea.setStyle(
            "-fx-control-inner-background: rgba(8,10,14,0.95);" +
            "-fx-background-color: rgba(8,10,14,0.95);" +
            "-fx-text-fill: #ffffff;" +
            "-fx-font-family: 'Consolas','JetBrains Mono','Menlo','SF Pro Text';" +
            "-fx-font-size: 14px;" +
            "-fx-border-color: linear-gradient(to right,#888888,#bbbbbb);" +
            "-fx-border-radius:14; -fx-background-radius:14; -fx-padding:10;"
        );
        
        dataStructureExplanationArea.setPromptText("Data structure operations will appear here...");
        explBox.getChildren().addAll(explLabel, dataStructureExplanationArea);

        mainSplit.getChildren().addAll(leftBox, explBox);
        common.dataStructureContainer.getChildren().add(mainSplit);
        redrawDS();
    }
	
	private void clearDSExplanation(){
        if (dataStructureExplanationArea != null) dataStructureExplanationArea.clear();
    }

    private void redrawDS(){
        if (dsCanvas == null) return;
        GraphicsContext g = dsCanvas.getGraphicsContext2D();
        g.clearRect(0,0, dsCanvas.getWidth(), dsCanvas.getHeight());
        switch(currentDSKind){
            case NONE -> drawSelectMessage(g);
            case STACK -> drawStack(g);
            case QUEUE -> drawQueue(g);
            case HEAP_MAX, HEAP_MIN -> drawHeap(g);
            case FENWICK -> drawFenwick(g);
            case SEGMENT -> drawSegment(g);
            case AVL -> drawAVLTree(g);
        }
    }
    
    private void drawSelectMessage(GraphicsContext g){
        g.setFill(Color.web("#ffffff66"));
        g.setFont(Font.font("SF Pro Text", 18));
        g.fillText("Select a data structure from the dropdown.", 60, 90);
    }

    private void buildDSOperationButtons() {
        dsOperationButtonsBox.getChildren().clear();
        List<Button> btns = new ArrayList<>();
        switch (currentDSKind) {
            case NONE:
                break;
            case STACK: {
                Button push = createDSOpButton("PUSH", () -> {
                    Integer v = parseValue();
                    if (v != null) {
                        stackData.push(v);
                        updateDSExplanation("PUSH -> " + v);
                        redrawDS();
                        dsValueField.clear();
                    }
                });
                Button pop = createDSOpButton("POP", () -> {
                    if (!stackData.isEmpty()) {
                        int v = stackData.pop();
                        updateDSExplanation("POP -> " + v);
                        redrawDS();
                    }
                });
                btns = List.of(push, pop);
                break;
            }
            case QUEUE: {
                Button enq = createDSOpButton("ENQ", () -> {
                    Integer v = parseValue();
                    if (v != null) {
                        queueData.addLast(v);
                        updateDSExplanation("ENQUEUE -> " + v);
                        redrawDS();
                        dsValueField.clear();
                    }
                });
                Button deq = createDSOpButton("DEQ", () -> {
                    if (!queueData.isEmpty()) {
                        int v = queueData.removeFirst();
                        updateDSExplanation("DEQUEUE -> " + v);
                        redrawDS();
                    }
                });
                btns = List.of(enq, deq);
                break;
            }
            case HEAP_MAX, HEAP_MIN: {
                Button ins = createDSOpButton("INSERT", () -> {
                    Integer v = parseValue();
                    if (v != null) {
                        updateDSExplanation("Begin animated INSERT of " + v + (currentDSKind == DSKind.HEAP_MAX ? " into Max Heap" : " into Min Heap"));
                        startHeapInsertAnimation(v);
                        dsValueField.clear();
                    }
                });
                Button ext = createDSOpButton("EXTRACT", () -> {
                    if (!heapData.isEmpty()) {
                        extractValueInteractive();
                    }
                });
                btns = List.of(ins, ext);
                break;
            }
            case FENWICK: {
                Button upd = createDSOpButton("UPDATE", () -> {
                    Integer idx = parseIndex();
                    Integer v = parseValue();
                    if (idx != null && v != null) {
                        fenwickPointUpdate(idx, v);
                        updateDSExplanation("UPDATE idx=" + idx + " +=" + v);
                        redrawDS();
                        dsValueField.clear();
                    }
                });
                Button qry = createDSOpButton("PREFIX", () -> {
                    Integer idx = parseIndex();
                    if (idx != null) {
                        updateDSExplanation("PREFIX(" + idx + ") = " + fenwickPrefix(idx));
                    }
                });
                Button rng = createDSOpButton("RANGE", () -> {
                    Integer l = parseIndex();
                    Integer r = parseRight();
                    if (l != null && r != null) {
                        int ans = fenwickPrefix(r) - fenwickPrefix(l - 1);
                        updateDSExplanation("RANGE(" + l + "," + r + ") = " + ans);
                    }
                });
                btns = List.of(upd, qry, rng);
                break;
            }
            case SEGMENT: {
                Button upd = createDSOpButton("UPDATE", () -> {
                    Integer idx = parseIndex();
                    Integer v = parseValue();
                    if (idx != null && v != null) {
                        segPointUpdate(idx, v);
                        updateDSExplanation("UPDATE idx=" + idx + " ->" + v);
                        redrawDS();
                        dsValueField.clear();
                    }
                });
                Button rng = createDSOpButton("RANGE", () -> {
                    Integer l = parseIndex();
                    Integer r = parseRight();
                    if (l != null && r != null) {
                        int ans = segRangeQuery(l, r);
                        updateDSExplanation("RANGE(" + l + "," + r + ") = " + ans);
                    }
                });
                btns = List.of(upd, rng);
                break;
            }
            case AVL: {
                Button ins = createDSOpButton("INSERT", () -> {
                    String valueText = dsValueField.getText().trim();
                    if (valueText.isEmpty()) {
                        updateDSExplanation("Please enter a value to insert.");
                        return;
                    }
                    try {
                        int value = Integer.parseInt(valueText);
                        avlInsertAnimated(value);
                    } catch (NumberFormatException e) {
                        updateDSExplanation("Invalid number format.");
                    }
                });
                Button del = createDSOpButton("DELETE", () -> {
                    String valueText = dsValueField.getText().trim();
                    if (valueText.isEmpty()) {
                        updateDSExplanation("Please enter a value to delete.");
                        return;
                    }
                    try {
                        int value = Integer.parseInt(valueText);
                        avlDeleteAnimated(value);
                    } catch (NumberFormatException e) {
                        updateDSExplanation("Invalid number format.");
                    }
                });
                Button Clear = createDSOpButton("CLEAR", () -> {
                    avlRoot = null;
                    stopAVLAnimation();
                    redrawDS();
                    updateDSExplanation("AVL tree cleared.");
                });
                btns = List.of(ins, del, Clear);
                break;
            }
        }
        dsOperationButtonsBox.getChildren().addAll(btns);
    }
    
    private Button createDSOpButton(String text, Runnable action) {
        Button button = new Button(text);
        button.setStyle(
            "-fx-background-color: #2c3e50;" +
            "-fx-text-fill: #00ffff;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 8;" +
            "-fx-border-radius: 8;" +
            "-fx-border-color: #00ffff55;" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;"
        );
        button.setFont(Font.font("SF Pro Text", 11));
        button.setOnAction(event -> action.run());
        return button;
    }
    
    private void updateDSExplanation(String line){
        if (dataStructureExplanationArea == null) return; 
        if (line == null || line.isBlank()) return;
        dataStructureExplanationArea.appendText(line + "\n");
    }
    
    private void changeDSKind(String label) {
        if (label == null || label.equals("None")) {
            currentDSKind = DSKind.NONE;
            buildDSOperationButtons();
            clearDSExplanation();
            updateDSFieldVisibility();
            return;
        }
        switch(label){
            case "Stack" -> currentDSKind = DSKind.STACK;
            case "Queue" -> currentDSKind = DSKind.QUEUE;
            case "Max Heap" -> { currentDSKind = DSKind.HEAP_MAX; heapData.clear(); }
            case "Min Heap" -> { currentDSKind = DSKind.HEAP_MIN; heapData.clear(); }
            case "Fenwick Tree" -> { currentDSKind = DSKind.FENWICK; initFenwick(10); }
            case "Segment Tree" -> { currentDSKind = DSKind.SEGMENT; initSegment(8); }
            default -> currentDSKind = DSKind.AVL; // placeholder
        }
        buildDSOperationButtons();
        clearDSExplanation();
        updateDSExplanation("Selected: " + label + ".");
        updateDSFieldVisibility();
    }
    
    private void updateDSFieldVisibility(){
        if (dsValueField == null) return;
        switch(currentDSKind){
            case NONE -> { dsValueField.setVisible(false); dsIndexField.setVisible(false); dsRightField.setVisible(false); }
            case STACK, QUEUE -> { dsValueField.setDisable(false); dsValueField.setVisible(true); dsIndexField.setVisible(false); dsRightField.setVisible(false); }
            case HEAP_MAX, HEAP_MIN -> { dsValueField.setVisible(true); dsIndexField.setVisible(false); dsRightField.setVisible(false); }
            case FENWICK -> { dsValueField.setVisible(true); dsIndexField.setVisible(true); dsRightField.setVisible(true); }
            case SEGMENT -> { dsValueField.setVisible(true); dsIndexField.setVisible(true); dsRightField.setVisible(true); }
            case AVL -> { dsValueField.setVisible(true); dsIndexField.setVisible(false); dsRightField.setVisible(false); }
        }
        // Clear fields when switching to avoid stale inputs
        dsValueField.clear();
        dsIndexField.clear();
        dsRightField.clear();
    }
    
    private void drawStack(GraphicsContext g){
        double x = 80, baseY = dsCanvas.getHeight()-40; double w=80, h=40;
        if (stackData.isEmpty()) {
            g.setFill(Color.web("#ffffff66")); g.setFont(Font.font(14));
            g.fillText("Stack empty", x, baseY - 10);
            return;
        }
        List<Integer> items = new ArrayList<>(stackData); // iterator order: top(first) -> bottom(last)
        int n = items.size();
        for(int i=0;i<n;i++){
            int val = items.get(i);
            double y = baseY - h*(n - i); // i=0 (top) highest, i=n-1 lowest
            g.setFill(Color.web("#0097a7")); g.fillRoundRect(x,y,w,h,10,10);
            g.setStroke(Color.web("#00ffff")); g.strokeRoundRect(x,y,w,h,10,10);
            g.setFill(Color.WHITE); g.setFont(Font.font("SF Pro Text",14));
            g.fillText(String.valueOf(val), x + w/2 - 8, y + h/2 + 5);
        }
        // Top label above top element
        g.setFill(Color.web("#ffffff99")); g.setFont(Font.font(12)); g.fillText("Top", x-10, baseY - h*n - 10);
    }
    
    private void drawQueue(GraphicsContext g){
        double x=80, y=100, w=70, h=70; int i=0;
        for(Integer val: queueData){
            double cx = x + i*(w+12);
            g.setFill(Color.web("#0097a7")); g.fillRoundRect(cx,y,w,h,18,18);
            g.setStroke(Color.web("#00ffff")); g.strokeRoundRect(cx,y,w,h,18,18);
            g.setFill(Color.WHITE); g.setFont(Font.font("SF Pro Text",16));
            g.fillText(String.valueOf(val), cx + w/2 - 8, y + h/2 + 6);
            i++;
        }
        g.setFill(Color.web("#ffffff99")); g.setFont(Font.font(12));
        g.fillText("Front", x-10, y-10);
        g.fillText("Rear", x + (w+12)*queueData.size() - 10, y - 10);
    }
    
    private void drawHeap(GraphicsContext g){
        if (heapData.isEmpty()) { g.setFill(Color.web("#ffffff66")); g.setFont(Font.font(14)); g.fillText("Heap empty", 60, 60); return; }
        double centerX = dsCanvas.getWidth()/2;
        double levelY = 50; double nodeRadius=24; int n=heapData.size();
        for(int i=0;i<n;i++){
            int level = (int)(Math.log(i+1)/Math.log(2));
            int levelStart = (1<<level)-1;
            int posInLevel = i - levelStart;
            int levelCount = 1<<level;
            double spacing = dsCanvas.getWidth() / (levelCount+1);
            double x = spacing*(posInLevel+1);
            double y = 60 + level*80;
            // draw edge to parent
            if(i>0){ int parent=(i-1)/2; double px=0,py=0; int plevel=(int)(Math.log(parent+1)/Math.log(2)); int pstart=(1<<plevel)-1; int ppos= parent - pstart; int pcount=1<<plevel; double pspacing= dsCanvas.getWidth() / (pcount+1); px = pspacing*(ppos+1); py = 60 + plevel*80; g.setStroke(Color.web("#00d8ff55")); g.setLineWidth(2); g.strokeLine(px,y- (80- nodeRadius), x, y-nodeRadius); }
            boolean highlighted = (i==heapHighlightA || i==heapHighlightB);
            Color baseFill = Color.web("#0097a7");
            Color highlightFill = currentHeapAction==null? Color.web("#00bcd4"): switch(currentHeapAction){
                case "insert" -> Color.web("#4caf50");
                case "compare" -> Color.web("#ffb300");
                case "swap" -> Color.web("#e53935");
                default -> Color.web("#00bcd4"); };
            g.setFill(highlighted ? highlightFill : baseFill);
            g.fillOval(x-nodeRadius,y-nodeRadius,nodeRadius*2,nodeRadius*2);
            g.setStroke(highlighted ? Color.web("#ffffff") : Color.web("#00ffff"));
            g.setLineWidth(highlighted?3:2); g.strokeOval(x-nodeRadius,y-nodeRadius,nodeRadius*2,nodeRadius*2);
            
            // Center the text properly within the circle
            g.setFill(Color.WHITE); 
            g.setFont(Font.font("SF Pro Text", FontWeight.SEMI_BOLD, 14));
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(javafx.geometry.VPos.CENTER);
            g.fillText(String.valueOf(heapData.get(i)), x, y);
        }
    }
    private void drawFenwick(GraphicsContext g){
        if (fenwickTree==null){ g.setFill(Color.web("#ffffff66")); g.fillText("Fenwick not init", 50,50); return; }
        g.setFont(Font.font("SF Pro Text",14));
        for(int i=1;i<=fenwickSize;i++){
            double x=40 + (i-1)*55; double y=200; double w=50,h=40;
            g.setFill(Color.web("#0097a7")); g.fillRoundRect(x,y,w,h,10,10);
            g.setStroke(Color.web("#00ffff")); g.strokeRoundRect(x,y,w,h,10,10);
            g.setFill(Color.WHITE); g.fillText(String.valueOf(fenwickTree[i]), x+18, y+25);
            g.setFill(Color.web("#ffffff99")); g.setFont(Font.font(10)); g.fillText("i="+i, x+14, y+h+12);
        }
    }
    private void drawSegment(GraphicsContext g){
        if (segTree==null){ g.setFill(Color.web("#ffffff66")); g.fillText("Segment tree not init", 50,50); return; }
        int N = segSize; int total = 4*N; double baseY = 380; double nodeRadius = 18;
        // simple linear depiction of leaves
        for(int i=0;i<N;i++){
            double x = 50 + i*60; double y=baseY; 
            g.setFill(Color.web("#0097a7")); 
            g.fillOval(x-nodeRadius,y-nodeRadius,nodeRadius*2,nodeRadius*2); 
            g.setStroke(Color.web("#00ffff")); 
            g.strokeOval(x-nodeRadius,y-nodeRadius,nodeRadius*2,nodeRadius*2); 
            
            // Center the text properly within the circle
            g.setFill(Color.WHITE); 
            g.setFont(Font.font("SF Pro Text", FontWeight.SEMI_BOLD, 12)); 
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(javafx.geometry.VPos.CENTER);
            g.fillText(String.valueOf(segSource.get(i)), x, y);
        }
        g.setFill(Color.web("#ffffffaa")); g.setFont(Font.font(12)); g.fillText("(Internal nodes omitted for brevity)", 50, baseY+40);
    }
    
    // AVL Tree Implementation
    private void drawAVLTree(GraphicsContext g) {
        g.clearRect(0, 0, dsCanvas.getWidth(), dsCanvas.getHeight());
        
        if (avlRoot == null) {
            g.setFill(Color.web("#ffffff66")); 
            g.setFont(Font.font(16)); 
            g.fillText("AVL Tree is empty", 60, 80);
            return;
        }
        
        // Calculate tree layout with better spacing
        calculateAVLLayout(avlRoot, dsCanvas.getWidth() / 2, 40, dsCanvas.getWidth() / 2.5);
        
        // Draw edges first
        drawAVLEdges(g, avlRoot);
        
        // Draw nodes on top
        drawAVLNodes(g, avlRoot);
    }
    
    private void calculateAVLLayout(AVLNode node, double x, double y, double hSpacing) {
        if (node == null) return;
        
        node.x = x;
        node.y = y;
        
        double nextY = y + 80;  // Increased vertical spacing
        double nextSpacing = Math.max(hSpacing * 0.6, 40);  // Minimum spacing of 40
        
        if (node.left != null) {
            calculateAVLLayout(node.left, x - nextSpacing, nextY, nextSpacing);
        }
        if (node.right != null) {
            calculateAVLLayout(node.right, x + nextSpacing, nextY, nextSpacing);
        }
    }
    
    private void drawAVLEdges(GraphicsContext g, AVLNode node) {
        if (node == null) return;
        
        g.setStroke(Color.web("#4ecdc4"));
        g.setLineWidth(2);
        
        if (node.left != null) {
            g.strokeLine(node.x, node.y, node.left.x, node.left.y);
            drawAVLEdges(g, node.left);
        }
        if (node.right != null) {
            g.strokeLine(node.x, node.y, node.right.x, node.right.y);
            drawAVLEdges(g, node.right);
        }
    }
    
    private void drawAVLNodes(GraphicsContext g, AVLNode node) {
        if (node == null) return;
        
        double radius = 20;
        
        // Determine node color based on state
        Color fillColor = node.nodeColor;
        if (node == avlHighlightedNode) {
            fillColor = Color.web("#ff6b6b");
        } else if (node.isAnimating) {
            fillColor = Color.web("#ffeaa7");
        } else if (avlRotationNodes.contains(node)) {
            fillColor = Color.web("#fd79a8");
        }
        
        // Draw node circle
        g.setFill(fillColor);
        g.fillOval(node.x - radius, node.y - radius, radius * 2, radius * 2);
        
        g.setStroke(Color.web("#00ffff"));
        g.setLineWidth(2);
        g.strokeOval(node.x - radius, node.y - radius, radius * 2, radius * 2);
        
        // Draw value
        g.setFill(Color.WHITE);
        g.setFont(Font.font("SF Pro Text", FontWeight.BOLD, 14));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(String.valueOf(node.value), node.x, node.y);
        
        // Draw balance factor
        g.setFill(Color.web("#00ffff"));
        g.setFont(Font.font("SF Pro Text", FontWeight.NORMAL, 10));
        g.fillText("B:" + node.getBalance(), node.x, node.y - 30);
        
        // Draw height
        g.fillText("H:" + node.height, node.x, node.y + 35);
        
        // Recursively draw children
        drawAVLNodes(g, node.left);
        drawAVLNodes(g, node.right);
    }
    
    // Fenwick helpers
    private void initFenwick(int n) {
        fenwickSize = n;
        fenwickTree = new int[n + 2];
        fenwickSource.clear();
        for (int i = 0; i < n; i++) {
            fenwickSource.add(0);
        }
    }

    private void fenwickPointUpdate(int idx, int delta) {
        if (idx < 1 || idx > fenwickSize) {
            return;
        }
        fenwickSource.set(idx - 1, fenwickSource.get(idx - 1) + delta);
        while (idx <= fenwickSize) {
            fenwickTree[idx] += delta;
            idx += idx & -idx;
        }
        redrawDS();
    }

    private int fenwickPrefix(int idx) {
        idx = Math.max(0, Math.min(idx, fenwickSize));
        int sum = 0;
        while (idx > 0) {
            sum += fenwickTree[idx];
            idx -= idx & -idx;
        }
        return sum;
    }

    // Segment helpers (point update, sum query)
    private void initSegment(int n) {
        segSize = n;
        segSource.clear();
        for (int i = 0; i < n; i++) {
            segSource.add(0);
        }
        int size = 1;
        while (size < n) {
            size <<= 1;
        }
        segTree = new int[2 * size];
    }

    private void segPointUpdate(int idx, int val) {
        if (idx < 0 || idx >= segSize) {
            return;
        }
        segSource.set(idx, val);
        int size = 1;
        while (size < segSize) {
            size <<= 1;
        }
        int pos = size + idx;
        segTree[pos] = val;
        for (pos /= 2; pos >= 1; pos /= 2) {
            segTree[pos] = segTree[2 * pos] + segTree[2 * pos + 1];
            if (pos == 1) {
                break;
            }
        }
    }

    private int segRangeQuery(int l, int r) {
        if (l < 0) {
            l = 0;
        }
        if (r >= segSize) {
            r = segSize - 1;
        }
        if (l > r) {
            return 0;
        }
        int size = 1;
        while (size < segSize) {
            size <<= 1;
        }
        int left = size + l, right = size + r;
        int res = 0;
        while (left <= right) {
            if ((left & 1) == 1) {
                res += segTree[left++];
            }
            if ((right & 1) == 0) {
                res += segTree[right--];
            }
            left /= 2;
            right /= 2;
        }
        return res;
    }
    
    private Integer parseValue(){ try { return Integer.parseInt(dsValueField.getText().trim()); } catch(Exception ex){ return null; } }
    private Integer parseIndex(){ try { return Integer.parseInt(dsIndexField.getText().trim()); } catch(Exception ex){ return null; } }
    private Integer parseRight(){ try { return Integer.parseInt(dsRightField.getText().trim()); } catch(Exception ex){ return null; } }
    
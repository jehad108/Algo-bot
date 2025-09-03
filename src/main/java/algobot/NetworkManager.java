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
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import java.util.*;
import java.util.function.BiConsumer;

public class NetworkManager {
	private VBox networkExplainBox;
    private TextArea networkExplanationArea;
    private Label networkStatusLabel;
    private Pane networkCanvas;
    private Button netAddNodeBtnRef, netAddEdgeBtnRef, netSetSourceBtnRef, netSetSinkBtnRef, netRunBtnRef;
    private boolean netAddNodeArmed = false, netAddEdgeArmed = false;
    private boolean netSelectingSource = false, netSelectingSink = false;
    private Map<String, GraphNode> netNodes = new HashMap<>();
    private Map<String, Circle> netNodeCircles = new HashMap<>();
    private Map<String, javafx.scene.text.Text> netNodeTexts = new HashMap<>();
    private List<EdgeRecord> netEdges = new ArrayList<>();
    // Temporary overlays for min-cut highlighting
    private final List<Node> netMinCutOverlays = new ArrayList<>();
    private Set<String> netEdgeKeys = new HashSet<>(); // directed id1->id2
    private String netSelectedNodeId = null; // for edge creation
    private boolean netDraggingEdge = false; private String netDragSourceNodeId = null; private Line netDragLine = null;
    private GraphNode flowSource = null, flowSink = null;
    private static final double NODE_RADIUS = 22;
    private static final double BOUNDARY_MARGIN = NODE_RADIUS + 15;
    private Node netCurrentOverlay = null; 
    private EventHandler<MouseEvent> netOutsideHandler = null;
    
    
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
    
    private static class Seg {
        final int u, v;
        final boolean back;

        Seg(int u, int v, boolean b) {
            this.u = u;
            this.v = v;
            this.back = b;
        }
    }
    
	public void createNetworkPage() {
    	common.networkContainer = new VBox(10);
    	common.networkContainer.setAlignment(Pos.TOP_CENTER);
    	common.networkContainer.setPadding(new Insets(60, 20, 20, 20));

        // Same gradient as graph
        LinearGradient gradient = new LinearGradient(0, 0, 1, 1, true, null,
            new Stop(0, Color.web("#0c0c0c")),
            new Stop(0.3, Color.web("#141e30")),
            new Stop(0.6, Color.web("#243b55")),
            new Stop(1, Color.web("#0f051d"))
        );
        common.networkContainer.setBackground(new Background(new BackgroundFill(gradient, null, null)));

        HBox titleContainer = ButtonManager.createTitleWithBackButton("MAX FLOW / MIN CUT NETWORK");

        // Compact control panel (mirrors graph UI, but fixed Directed+Weighted and only Edmonds–Karp)
        VBox buttonPanel = new VBox(6);
        buttonPanel.setAlignment(Pos.CENTER_RIGHT);
        buttonPanel.setPadding(new Insets(25, 0, 0, 15));

        HBox row1 = new HBox(8);
        row1.setAlignment(Pos.CENTER_RIGHT);
        netAddNodeBtnRef = ButtonManager.createCompactButton("ADD NODE", "#00ffff");
        netAddEdgeBtnRef = ButtonManager.createCompactButton("ADD EDGE", "#00ffff");
        ButtonManager.setCompactButtonDisabled(netAddEdgeBtnRef);
        netAddNodeBtnRef.setOnAction(e -> { toggleNetAddNodeMode(); if (netAddEdgeArmed) toggleNetAddEdgeMode(); });
        netAddEdgeBtnRef.setOnAction(e -> { toggleNetAddEdgeMode(); if (netAddNodeArmed) toggleNetAddNodeMode(); });
        row1.getChildren().addAll(netAddNodeBtnRef, netAddEdgeBtnRef);

        HBox row2 = new HBox(8);
        row2.setAlignment(Pos.CENTER_RIGHT);
        Button netUndoEdge = ButtonManager.createCompactButton("UNDO EDGE", "#00ffff");
        netUndoEdge.setOnAction(e -> netUndoLastEdge());
        Button netUndoNode = ButtonManager.createCompactButton("UNDO NODE", "#00ffff");
        netUndoNode.setOnAction(e -> netRemovePreviousNode());
        Button netClear = ButtonManager.createCompactButton("CLEAR", "#00ffff");
        netClear.setOnAction(e -> clearNetwork());
        row2.getChildren().addAll(netUndoEdge, netUndoNode, netClear);

        HBox row3 = new HBox(8);
        row3.setAlignment(Pos.CENTER_RIGHT);
        netSetSourceBtnRef = ButtonManager.createCompactButton("SET SOURCE", "#00ffff");
        netSetSinkBtnRef = ButtonManager.createCompactButton("SET SINK", "#00ffff");
        netRunBtnRef = ButtonManager.createCompactButton("RUN (EDMONDS–KARP)", "#00ffff");
        ButtonManager.setCompactButtonDisabled(netSetSourceBtnRef);
        ButtonManager.setCompactButtonDisabled(netSetSinkBtnRef);
        netRunBtnRef.setDisable(true);
        netSetSourceBtnRef.setOnAction(e -> {
            // Smooth click feedback
            ScaleTransition st = new ScaleTransition(Duration.millis(120), netSetSourceBtnRef);
            st.setToX(0.96); st.setToY(0.96); st.setAutoReverse(true); st.setCycleCount(2); st.play();
            // Ensure other modes are off so clicks select nodes
            if (netAddEdgeArmed) toggleNetAddEdgeMode();
            if (netAddNodeArmed) toggleNetAddNodeMode();
            netSelectingSource = true; netSelectingSink = false; networkStatusLabel.setText("Click a node to set SOURCE");
        });
        netSetSinkBtnRef.setOnAction(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(120), netSetSinkBtnRef);
            st.setToX(0.96); st.setToY(0.96); st.setAutoReverse(true); st.setCycleCount(2); st.play();
            if (netAddEdgeArmed) toggleNetAddEdgeMode();
            if (netAddNodeArmed) toggleNetAddNodeMode();
            netSelectingSink = true; netSelectingSource = false; networkStatusLabel.setText("Click a node to set SINK");
        });
        netRunBtnRef.setOnAction(e -> runEdmondsKarp());
        row3.getChildren().addAll(netSetSourceBtnRef, netSetSinkBtnRef, netRunBtnRef);

        buttonPanel.getChildren().addAll(row1, row2, row3);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleContainer.getChildren().addAll(spacer, buttonPanel);

        // Config summary (fixed Directed + Weighted)
        HBox configPanel = new HBox(18);
        configPanel.setAlignment(Pos.CENTER_LEFT);
        configPanel.setPadding(new Insets(6, 12, 6, 12));
        configPanel.setStyle("-fx-background-color: rgba(10,15,22,0.65); -fx-background-radius:10; -fx-border-radius:10; -fx-border-color:#00ffff44; -fx-border-width:1;");
        Label cfg = new Label("Graph: Directed • Weighted (capacities)");
        cfg.setTextFill(Color.web("#e0ffff"));
        cfg.setFont(Font.font("SF Pro Text", 12));
        networkStatusLabel = new Label("Add nodes and edges with capacities.");
        networkStatusLabel.setTextFill(Color.web("#cccccc"));
        networkStatusLabel.setFont(Font.font("SF Pro Text", 12));
        Region cfgSpacer = new Region(); HBox.setHgrow(cfgSpacer, Priority.ALWAYS);
        configPanel.getChildren().addAll(cfg, cfgSpacer, networkStatusLabel);

        // Main split: canvas + explanation
        HBox mainSplit = new HBox(18);
        mainSplit.setAlignment(Pos.TOP_CENTER);
        mainSplit.setFillHeight(true);
        Pane netCanvas = createNetworkCanvas();
        netCanvas.setPrefSize(1500, 900);
        netCanvas.setMinSize(1000, 620);
        VBox.setVgrow(netCanvas, Priority.ALWAYS);

        networkExplainBox = new VBox(8);
        networkExplainBox.setAlignment(Pos.TOP_LEFT);
        networkExplainBox.setPrefWidth(620);
        Label expl = new Label("NETWORK FLOW STEPS");
        expl.setTextFill(Color.web("#66d9ff"));
        expl.setFont(Font.font("SF Pro Text", FontWeight.SEMI_BOLD, 12));
        networkExplanationArea = new TextArea();
        networkExplanationArea.setEditable(false);
        networkExplanationArea.setWrapText(true);
        networkExplanationArea.setPrefWidth(620);
        networkExplanationArea.setPrefHeight(520);
        networkExplanationArea.setStyle(
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
        networkExplanationArea.setPromptText("Algorithm steps will appear here...");
        VBox.setVgrow(networkExplanationArea, Priority.ALWAYS);
        networkExplainBox.getChildren().addAll(expl, networkExplanationArea);

        HBox.setHgrow(netCanvas, Priority.ALWAYS);
        netCanvas.setMinWidth(650);
        mainSplit.getChildren().addAll(netCanvas, networkExplainBox);

        Label hint = new Label("1) ADD NODE  2) ADD EDGE (set capacities)  3) SET SOURCE & SET SINK  4) RUN");
        hint.setTextFill(Color.web("#cfefff"));
        hint.setFont(Font.font("SF Pro Text", 12));

        common.networkContainer.getChildren().addAll(titleContainer, configPanel, mainSplit, hint);
    }
	
   	private void toggleNetAddNodeMode(){
        netAddNodeArmed = !netAddNodeArmed;
        // Exiting source/sink selection when entering a build mode
        if (netAddNodeArmed) { netSelectingSource = false; netSelectingSink = false; }
        updateNetworkModeUI();
    }
    
    private void toggleNetAddEdgeMode(){
        netAddEdgeArmed = !netAddEdgeArmed;
        if (netAddEdgeArmed) { netSelectingSource = false; netSelectingSink = false; }
        updateNetworkModeUI();
    }
    
    private void updateNetworkModeUI(){
        if (netAddNodeBtnRef != null) netAddNodeBtnRef.setText(netAddNodeArmed ? "NODE MODE ON" : "ADD NODE");
        if (netAddEdgeBtnRef != null) netAddEdgeBtnRef.setText(netAddEdgeArmed ? "EDGE MODE ON" : "ADD EDGE");
        if (networkStatusLabel != null) {
            if (netAddNodeArmed) {
                networkStatusLabel.setText("Node mode ON: Click on canvas to add nodes.");
            } else if (netAddEdgeArmed) {
                networkStatusLabel.setText("Edge mode ON: Drag from one node to another to add an edge.");
            } else if (!netSelectingSource && !netSelectingSink) {
                networkStatusLabel.setText("Add nodes and edges with capacities.");
            }
        }
    }
    
    private void netUndoLastEdge(){ 
    	if (netEdges.isEmpty()) return; 
    	EdgeRecord last = netEdges.remove(netEdges.size()-1);
    	
	    netStopWaterAnim(last, false);
	    
	    if(last.pipeBaseLine!=null) networkCanvas.getChildren().remove(last.pipeBaseLine);
	    if(last.pipeInnerLine!=null) networkCanvas.getChildren().remove(last.pipeInnerLine);
	    if(last.waterLine!=null) networkCanvas.getChildren().remove(last.waterLine);
	    if(last.line!=null) networkCanvas.getChildren().remove(last.line); // legacy safety
	    if(last.weightLabel!=null) networkCanvas.getChildren().remove(last.weightLabel); 
	    if(last.arrowHead!=null) networkCanvas.getChildren().remove(last.arrowHead); 
	    if(last.residualBackLine!=null) networkCanvas.getChildren().remove(last.residualBackLine); 
	    if(last.residualBackLabel!=null) networkCanvas.getChildren().remove(last.residualBackLabel); 
	    
	    netEdgeKeys.remove(last.a+"->"+last.b); updateNetworkWorkflowButtons(); }
		private void netRemovePreviousNode(){ if(netNodes.isEmpty()) return; // Remove last added node and its adjacent edges
	    String lastId = String.valueOf(netNodes.size()-1);
		Circle c = netNodeCircles.remove(lastId);
		if(c!=null) networkCanvas.getChildren().remove(c);
		javafx.scene.text.Text t = netNodeTexts.remove(lastId);
		if(t!=null) networkCanvas.getChildren().remove(t);
	    netNodes.remove(lastId);
	    // Remove associated label nodes too
	    List<EdgeRecord> toRemove = new ArrayList<>();
    
	    for(EdgeRecord er: netEdges){ 
	    	if(er.a.equals(lastId) || er.b.equals(lastId)) 
	    		toRemove.add(er); 
	    }
	    
	    for(EdgeRecord er: toRemove){ 
	    	netEdges.remove(er); 
	    	netEdgeKeys.remove(er.a+"->"+er.b); 
	    	netStopWaterAnim(er, false);
	    	
		    if(er.pipeBaseLine!=null) networkCanvas.getChildren().remove(er.pipeBaseLine);
		    if(er.pipeInnerLine!=null) networkCanvas.getChildren().remove(er.pipeInnerLine);
		    if(er.waterLine!=null) networkCanvas.getChildren().remove(er.waterLine);
		    if(er.line!=null) networkCanvas.getChildren().remove(er.line);
		    if(er.weightLabel!=null) networkCanvas.getChildren().remove(er.weightLabel); 
		    if(er.arrowHead!=null) networkCanvas.getChildren().remove(er.arrowHead); 
		    if(er.residualBackLine!=null) networkCanvas.getChildren().remove(er.residualBackLine); 
		    if(er.residualBackLabel!=null) networkCanvas.getChildren().remove(er.residualBackLabel); 
		 }
    
	     if(flowSource!=null && flowSource.getId().equals(lastId)) flowSource=null; if(flowSink!=null && flowSink.getId().equals(lastId)) flowSink=null;
	     updateNetworkWorkflowButtons();
	}
		
	private void netStopWaterAnim(EdgeRecord e, boolean keepIdle){
	     if (e == null || e.waterLine == null) return;
	     try {
	         if (e.waterDashAnim != null) { e.waterDashAnim.stop(); }
	             e.waterDashAnim = null;
	         if (keepIdle) {
	             e.waterLine.setOpacity(0.23);
	         } else {
	             e.waterLine.setOpacity(0.0);
	         }
	     } catch (Exception ignored) {}
	}
	
	private void updateNetworkWorkflowButtons(){
        boolean hasEdges = !netEdges.isEmpty();
        boolean hasNodes = !netNodes.isEmpty();
        if (netAddEdgeBtnRef != null) {
            if (hasNodes) ButtonManager.setCompactButtonEnabled(netAddEdgeBtnRef); else ButtonManager.setCompactButtonDisabled(netAddEdgeBtnRef);
        }
        // Enable Set Source/Sink as soon as nodes exist (no edge requirement)
        if (netSetSourceBtnRef != null) {
            boolean disable = !hasNodes || (flowSource != null);
            netSetSourceBtnRef.setDisable(disable);
        }
        if (netSetSinkBtnRef != null) {
            boolean disable = !hasNodes || (flowSink != null);
            netSetSinkBtnRef.setDisable(disable);
        }
        if (netRunBtnRef != null) {
            boolean disable = (flowSource == null || flowSink == null);
            netRunBtnRef.setDisable(disable);
        }
    }
	
	private Pane createNetworkCanvas() {
        networkCanvas = new Pane();
        networkCanvas.setPrefSize(900, 520);
        networkCanvas.setMinSize(700, 420);
        networkCanvas.setStyle("-fx-background-color: rgba(15, 15, 18, 0.95); " +
                "-fx-background-radius: 20; " +
                "-fx-border-color: linear-gradient(to bottom right,#00ffff55,#4ecdc455); " +
                "-fx-border-width: 2; -fx-border-radius: 20; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 25,0,0,8);");

        Rectangle safe = new Rectangle(BOUNDARY_MARGIN, BOUNDARY_MARGIN, 900 - 2*BOUNDARY_MARGIN, 520 - 2*BOUNDARY_MARGIN);
        safe.setFill(Color.TRANSPARENT); safe.setStroke(Color.web("#00ffff", 0.15)); safe.setStrokeWidth(1); safe.getStrokeDashArray().addAll(5.0,5.0); safe.setMouseTransparent(true); safe.setArcWidth(15); safe.setArcHeight(15);
        networkCanvas.getChildren().add(safe);
        Rectangle clip = new Rectangle(900, 520); clip.setArcWidth(40); clip.setArcHeight(40); networkCanvas.setClip(clip);

        networkCanvas.setOnMouseClicked(e -> {
            if (!netAddNodeArmed) return;
            double x = common.clamp(e.getX(), BOUNDARY_MARGIN, networkCanvas.getWidth() - BOUNDARY_MARGIN);
            double y = common.clamp(e.getY(), BOUNDARY_MARGIN, networkCanvas.getHeight() - BOUNDARY_MARGIN);
            boolean tooClose = networkCanvas.getChildren().stream().anyMatch(n -> n instanceof Circle c && Math.hypot(c.getCenterX()-x, c.getCenterY()-y) < (NODE_RADIUS*2 + 6));
            if (!tooClose) {
                String id = String.valueOf(netNodes.size());
                netAddNodeAtPosition(id, x, y);
            }
        });
        
        return networkCanvas;
    }
	
	private void netAddNodeAtPosition(String nodeId, double x, double y){
        if (netNodes.containsKey(nodeId)) return;
        GraphNode node = new GraphNode(nodeId, x, y);
        netNodes.put(nodeId, node);

        Circle circle = new Circle(x, y, NODE_RADIUS);
        circle.setFill(Color.web("#0097a7", 0.9));
        circle.setStroke(Color.web("#00ffff"));
        circle.setStrokeWidth(2.0);
        circle.setEffect(new DropShadow(16, Color.web("#00ffff55")));
        node.setCircle(circle);
        netNodeCircles.put(nodeId, circle);

        javafx.scene.text.Text numText = new javafx.scene.text.Text(nodeId);
        numText.setFill(Color.WHITE);
        numText.setStroke(Color.web("#002b36"));
        numText.setStrokeWidth(1.5);
        numText.setStyle("-fx-font-size: 18px; -fx-font-weight: 800;");
        DropShadow txtShadow = new DropShadow(); txtShadow.setColor(Color.web("#000000",0.9)); txtShadow.setRadius(4); txtShadow.setSpread(0.5); numText.setEffect(txtShadow);
        numText.setManaged(false); numText.setMouseTransparent(true);
        Runnable centerText = () -> { double w = numText.getLayoutBounds().getWidth(); double h = numText.getLayoutBounds().getHeight(); numText.setX(circle.getCenterX()-w/2); numText.setY(circle.getCenterY()+h/4 - 1); };
        centerText.run(); numText.layoutBoundsProperty().addListener((o,ov,nv)->centerText.run()); circle.centerXProperty().addListener((o,ov,nv)->centerText.run()); circle.centerYProperty().addListener((o,ov,nv)->centerText.run());

        circle.setOnMousePressed(e -> { if (netAddEdgeArmed) { netStartEdgeDrag(nodeId, e.getSceneX(), e.getSceneY()); e.consume(); } });
        circle.setOnMouseDragged(e -> { if (netDraggingEdge) { netUpdateDragLine(e.getSceneX(), e.getSceneY()); e.consume(); } else { double nx = common.clamp(e.getX(), BOUNDARY_MARGIN, networkCanvas.getWidth()-BOUNDARY_MARGIN); double ny = common.clamp(e.getY(), BOUNDARY_MARGIN, networkCanvas.getHeight()-BOUNDARY_MARGIN); circle.setCenterX(nx); circle.setCenterY(ny); centerText.run(); node.setX(nx); node.setY(ny);} });
        circle.setOnMouseReleased(e -> { if (netDraggingEdge) { netFinishEdgeDrag(e.getSceneX(), e.getSceneY()); e.consume(); } });
        circle.setOnMouseClicked(e -> { if (!netAddEdgeArmed) { handleNetworkNodeClick(nodeId); e.consume(); } });

	    networkCanvas.getChildren().addAll(circle, numText);
	    netNodeTexts.put(nodeId, numText);
        updateNetworkWorkflowButtons();
    }
	
	private void highlightNetNode(Circle c, Color stroke) {
	    if (c == null) {
	        return;
	    }
	    c.setStroke(stroke);
	    c.setStrokeWidth(3.0);
	}

	private void unhighlightNetNode(Circle c) {
	    if (c == null) {
	        return;
	    }
	    c.setStroke(Color.web("#00ffff"));
	    c.setStrokeWidth(2.0);
	}

	private void netStartEdgeDrag(String sourceId, double sceneX, double sceneY) {
	    netDraggingEdge = true;
	    netDragSourceNodeId = sourceId;

	    highlightNetNode(netNodeCircles.get(sourceId), Color.web("#ffe36e"));

	    netDragLine = new Line();
	    netDragLine.setStroke(Color.web("#00d8ff"));
	    netDragLine.setStrokeWidth(3);
	    netDragLine.setOpacity(0.8);
	    netDragLine.getStrokeDashArray().addAll(5.0, 5.0);

	    Circle src = netNodeCircles.get(sourceId);
	    if (src != null) {
	        netDragLine.setStartX(src.getCenterX());
	        netDragLine.setStartY(src.getCenterY());
	        netDragLine.setEndX(src.getCenterX());
	        netDragLine.setEndY(src.getCenterY());
	        networkCanvas.getChildren().add(netDragLine);
	    }
	}

	private void netUpdateDragLine(double sceneX, double sceneY) {
	    if (netDragLine != null) {
	        javafx.geometry.Point2D p = networkCanvas.sceneToLocal(sceneX, sceneY);
	        netDragLine.setEndX(p.getX());
	        netDragLine.setEndY(p.getY());
	    }
	}

	private void netFinishEdgeDrag(double sceneX, double sceneY) {
	    if (!netDraggingEdge || netDragSourceNodeId == null) {
	        return;
	    }

	    String target = netFindNodeAt(sceneX, sceneY);

	    if (target != null && !target.equals(netDragSourceNodeId)) {
	        netCreateEdge(netDragSourceNodeId, target);
	    }

	    netCleanupEdgeDrag();
	}

	private void netCleanupEdgeDrag() {
	    netDraggingEdge = false;

	    if (netDragSourceNodeId != null) {
	        unhighlightNetNode(netNodeCircles.get(netDragSourceNodeId));
	        netDragSourceNodeId = null;
	    }

	    if (netDragLine != null) {
	        networkCanvas.getChildren().remove(netDragLine);
	        netDragLine = null;
	    }
	}

	private String netFindNodeAt(double sceneX, double sceneY) {
	    javafx.geometry.Point2D p = networkCanvas.sceneToLocal(sceneX, sceneY);

	    for (Map.Entry<String, Circle> e : netNodeCircles.entrySet()) {
	        Circle c = e.getValue();
	        if (Math.hypot(p.getX() - c.getCenterX(), p.getY() - c.getCenterY()) <= NODE_RADIUS) {
	            return e.getKey();
	        }
	    }

	    return null;
	}

	private void netCreateEdge(String id1, String id2) {
	    if (id1 == null || id2 == null || id1.equals(id2)) {
	        return;
	    }
	    String key = id1 + "->" + id2;
	    if (netEdgeKeys.contains(key)) {
	        return;
	    }
	    
	    Circle c1 = netNodeCircles.get(id1);
	    Circle c2 = netNodeCircles.get(id2);
	    
	    if (c1 == null || c2 == null) {
	        return;
	    }
	    
	    showInlineWeightInputNetwork(id1, id2, c1, c2, (cap, canceled) -> {
	        if (canceled) {
	            return;
	        }
	        double w = (cap == null ? 1.0 : cap);
	        netFinalizeEdgeCreation(id1, id2, key, c1, c2, w);
	    });
	}
	
	private void handleNetworkNodeClick(String nodeId){
        if (netSelectingSource) {
            flowSource = netNodes.get(nodeId);
            highlightNetNode(flowSource.getCircle(), Color.web("#7CFC00"));
            netSelectingSource = false;
            networkStatusLabel.setText("Source set: "+nodeId+". Now set sink.");
            if (netSetSourceBtnRef != null) netSetSourceBtnRef.setDisable(true);
            if (netSetSinkBtnRef != null && flowSink==null) netSetSinkBtnRef.setDisable(false);
        } else if (netSelectingSink) {
            flowSink = netNodes.get(nodeId);
            highlightNetNode(flowSink.getCircle(), Color.web("#ff6b6b"));
            netSelectingSink = false;
            networkStatusLabel.setText("Sink set: "+nodeId+". Ready to run.");
            if (netSetSinkBtnRef != null) netSetSinkBtnRef.setDisable(true);
        }
        updateNetworkWorkflowButtons();
    }
	
	private void showInlineWeightInputNetwork(String from, String to, Circle c1, Circle c2, BiConsumer<Double, Boolean> consumer) {
	    if (networkCanvas == null) {
	        consumer.accept(1.0, false);
	        return;
	    }
	    
	    if (netCurrentOverlay != null) {
	        networkCanvas.getChildren().remove(netCurrentOverlay);
	    }
	    
	    if (netOutsideHandler != null) {
	        networkCanvas.removeEventFilter(MouseEvent.MOUSE_PRESSED, netOutsideHandler);
	    }
	    
	    double midX = (c1.getCenterX() + c2.getCenterX()) / 2.0;
	    double midY = (c1.getCenterY() + c2.getCenterY()) / 2.0;

	    HBox box = new HBox(6);
	    box.setAlignment(Pos.CENTER_LEFT);
	    box.setPadding(new Insets(6, 10, 6, 10));
	    box.setStyle("-fx-background-color: rgba(5,18,28,0.92); -fx-background-radius:12; -fx-border-radius:12; -fx-border-color:#00ffff99; -fx-border-width:1.2;");

	    Label lbl = new Label("Capacity " + from + "→" + to + ":");
	    lbl.setStyle("-fx-text-fill:#66d9ff;-fx-font-size:11px;-fx-font-weight:600;");

	    TextField tf = new TextField("1");
	    tf.setPrefColumnCount(4);
	    tf.setStyle("-fx-background-color: rgba(255,255,255,0.07); -fx-text-fill:#e8faff; -fx-font-size:12px; -fx-font-weight:600; -fx-background-radius:8; -fx-border-radius:8; -fx-border-color:#00ffff55; -fx-border-width:1; -fx-padding:3 6;");

	    Button ok = new Button("ADD");
	    ok.setStyle("-fx-background-color:#00b4d8;-fx-text-fill:white;-fx-font-weight:700;-fx-background-radius:8;-fx-padding:4 12; -fx-font-size:11px;");

	    Button cancel = new Button("X");
	    cancel.setStyle("-fx-background-color:#374151;-fx-text-fill:#eeeeee;-fx-font-weight:600;-fx-background-radius:8;-fx-padding:4 10; -fx-font-size:11px;");

	    box.getChildren().addAll(lbl, tf, ok, cancel);
	    box.setLayoutX(midX - 90);
	    box.setLayoutY(midY - 28);
	    netCurrentOverlay = box;

	    networkCanvas.getChildren().add(box);
	    tf.requestFocus();

	    Runnable commit = () -> {
	        Double val = 1.0;
	        try {
	            val = Double.parseDouble(tf.getText().trim());
	        } catch (Exception ex) {
	        }
	        cleanupInlineWeightNetwork();
	        consumer.accept(val, false);
	    };

	    Runnable cancelRun = () -> {
	        cleanupInlineWeightNetwork();
	        consumer.accept(null, true);
	    };

	    ok.setOnAction(e -> commit.run());
	    cancel.setOnAction(e -> cancelRun.run());
	    tf.setOnAction(e -> commit.run());

	    tf.textProperty().addListener((o, ov, nv) -> {
	        try {
	            Double.parseDouble(nv.trim());
	            ok.setDisable(false);
	        } catch (Exception ex) {
	            ok.setDisable(true);
	        }
	    });

	    tf.setOnKeyPressed(e -> {
	        if (e.getCode() == KeyCode.ESCAPE) {
	            cancelRun.run();
	        }
	    });

	    netOutsideHandler = evt -> {
	        if (!box.localToScene(box.getBoundsInLocal()).contains(evt.getSceneX(), evt.getSceneY())) {
	            cancelRun.run();
	        }
	    };
	    
	    networkCanvas.addEventFilter(MouseEvent.MOUSE_PRESSED, netOutsideHandler);
	}
	
	private void netFinalizeEdgeCreation(String a, String b, String key, Circle c1, Circle c2, double cap){
        // Build a layered "pipe": base (outer), inner, and a water overlay that we can animate
        Line base = new Line();
        base.setStroke(Color.web("#0b2a35"));
        base.setStrokeWidth(9);
        base.setOpacity(0.9);
        base.setStrokeLineCap(StrokeLineCap.ROUND);

        Line inner = new Line();
        inner.setStroke(Color.web("#6fd6ff"));
        inner.setStrokeWidth(6.5);
        inner.setOpacity(0.9);
        inner.setEffect(new DropShadow(10, Color.web("#00ffff55")));
        inner.setStrokeLineCap(StrokeLineCap.ROUND);

        Line water = new Line();
        water.setStroke(Color.web("#3f7cff"));
        water.setStrokeWidth(4.2);
        water.setOpacity(0.0); // hidden until animating
        water.getStrokeDashArray().setAll(18.0, 12.0);
        water.setStrokeLineCap(StrokeLineCap.ROUND);

        Runnable pos = () -> {
            double c1X=c1.getCenterX(), c1Y=c1.getCenterY(), c2X=c2.getCenterX(), c2Y=c2.getCenterY();
            double dx=c2X-c1X, dy=c2Y-c1Y; double dist=Math.sqrt(dx*dx+dy*dy);
            if(dist>0){ dx/=dist; dy/=dist; double sx=c1X+dx*NODE_RADIUS, sy=c1Y+dy*NODE_RADIUS, ex=c2X-dx*NODE_RADIUS, ey=c2Y-dy*NODE_RADIUS;
                base.setStartX(sx); base.setStartY(sy); base.setEndX(ex); base.setEndY(ey);
                inner.setStartX(sx); inner.setStartY(sy); inner.setEndX(ex); inner.setEndY(ey);
                water.setStartX(sx); water.setStartY(sy); water.setEndX(ex); water.setEndY(ey);
            }
        };
        c1.centerXProperty().addListener((o,ov,nv)->pos.run()); c1.centerYProperty().addListener((o,ov,nv)->pos.run());
        c2.centerXProperty().addListener((o,ov,nv)->pos.run()); c2.centerYProperty().addListener((o,ov,nv)->pos.run()); pos.run();
        // Insert in order: base at bottom, then inner, then water
        networkCanvas.getChildren().add(0, base);
        networkCanvas.getChildren().add(1, inner);
        networkCanvas.getChildren().add(2, water);

        // Show Remaining/Capacity; initially remaining = capacity
        Label capLabel = new Label(common.fmt(cap) + "/" + common.fmt(cap));
        capLabel.setStyle(
            "-fx-text-fill: white;" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: 700;" +
            "-fx-background-color: rgba(0, 43, 54, 0.85);" +
            "-fx-background-radius: 4;" +
            "-fx-padding: 2 6;" +
            "-fx-border-color: #00ffff;" +
            "-fx-border-width: 0.5;" +
            "-fx-border-radius: 4;"
        );
        capLabel.setManaged(true);
        capLabel.setMouseTransparent(true);
        capLabel.setVisible(true);
        networkCanvas.getChildren().add(capLabel);
        Runnable updateLabel = ()->{ double midX=(inner.getStartX()+inner.getEndX())/2.0, midY=(inner.getStartY()+inner.getEndY())/2.0; capLabel.setLayoutX(midX-20); capLabel.setLayoutY(midY-10); };
        inner.startXProperty().addListener((o,ov,nv)->updateLabel.run()); inner.startYProperty().addListener((o,ov,nv)->updateLabel.run());
        inner.endXProperty().addListener((o,ov,nv)->updateLabel.run()); inner.endYProperty().addListener((o,ov,nv)->updateLabel.run()); updateLabel.run();
        Polygon arrow = new Polygon(); arrow.setFill(Color.web("#6fd6ff")); arrow.setStroke(Color.web("#6fd6ff")); Runnable updArr=()->{ double sx=inner.getStartX(), sy=inner.getStartY(), ex=inner.getEndX(), ey=inner.getEndY(); double dx=ex-sx, dy=ey-sy, len=Math.sqrt(dx*dx+dy*dy); if(len>0){ dx/=len; dy/=len; double ax=ex, ay=ey, size=12; double px=-dy*size*0.5, py=dx*size*0.5; arrow.getPoints().setAll(ax,ay, ax-dx*size+px, ay-dy*size+py, ax-dx*size-px, ay-dy*size-py);} };
        inner.startXProperty().addListener((o,ov,nv)->updArr.run()); inner.startYProperty().addListener((o,ov,nv)->updArr.run()); inner.endXProperty().addListener((o,ov,nv)->updArr.run()); inner.endYProperty().addListener((o,ov,nv)->updArr.run()); updArr.run(); networkCanvas.getChildren().add(arrow); capLabel.toFront();
        // Create record and connect layered lines
        EdgeRecord er = new EdgeRecord(inner, a, b, cap, capLabel, arrow);
        er.pipeBaseLine = base; er.pipeInnerLine = inner; er.waterLine = water; er.line = inner;
	    netEdges.add(er); netEdgeKeys.add(key);
	    // Prepare residual back-edge overlay (hidden initially)
	    netEnsureBackOverlay(er);
	    netSetBackOverlayVisible(er, false);
        updateNetworkWorkflowButtons();
    }
	
	private void netEnsureBackOverlay(EdgeRecord er){
        if(er==null || er.residualBackLine!=null) return;
        // Create a dashed magenta line from b -> a
        Circle c1 = netNodeCircles.get(er.a);
        Circle c2 = netNodeCircles.get(er.b);
        if(c1==null || c2==null) return;
        Line back = new Line();
        back.setStroke(Color.web("#ff66cc"));
        back.setStrokeWidth(2.0);
        back.getStrokeDashArray().setAll(6.0, 6.0);
        back.setOpacity(0.0);
        Runnable pos = ()->{ double c1X=c1.getCenterX(), c1Y=c1.getCenterY(), c2X=c2.getCenterX(), c2Y=c2.getCenterY(); double dx=c1X-c2X, dy=c1Y-c2Y; double dist=Math.hypot(dx,dy); if(dist>0){ dx/=dist; dy/=dist; back.setStartX(c2X+dx*NODE_RADIUS); back.setStartY(c2Y+dy*NODE_RADIUS); back.setEndX(c1X-dx*NODE_RADIUS); back.setEndY(c1Y-dy*NODE_RADIUS);} };
        c1.centerXProperty().addListener((o,ov,nv)->pos.run()); c1.centerYProperty().addListener((o,ov,nv)->pos.run()); c2.centerXProperty().addListener((o,ov,nv)->pos.run()); c2.centerYProperty().addListener((o,ov,nv)->pos.run()); pos.run();
        Label lbl = new Label("");
        lbl.setStyle("-fx-text-fill:#ffb3e6; -fx-font-weight:700; -fx-font-size:11px; -fx-background-color: rgba(50,0,35,0.8); -fx-background-radius: 4; -fx-padding: 1 4; -fx-border-color:#ff66cc; -fx-border-width:0.5; -fx-border-radius:4;");
        lbl.setManaged(true); lbl.setMouseTransparent(true); lbl.setVisible(true); lbl.setOpacity(0.0);
        Runnable updateLbl = ()->{ double midX=(back.getStartX()+back.getEndX())/2.0, midY=(back.getStartY()+back.getEndY())/2.0; lbl.setLayoutX(midX-10); lbl.setLayoutY(midY-8); };
        back.startXProperty().addListener((o,ov,nv)->updateLbl.run()); back.startYProperty().addListener((o,ov,nv)->updateLbl.run()); back.endXProperty().addListener((o,ov,nv)->updateLbl.run()); back.endYProperty().addListener((o,ov,nv)->updateLbl.run()); updateLbl.run();
        // Place under main forward line but above canvas background
        int insertIndex = Math.max(0, networkCanvas.getChildren().indexOf(er.line));
        networkCanvas.getChildren().add(insertIndex, back);
        networkCanvas.getChildren().add(lbl);
        er.residualBackLine = back; er.residualBackLabel = lbl;
    }
	
	private void netSetBackOverlayVisible(EdgeRecord er, boolean visible) {
	    if (er == null) {
	        return;
	    }
	    if (er.residualBackLine != null) {
	        er.residualBackLine.setOpacity(visible ? 0.85 : 0.0);
	    }
	    if (er.residualBackLabel != null) {
	        er.residualBackLabel.setOpacity(visible ? 1.0 : 0.0);
	    }
	}

	private EdgeRecord netFindEdge(String a, String b) {
	    for (EdgeRecord er : netEdges) {
	        if (er.a.equals(a) && er.b.equals(b)) {
	            return er;
	        }
	    }
	    return null;
	}
	
	private void cleanupInlineWeightNetwork() {
	    if (netCurrentOverlay != null) {
	        networkCanvas.getChildren().remove(netCurrentOverlay);
	    }
	    netCurrentOverlay = null;

	    if (netOutsideHandler != null) {
	        networkCanvas.removeEventFilter(MouseEvent.MOUSE_PRESSED, netOutsideHandler);
	    }
	    netOutsideHandler = null;
	}
	
	public void clearNetwork() {
	    if (networkCanvas != null) {
	        networkCanvas.getChildren().clear();
	    }
	    // Re-add safe zone boundary
	    if (networkCanvas != null) {
	        Rectangle safe = new Rectangle(BOUNDARY_MARGIN, BOUNDARY_MARGIN, 900 - 2 * BOUNDARY_MARGIN, 520 - 2 * BOUNDARY_MARGIN);
	        safe.setFill(Color.TRANSPARENT);
	        safe.setStroke(Color.web("#00ffff", 0.15));
	        safe.setStrokeWidth(1);
	        safe.getStrokeDashArray().addAll(5.0, 5.0);
	        safe.setMouseTransparent(true);
	        safe.setArcWidth(15);
	        safe.setArcHeight(15);
	        networkCanvas.getChildren().add(safe);
	        
	        Rectangle clip = new Rectangle(900, 520);
	        clip.setArcWidth(40);
	        clip.setArcHeight(40);
	        networkCanvas.setClip(clip);
	    }

	    netNodes.clear();
	    netNodeCircles.clear();
	    netNodeTexts.clear();
	    // remove overlays already handled by clearing children; clear edge state maps
	    netEdges.clear();
	    netEdgeKeys.clear();
	    netSelectedNodeId = null;
	    netDraggingEdge = false;
	    netDragSourceNodeId = null;
	    netDragLine = null;
	    netAddNodeArmed = false;
	    netAddEdgeArmed = false;
	    netSelectingSource = false;
	    netSelectingSink = false;
	    flowSource = null;
	    flowSink = null;

	    if (networkExplanationArea != null) {
	        networkExplanationArea.clear();
	    }
	    if (networkStatusLabel != null) {
	        networkStatusLabel.setText("Add nodes and edges with capacities.");
	    }
	    
	    updateNetworkWorkflowButtons();
	}
	
	private void runEdmondsKarp(){
        if (flowSource==null || flowSink==null){ 
        	if(networkStatusLabel!=null) 
        		networkStatusLabel.setText("Set source and sink first."); 
        	return; 
        }
        
	    // Clear previous min-cut overlays if any
	    clearMinCutOverlays();
	    
        // Build index map
        List<String> ids = new ArrayList<>(netNodes.keySet()); Collections.sort(ids, Comparator.comparingInt(Integer::parseInt));
        int n = ids.size(); Map<String,Integer> idx = new HashMap<>(); for (int i=0;i<n;i++) idx.put(ids.get(i), i);
        int s = idx.get(flowSource.getId()); int t = idx.get(flowSink.getId());
        double[][] cap = new double[n][n];
        for (EdgeRecord e : netEdges){ Integer u = idx.get(e.a), v = idx.get(e.b); if(u!=null && v!=null) cap[u][v] += e.weight; }
        double[][] flow = new double[n][n];
        NetFlowRun run = new NetFlowRun(n, s, t, ids, idx, cap, flow);
        appendNetworkLog("Starting Edmonds–Karp from "+flowSource.getId()+" to "+flowSink.getId()+"\n");
        // Kick off iterative augment-animate loop
        AugmentStep(run);
    }
	
	private void clearMinCutOverlays(){
        if(networkCanvas==null) { netMinCutOverlays.clear(); return; }
        for(Node n : netMinCutOverlays){ networkCanvas.getChildren().remove(n); }
        netMinCutOverlays.clear();
    }
	
	private static class NetFlowRun {
	    final int n, s, t;
	    final List<String> ids;
	    final Map<String, Integer> idx;
	    final double[][] cap;
	    final double[][] flow;
	    double maxFlow = 0;

	    NetFlowRun(int n, int s, int t, List<String> ids, Map<String, Integer> idx, double[][] cap, double[][] flow) {
	        this.n = n;
	        this.s = s;
	        this.t = t;
	        this.ids = ids;
	        this.idx = idx;
	        this.cap = cap;
	        this.flow = flow;
	    }
	}
	
	private void appendNetworkLog(String text) {
	    if (networkExplanationArea == null) {
	        return;
	    }
	    networkExplanationArea.appendText(text);
	    Platform.runLater(() -> {
	        networkExplanationArea.positionCaret(networkExplanationArea.getLength());
	        networkExplanationArea.setScrollTop(Double.MAX_VALUE);
	    });
	}
	
	private void AugmentStep(NetFlowRun run){
        // BFS in residual network; parent[v] >=0 means forward parent; parent[v] <0 encodes back edge with parent = -(u+1)
        int[] parent = new int[run.n]; Arrays.fill(parent, -1); parent[run.s]=run.s; double[] m = new double[run.n]; m[run.s]=Double.POSITIVE_INFINITY;
        ArrayDeque<Integer> q = new ArrayDeque<>(); q.add(run.s);
        while(!q.isEmpty() && parent[run.t]==-1){ int u = q.poll(); for(int v=0; v<run.n; v++){
                double residualF = run.cap[u][v] - run.flow[u][v];
                if(residualF > 1e-9 && parent[v]==-1){ parent[v]=u; m[v]=Math.min(m[u], residualF); q.add(v);} 
                // back edge from u->v exists if there is flow from v to u
                if(run.flow[v][u] > 1e-9 && parent[v]==-1){ parent[v] = -(u+1); m[v]=Math.min(m[u], run.flow[v][u]); q.add(v);} }
        }
        if(parent[run.t]==-1){
            appendNetworkLog("No more augmenting paths. Max Flow = "+common.fmt(run.maxFlow)+"\n");
            if(networkStatusLabel!=null) networkStatusLabel.setText("Max Flow computed: "+common.fmt(run.maxFlow));
            applyNetworkFlowToLabels(run.flow, run.idx);
            // Update residual back-edge overlays one last time
            updateNetworkResidualOverlays(run.flow, run.idx);
            // Compute reachable set in residual graph and highlight min-cut edges
            boolean[] reach = computeResidualReachable(run);
            highlightMinCutEdges(run, reach);
            return;
        }
        double aug = m[run.t];
        // Reconstruct path segments from s to t
        java.util.List<Seg> segs = new ArrayList<>(); List<String> pathNodes = new ArrayList<>();
        int cur = run.t; pathNodes.add(run.ids.get(cur));
        while(cur != run.s){ 
        	int p = parent[cur]; 
        	if(p>=0){ 
        		segs.add(0, new Seg(p, cur, false)); 
        		cur = p; 
        	} 
        	else { 
        		int u = -p-1; 
        		segs.add(0, new Seg(u, cur, true)); 
        		cur = u; 
        	} 
        	
        	pathNodes.add(0, run.ids.get(cur)); 
        }
        
        appendNetworkLog("Path: "+String.join(" -> ", pathNodes)+"  | bottleneck = "+common.fmt(aug)+"\n");
        // Animate highlighting this path, then apply augmentation and recurse
        animateAugmentPathAndApply(segs, aug, run, () -> AugmentStep(run));
    }
	
	private void applyNetworkFlowToLabels(double[][] flow, Map<String,Integer> idx){
        for (EdgeRecord e : netEdges){
            Integer u = idx.get(e.a), v = idx.get(e.b);
            if (u==null || v==null) continue;
            double f = flow[u][v];
            e.flow = f;
            // Show remaining capacity over total capacity to reflect decreased capacity after pushing flow
            double remaining = Math.max(0, e.weight - f);
            if (e.weightLabel != null){ e.weightLabel.setText(common.fmt(remaining) + "/" + common.fmt(e.weight)); }
        }
    }
	
    private void updateNetworkResidualOverlays(double[][] flow, Map<String,Integer> idx){
        for(EdgeRecord e : netEdges){ Integer u = idx.get(e.a), v = idx.get(e.b); if(u==null||v==null) continue; double f = flow[u][v];
            netEnsureBackOverlay(e);
            if(f > 1e-9){ netSetBackOverlayVisible(e, true); if(e.residualBackLabel!=null) e.residualBackLabel.setText(common.fmt(f)); }
            else { netSetBackOverlayVisible(e, false); }
        }
    }
    
    private boolean[] computeResidualReachable(NetFlowRun run){
        boolean[] reach = new boolean[run.n];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        reach[run.s] = true; q.add(run.s);
        while(!q.isEmpty()){
            int u = q.poll();
            for(int v=0; v<run.n; v++){
                // forward residual capacity
                if(!reach[v] && run.cap[u][v] - run.flow[u][v] > 1e-9){ reach[v]=true; q.add(v); }
                // backward residual capacity (i.e., there is flow to cancel)
                if(!reach[v] && run.flow[v][u] > 1e-9){ reach[v]=true; q.add(v); }
            }
        }
        return reach;
    }
    
    private void highlightMinCutEdges(NetFlowRun run, boolean[] reach){
        clearMinCutOverlays();
        Color cutColor = Color.web("#ffd166"); // warm gold
        List<String> listed = new ArrayList<>();
        for(EdgeRecord e : netEdges){
            Integer u = run.idx.get(e.a), v = run.idx.get(e.b);
            if(u==null||v==null) continue;
            if(reach[u] && !reach[v]){
                // Create a bright overlay line atop the inner pipe
                Line ol = new Line();
                Line base = (e.pipeInnerLine!=null)? e.pipeInnerLine : e.line;
                if(base==null) continue;
                ol.startXProperty().bind(base.startXProperty());
                ol.startYProperty().bind(base.startYProperty());
                ol.endXProperty().bind(base.endXProperty());
                ol.endYProperty().bind(base.endYProperty());
                ol.setStroke(cutColor);
                ol.setStrokeWidth((base.getStrokeWidth()>0? base.getStrokeWidth():6.0) + 3.0);
                ol.setOpacity(0.0);
                ol.setStrokeLineCap(StrokeLineCap.ROUND);
                ol.setMouseTransparent(true);
                ol.setEffect(new DropShadow(18, cutColor));
                networkCanvas.getChildren().add(ol);
                netMinCutOverlays.add(ol);

                // Optional subtle pulse-in
                Timeline tl = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(ol.opacityProperty(), 0.0)),
                    new KeyFrame(Duration.millis(400), new KeyValue(ol.opacityProperty(), 0.95))
                );
                tl.play();

                // Keep labels on top
                if(e.weightLabel!=null) e.weightLabel.toFront();
                listed.add(e.a+"->"+e.b);
            }
        }
        if(!listed.isEmpty()){
            appendNetworkLog("Min cut edges (S->T): "+String.join(", ", listed)+"\n");
        } else {
            appendNetworkLog("Min cut: no crossing edges found.\n");
        }
    }

    private void animateAugmentPathAndApply(List<Seg> segs, double aug, NetFlowRun run, Runnable onDone) {
        // Build blue overlay arrows on each segment of the path.
        // This makes the active augmenting path obvious to the user without extra dots/labels.
        List<Node> tempOverlays = new ArrayList<>();
        // Track edges whose water animation we enable for this augmentation
        List<EdgeRecord> waterEdges = new ArrayList<>();
        List<Boolean> waterReverse = new ArrayList<>();

        Color pathBlue = Color.web("#3f7cff");
        double lineW = 7.0; // thicker base

        // Helper to create an arrowhead polygon oriented from (x1,y1) -> (x2,y2)
        java.util.function.BiFunction<double[], Double, Polygon> makeArrow = (segPts, scale) -> {
            double x1 = segPts[0], y1 = segPts[1], x2 = segPts[2], y2 = segPts[3];
            double ang = Math.atan2(y2 - y1, x2 - x1);
            double len = 10 * scale, wing = 6 * scale;
            double ax = x2, ay = y2;
            double lx = ax - len * Math.cos(ang) - wing * Math.sin(ang);
            double ly = ay - len * Math.sin(ang) + wing * Math.cos(ang);
            double rx = ax - len * Math.cos(ang) + wing * Math.sin(ang);
            double ry = ay - len * Math.sin(ang) - wing * Math.cos(ang);
            Polygon p = new Polygon(ax, ay, lx, ly, rx, ry);
            p.setFill(pathBlue);
            p.setOpacity(0);
            p.setMouseTransparent(true);
            return p;
        };

        // Create overlays for all segments at once
        for (Seg s : segs) {
            String a = run.ids.get(s.u), b = run.ids.get(s.v);
            // Determine overlay direction: forward is u->v, back means we move along v->u in residual
            String from = s.back ? b : a;
            String to = s.back ? a : b;
            Circle cFrom = netNodeCircles.get(from);
            Circle cTo = netNodeCircles.get(to);
            if (cFrom == null || cTo == null) {
                continue;
            }
            // For water animation, always reference the forward edge a->b and use reverse flag for back segments
            EdgeRecord er = netFindEdge(a, b);
            if (er != null) {
                waterEdges.add(er);
                waterReverse.add(s.back);
            }
            double x1 = cFrom.getCenterX(), y1 = cFrom.getCenterY();
            double x2 = cTo.getCenterX(), y2 = cTo.getCenterY();
            double dx = x2 - x1, dy = y2 - y1;
            double dist = Math.hypot(dx, dy);
            if (dist == 0) {
                dist = 1;
            }
            dx /= dist;
            dy /= dist;
            double startX = x1 + dx * NODE_RADIUS, startY = y1 + dy * NODE_RADIUS;
            double endX = x2 - dx * NODE_RADIUS, endY = y2 - dy * NODE_RADIUS;

            Line overlay = new Line(startX, startY, endX, endY);
            overlay.setStroke(pathBlue);
            overlay.setStrokeWidth(lineW);
            overlay.setOpacity(0);
            overlay.setMouseTransparent(true);
            // Solid line with a soft glow so it pops clearly over the base edge
            overlay.setEffect(new DropShadow(12, pathBlue));
            overlay.setStrokeLineCap(StrokeLineCap.ROUND);

            Polygon arrow = makeArrow.apply(new double[]{startX, startY, endX, endY}, 1.0);
            arrow.setEffect(new DropShadow(10, pathBlue));

            networkCanvas.getChildren().addAll(overlay, arrow);
            tempOverlays.add(overlay);
            tempOverlays.add(arrow);
        }

        // Keep original edge capacity labels on top of overlays
        for (EdgeRecord e : netEdges) {
            if (e.weightLabel != null) {
                e.weightLabel.toFront();
            }
        }

        // Animate overlays: slower fade in for clarity and make them even thicker when selected
        Timeline fadeIn = new Timeline();
        Duration fadeInDur = Duration.millis(900);
        for (Node n : tempOverlays) {
            fadeIn.getKeyFrames().add(new KeyFrame(fadeInDur, new KeyValue(n.opacityProperty(), 0.95)));
        }
        // Animate line thickness bump
        for (Node n : tempOverlays) {
            if (n instanceof Line ln) {
                fadeIn.getKeyFrames().add(new KeyFrame(fadeInDur, new KeyValue(ln.strokeWidthProperty(), lineW + 3.0)));
            }
        }

        // Full sequence: fade in overlays, hold longer so the path is very clear
        PauseTransition hold = new PauseTransition(Duration.millis(1400));
        SequentialTransition seq = new SequentialTransition(fadeIn, hold);

        // Start water animation on involved edges immediately
        for (int i = 0; i < waterEdges.size(); i++) {
            EdgeRecord er = waterEdges.get(i);
            boolean rev = waterReverse.get(i) != null && waterReverse.get(i);
            netStartWaterAnim(er, rev);
        }

        seq.setOnFinished(ev -> {
            // Apply augmentation to flow matrix
            for (Seg s : segs) {
                if (!s.back) {
                    run.flow[s.u][s.v] += aug;
                } else {
                    run.flow[s.v][s.u] -= aug;
                }
            }
            run.maxFlow += aug;
            appendNetworkLog("Augmented by " + common.fmt(aug) + "  | Total flow now " + common.fmt(run.maxFlow) + "\n\n");
            applyNetworkFlowToLabels(run.flow, run.idx);
            updateNetworkResidualOverlays(run.flow, run.idx);

            // Fade out overlays then cleanup and continue
            Timeline fadeOut = new Timeline();
            Duration fadeOutDur = Duration.millis(800);
            for (Node n : tempOverlays) {
                fadeOut.getKeyFrames().add(new KeyFrame(fadeOutDur, new KeyValue(n.opacityProperty(), 0.0)));
            }
            fadeOut.setOnFinished(x -> {
                // Stop water animation for these edges; keep a faint idle water look
                for (EdgeRecord er : waterEdges) {
                    netStopWaterAnim(er, true);
                }
                networkCanvas.getChildren().removeAll(tempOverlays);
                PauseTransition pause = new PauseTransition(Duration.millis(280));
                pause.setOnFinished(e2 -> onDone.run());
                pause.play();
            });
            fadeOut.play();
        });
        seq.play();
    }
    
    private void netStartWaterAnim(EdgeRecord e, boolean reverse){
        if (e == null || e.waterLine == null) return;
        try {
            e.waterLine.setOpacity(0.55);
            // Ensure dash pattern exists
            if (e.waterLine.getStrokeDashArray().isEmpty()) {
                e.waterLine.getStrokeDashArray().setAll(18.0, 12.0);
            }
            // Compute a duration proportional to length for a natural flow speed
            double sx = e.waterLine.getStartX(), sy = e.waterLine.getStartY();
            double ex = e.waterLine.getEndX(), ey = e.waterLine.getEndY();
            double len = Math.hypot(ex - sx, ey - sy);
            double periodMs = Math.max(350, Math.min(1400, len * 6.0)); // clamp for consistency
            double toOffset = (reverse ? -1 : 1) * (e.waterLine.getStrokeDashArray().stream().mapToDouble(d->d).sum());
            // Stop existing
            if (e.waterDashAnim != null) { e.waterDashAnim.stop(); }
            e.waterDashAnim = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(e.waterLine.strokeDashOffsetProperty(), 0.0, Interpolator.LINEAR)),
                new KeyFrame(Duration.millis(periodMs), new KeyValue(e.waterLine.strokeDashOffsetProperty(), toOffset, Interpolator.LINEAR))
            );
            e.waterDashAnim.setCycleCount(Animation.INDEFINITE);
            e.waterDashAnim.play();
        } catch (Exception ignored) {}
    }
}

package algobot;

import javafx.animation.*;
import javafx.stage.Screen;
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
import javafx.scene.shape.*;
import javafx.util.Duration;
import java.util.*;

public class MazeManager {
	// Maze state variables for reset functionality
    private TextField currentMazeSizeField;
    private TextField currentStartX, currentStartY, currentEndX, currentEndY;
    private RadioButton currentSlowSpeed, currentNormalSpeed, currentFastSpeed;
    private Button currentSolveBtn;
    private Label currentStatusLabel;
    private Canvas currentMazeCanvas;
    private TextArea currentMazeStepLog;
    
    // Animation tracking for proper reset
    private Timeline currentGenerationTimeline;
    private Timeline currentSolutionTimeline;
    
    
    
	public void createMazePage() {
    	common.mazeContainer = new VBox(15);
    	common.mazeContainer.setAlignment(Pos.TOP_CENTER);
    	common.mazeContainer.setPadding(new Insets(60, 20, 20, 20));
        
        // Dramatic dark gradient background for maze section with neon accents
        LinearGradient gradient = new LinearGradient(0, 0, 1, 1, true, null,
            new Stop(0, Color.web("#0c0c0c")),
            new Stop(0.3, Color.web("#1a0d26")),
            new Stop(0.6, Color.web("#2d1b4a")),
            new Stop(1, Color.web("#0f051d"))
        );
        
        common.mazeContainer.setBackground(new Background(new BackgroundFill(gradient, null, null)));
        
        // Title with back button
        HBox titleContainer = ButtonManager.createTitleWithBackButton("MAZE SOLUTION & DSU");
        
        // Control panel for maze configuration
        VBox controlPanel = new VBox(15);
        // Removed large translate; we'll instead shrink canvas to avoid overlap
        controlPanel.setAlignment(Pos.CENTER);
        controlPanel.setPadding(new Insets(20));
        controlPanel.setStyle(
            "-fx-background-color: rgba(20,25,35,0.95); " +
            "-fx-background-radius: 15; " +
            "-fx-border-color: linear-gradient(to bottom right,#00ffff55,#4ecdc455); " +
            "-fx-border-width: 2; -fx-border-radius: 15; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 15,0,0,5);"
        );
        
        // Maze size configuration (auto animated DSU build - max 20)
        HBox sizeBox = new HBox(15);
        sizeBox.setAlignment(Pos.CENTER);
        Label sizeLabel = new Label("Maze Size (N×N):");
        sizeLabel.setStyle("-fx-text-fill: #00ffff; -fx-font-weight: bold; -fx-font-size: 14px;");
        
        TextField sizeField = new TextField("");
        currentMazeSizeField = sizeField; // Store reference for reset
        sizeField.setPrefWidth(60);
        sizeField.setStyle(
            "-fx-background-color: rgba(15,20,30,0.9); " +
            "-fx-text-fill: white; " +
            "-fx-border-color: #00ffff77; " +
            "-fx-border-radius: 5; " +
            "-fx-background-radius: 5; " +
            "-fx-font-size: 12px;"
        );
        
	    Button generateBtn = ButtonManager.createStyledButton("GENERATE", "#00ffff");
	        sizeBox.getChildren().addAll(sizeLabel, sizeField, generateBtn);
	        
	    // Start / End / Speed / Solve grouping (wrapping layout so buttons never overflow)
	    FlowPane pointsBox = new FlowPane();
	    pointsBox.setHgap(30);
	    pointsBox.setVgap(12);
	    pointsBox.setPrefWrapLength(880); // wrap before exceeding typical width
	    pointsBox.setAlignment(Pos.CENTER);
	        
	    // Start / End inline inputs (row first, then col)
	    VBox startBox = new VBox(5);
	    startBox.setAlignment(Pos.CENTER);
	    Label startLabel = new Label("Start Point");
	    startLabel.setStyle("-fx-text-fill: #00ffff; -fx-font-weight: bold; -fx-font-size: 12px;");
	    HBox startCoords = new HBox(5); startCoords.setAlignment(Pos.CENTER);
	    TextField startRowField = new TextField("");
	    TextField startColField = new TextField("");
	    currentStartX = startRowField; currentStartY = startColField; // reuse existing refs (row->X var name legacy)
	    startRowField.setPrefWidth(50); startColField.setPrefWidth(50);
	    String coordStyle = "-fx-background-color: rgba(15,20,30,0.9); -fx-text-fill: white; -fx-border-color: #00ffff77; -fx-border-radius: 5; -fx-background-radius: 5;";
	    startRowField.setStyle(coordStyle); startColField.setStyle(coordStyle);
	    startCoords.getChildren().addAll(new Label("Row:"), startRowField, new Label("Col:"), startColField);
	    startBox.getChildren().addAll(startLabel, startCoords);
	
	    VBox endBox = new VBox(5);
	    endBox.setAlignment(Pos.CENTER);
	    Label endLabel = new Label("End Point");
	    endLabel.setStyle("-fx-text-fill: #00ffff; -fx-font-weight: bold; -fx-font-size: 12px;");
	    HBox endCoords = new HBox(5); endCoords.setAlignment(Pos.CENTER);
	    TextField endRowField = new TextField("");
	    TextField endColField = new TextField("");
	    currentEndX = endRowField; currentEndY = endColField;
	    endRowField.setPrefWidth(50); endColField.setPrefWidth(50);
	    endRowField.setStyle(coordStyle); endColField.setStyle(coordStyle);
	    endCoords.getChildren().addAll(new Label("Row:"), endRowField, new Label("Col:"), endColField);
	    endBox.getChildren().addAll(endLabel, endCoords);
        
        // Speed control
        VBox speedBox = new VBox(5);
        speedBox.setAlignment(Pos.CENTER);
        Label speedLabel = new Label("Animation Speed");
        speedLabel.setStyle("-fx-text-fill: #00ffff; -fx-font-weight: bold; -fx-font-size: 12px;");
        
        HBox speedControls = new HBox(5);
        speedControls.setAlignment(Pos.CENTER);
        
        ToggleGroup speedGroup = new ToggleGroup();
        RadioButton slowSpeed = new RadioButton("Slow");
        RadioButton normalSpeed = new RadioButton("Normal");
        RadioButton fastSpeed = new RadioButton("Fast");
        
        // Store references for reset
        currentSlowSpeed = slowSpeed;
        currentNormalSpeed = normalSpeed;
        currentFastSpeed = fastSpeed;
        
        slowSpeed.setToggleGroup(speedGroup);
        normalSpeed.setToggleGroup(speedGroup);
        fastSpeed.setToggleGroup(speedGroup);
        normalSpeed.setSelected(true); // Default speed
        
        String radioStyle = "-fx-text-fill: white; -fx-font-size: 10px;";
        slowSpeed.setStyle(radioStyle);
        normalSpeed.setStyle(radioStyle);
        fastSpeed.setStyle(radioStyle);
        
        speedControls.getChildren().addAll(slowSpeed, normalSpeed, fastSpeed);
        speedBox.getChildren().addAll(speedLabel, speedControls);
        
	    Button solveBtn = ButtonManager.createStyledButton("SOLVE", "#00ffff");
	    currentSolveBtn = solveBtn; // store ref
	    solveBtn.setDisable(true);
	    pointsBox.getChildren().addAll(startBox, endBox, speedBox, generateBtn, solveBtn);
	    FlowPane.setMargin(generateBtn, new Insets(18,0,0,0));
	    FlowPane.setMargin(solveBtn, new Insets(18,0,0,0));
	        
	    controlPanel.getChildren().addAll(sizeBox, pointsBox);
	    controlPanel.setMaxWidth(1000);
        
        // Maze area split: canvas (left) + step log (right)
            HBox mazeWorkArea = new HBox(25);
            mazeWorkArea.setAlignment(Pos.TOP_CENTER);

            VBox canvasContainer = new VBox(10);
            canvasContainer.setAlignment(Pos.TOP_CENTER);
        
	    // Responsive smaller canvas so it does not overlap the control panel.
	    double visualW = Screen.getPrimary().getVisualBounds().getWidth();
	    double visualH = Screen.getPrimary().getVisualBounds().getHeight();
	    double maxSide = Math.min(600, Math.min(visualW * 0.55, visualH * 0.55));
	    if (maxSide < 400) maxSide = 400; // maintain minimum clarity
	    Canvas mazeCanvas = new Canvas(maxSide, maxSide);
        currentMazeCanvas = mazeCanvas; // Store reference for reset
        mazeCanvas.setStyle(
            "-fx-background-color: rgba(15,20,30,0.95); " +
            "-fx-background-radius: 10; " +
            "-fx-border-color: #00ffff77; " +
            "-fx-border-width: 2; " +
            "-fx-border-radius: 10; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,255,255,0.3), 10,0,0,3);"
        );
        
        Label statusLabel = new Label("enter size <=20 to create grid");
        currentStatusLabel = statusLabel; // Store reference for reset
        statusLabel.setStyle("-fx-text-fill: #b0e0ff; -fx-font-size: 14px; -fx-font-weight: bold;");
        
	    // Step log on right
	    VBox logContainer = new VBox(8);
	    logContainer.setAlignment(Pos.TOP_LEFT);
	    // Shift the log a bit further right visually
	    logContainer.setPadding(new Insets(0,0,0,10));
	    logContainer.setTranslateX(10);
	    Label logTitle = new Label("Generation & Solution Steps");
	    logTitle.setStyle("-fx-text-fill:#00ffff; -fx-font-size:13px; -fx-font-weight:bold;");
	    TextArea stepLog = new TextArea();
	    stepLog.setEditable(false);
	    stepLog.setWrapText(true);
	    stepLog.setPrefColumnCount(28);
	    stepLog.setPrefRowCount(18); // cap visible height
	    stepLog.setMaxHeight(18 * 20); // approximate row height * rows
	    stepLog.setStyle("-fx-control-inner-background: rgba(15,20,30,0.95); -fx-text-fill: #b0e0ff; -fx-font-size:14px; -fx-border-color:#00ffff55; -fx-border-radius:8; -fx-background-radius:8;");
	    currentMazeStepLog = stepLog;
	    Button clearLogBtn = ButtonManager.createStyledButton("CLEAR LOG", "#00ffff");
	    clearLogBtn.setOnAction(ev-> stepLog.clear());
	    logContainer.getChildren().addAll(logTitle, stepLog, clearLogBtn);
	
	    canvasContainer.getChildren().addAll(mazeCanvas, statusLabel);
	    mazeWorkArea.getChildren().addAll(canvasContainer, logContainer);
	        
	        // Initialize maze variables
	    final int[] mazeSize = {0};
	    final boolean[][][][] mazeWalls = {null};
	    final int[] startPoint = {-1,-1};
	    final int[] endPoint = {-1,-1};
        
        // Generate maze button action
        // Enable generate button only when required fields filled
        Runnable checkFilled = () -> {
            boolean ok = !sizeField.getText().trim().isEmpty() &&
                         !startRowField.getText().trim().isEmpty() && !startColField.getText().trim().isEmpty() &&
                         !endRowField.getText().trim().isEmpty() && !endColField.getText().trim().isEmpty();
            generateBtn.setDisable(!ok);
        };
        generateBtn.setDisable(true);
        sizeField.textProperty().addListener((o,a,b)->checkFilled.run());
        startRowField.textProperty().addListener((o,a,b)->checkFilled.run());
        startColField.textProperty().addListener((o,a,b)->checkFilled.run());
        endRowField.textProperty().addListener((o,a,b)->checkFilled.run());
        endColField.textProperty().addListener((o,a,b)->checkFilled.run());

        generateBtn.setOnAction(e -> {
            try {
                if (currentGenerationTimeline != null) { currentGenerationTimeline.stop(); }
                int size = Integer.parseInt(sizeField.getText().trim());
                if (size < 2) { statusLabel.setText("Size must be >=2"); statusLabel.setTextFill(Color.web("#ff6b6b")); return; }
                if (size > 20) { statusLabel.setText("Max size is 20"); statusLabel.setTextFill(Color.web("#ff6b6b")); return; }
                int sRow = Integer.parseInt(startRowField.getText().trim());
                int sCol = Integer.parseInt(startColField.getText().trim());
                int eRow = Integer.parseInt(endRowField.getText().trim());
                int eCol = Integer.parseInt(endColField.getText().trim());
                if (sRow<0||sRow>=size||sCol<0||sCol>=size||eRow<0||eRow>=size||eCol<0||eCol>=size){
                    statusLabel.setText("Row/Col must be 0.."+(size-1)); statusLabel.setTextFill(Color.web("#ff6b6b")); return; }
                mazeSize[0]=size;
                startPoint[0]=sCol; startPoint[1]=sRow; endPoint[0]=eCol; endPoint[1]=eRow;
                mazeWalls[0] = new boolean[size][size][4];
                for(int x=0;x<size;x++) for(int y=0;y<size;y++) Arrays.fill(mazeWalls[0][x][y], true);
                // carve openings at start and end if on boundary
                carveBoundaryOpening(mazeWalls[0], size, startPoint[0], startPoint[1]);
                carveBoundaryOpening(mazeWalls[0], size, endPoint[0], endPoint[1]);
                stepLog.clear();
                stepLog.appendText("Initialized " + size + "x" + size + " grid.\n");
                stepLog.appendText("Start: (row="+sRow+", col="+sCol+")  End: (row="+eRow+", col="+eCol+")\n");
                if (isBoundary(startPoint,size)) stepLog.appendText("Opened boundary at start.\n");
                if (isBoundary(endPoint,size)) stepLog.appendText("Opened boundary at end.\n");
                drawMaze(mazeCanvas, mazeWalls[0], size, startPoint, endPoint, null);
                statusLabel.setText("Generating maze (DSU Kruskal)..."); statusLabel.setTextFill(Color.web("#00ffff"));
                solveBtn.setDisable(true); // keep solve off during generation
                generateBtn.setDisable(true);
                int genSpeed; // refined faster overall speeds (smaller millis -> faster animation)
                if (slowSpeed.isSelected()) genSpeed = 450; else if (fastSpeed.isSelected()) genSpeed = 60; else genSpeed = 250;
                startAnimatedMazeGenerationWithLog(mazeCanvas, mazeWalls[0], size, startPoint, endPoint, statusLabel, genSpeed, stepLog, () -> {
                    statusLabel.setText("Maze generated. Click SOLVE to visualize path.");
                    stepLog.appendText("Maze generation complete.\n");
                    solveBtn.setDisable(false);
                    generateBtn.setDisable(false);
                });
            } catch(NumberFormatException exNum){
                statusLabel.setText("Invalid numbers."); statusLabel.setTextFill(Color.web("#ff6b6b"));
            }
        });

        solveBtn.setOnAction(ev -> {
            if (mazeSize[0] <= 0 || mazeWalls[0]==null) return;
            stepLog.appendText("Starting BFS from start to end...\n");
            List<int[]> path = solveMaze(mazeWalls[0], mazeSize[0], startPoint, endPoint);
            if (path == null) { statusLabel.setText("No path found."); statusLabel.setTextFill(Color.web("#ff6b6b")); return; }
            int animSpeed; if (slowSpeed.isSelected()) animSpeed=500; else if (fastSpeed.isSelected()) animSpeed=150; else animSpeed=300;
            statusLabel.setText("Solving..."); statusLabel.setTextFill(Color.web("#00ffff"));
            stepLog.appendText("Path length: "+path.size()+". Animating solution...\n");
            animateBallSolution(mazeCanvas, mazeWalls[0], mazeSize[0], startPoint, endPoint, path, statusLabel, animSpeed);
        });
        
	    // removed popup start/end selection (now provided beforehand)
	        
	        // Initial canvas empty until user creates grid
	        GraphicsContext gcInit = mazeCanvas.getGraphicsContext2D();
	        gcInit.clearRect(0,0,mazeCanvas.getWidth(),mazeCanvas.getHeight());
	
	    mazeCanvas.setOnMouseClicked(null); // disable manual editing
	        
	    // Add an extra spacer before control panel to ensure it's fully visible under any top bar
	    Region mazeTopSpacer = new Region();
	    mazeTopSpacer.setPrefHeight(10);
	    common.mazeContainer.getChildren().addAll(titleContainer, mazeTopSpacer, controlPanel, mazeWorkArea);
	    VBox.setMargin(mazeWorkArea, new Insets(20,0,0,0));
    }
	
	// Carve an exit only at the user-defined end cell if it lies on the boundary
    private void carveBoundaryOpening(boolean[][][] walls, int size, int cx, int cy) {
        if (cx < 0 || cy < 0 || cx >= size || cy >= size) return;
        if (cy == 0) walls[cx][cy][0] = false;       // top edge -> open north
        if (cy == size - 1) walls[cx][cy][2] = false; // bottom edge -> open south
        if (cx == size - 1) walls[cx][cy][1] = false; // right edge -> open east
        if (cx == 0) walls[cx][cy][3] = false;        // left edge -> open west
    }
    
    private boolean isBoundary(int[] pt, int size){
        return pt[0]==0 || pt[0]==size-1 || pt[1]==0 || pt[1]==size-1;
    }
    
    // Draw the maze on canvas
    private void drawMaze(Canvas canvas, boolean[][][] walls, int size, int[] start, int[] end, int[] ballPos) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        
        double cellSize = Math.min(canvas.getWidth(), canvas.getHeight()) / size;
        double offsetX = (canvas.getWidth() - cellSize * size) / 2;
        double offsetY = (canvas.getHeight() - cellSize * size) / 2;
        
        // Draw grid background (dark theme)
        gc.setFill(Color.web("#1a1a1a"));
        gc.fillRect(offsetX, offsetY, cellSize * size, cellSize * size);
        
        // Draw walls (cyan theme)
        gc.setStroke(Color.web("#00ffff"));
        gc.setLineWidth(3);
        
        // Draw walls with glow effect (cyan theme)
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                double cellX = offsetX + x * cellSize;
                double cellY = offsetY + y * cellSize;
                
                // Draw glow effect first (thicker, more transparent)
                gc.setStroke(Color.web("#00ffff", 0.3));
                gc.setLineWidth(6);
                
                if (walls[x][y][0]) { // North wall glow
                    gc.strokeLine(cellX, cellY, cellX + cellSize, cellY);
                }
                if (walls[x][y][1]) { // East wall glow
                    gc.strokeLine(cellX + cellSize, cellY, cellX + cellSize, cellY + cellSize);
                }
                if (walls[x][y][2]) { // South wall glow
                    gc.strokeLine(cellX, cellY + cellSize, cellX + cellSize, cellY + cellSize);
                }
                if (walls[x][y][3]) { // West wall glow
                    gc.strokeLine(cellX, cellY, cellX, cellY + cellSize);
                }
                
                // Draw main walls (normal thickness, full opacity)
                gc.setStroke(Color.web("#00ffff"));
                gc.setLineWidth(3);
                
                if (walls[x][y][0]) { // North wall
                    gc.strokeLine(cellX, cellY, cellX + cellSize, cellY);
                }
                if (walls[x][y][1]) { // East wall
                    gc.strokeLine(cellX + cellSize, cellY, cellX + cellSize, cellY + cellSize);
                }
                if (walls[x][y][2]) { // South wall
                    gc.strokeLine(cellX, cellY + cellSize, cellX + cellSize, cellY + cellSize);
                }
                if (walls[x][y][3]) { // West wall
                    gc.strokeLine(cellX, cellY, cellX, cellY + cellSize);
                }
            }
        }
        
        // Draw moving greenish ball
        if (ballPos != null) {
            // Draw glow effect
            gc.setFill(Color.web("#98CD00", 0.3));
            double ballX = offsetX + ballPos[0] * cellSize + cellSize / 2;
            double ballY = offsetY + ballPos[1] * cellSize + cellSize / 2;
            gc.fillOval(ballX - 8, ballY - 8, 16, 16);
            
            // Draw main ball
            gc.setFill(Color.web("#98CD00"));
            gc.fillOval(ballX - 5, ballY - 5, 10, 10);
        }
    }
    
    // Extended version with step logging
    private void startAnimatedMazeGenerationWithLog(Canvas canvas, boolean[][][] walls, int size, int[] start, int[] end,
                                                    Label statusLabel, int stepMillis, TextArea log, Runnable onComplete) {
        // Build wall list (east & south) like generateMaze
        List<int[]> wallList = new ArrayList<>();
        for (int x=0;x<size;x++) {
            for (int y=0;y<size;y++) {
                if (x < size-1) wallList.add(new int[]{x,y,1});
                if (y < size-1) wallList.add(new int[]{x,y,2});
            }
        }
        Collections.shuffle(wallList);
        int[] parent = new int[size*size];
        for (int i=0;i<parent.length;i++) parent[i]=i;
        // Draw initial grid
        drawMaze(canvas, walls, size, start, end, null);
        // Timeline for steps
        Timeline tl = new Timeline();
        
        // Store timeline for reset functionality
        currentGenerationTimeline = tl;
        final int[] index = {0};
        KeyFrame kf = new KeyFrame(Duration.millis(stepMillis), ev -> {
            if (index[0] >= wallList.size()) {
                tl.stop();
                statusLabel.setText("Maze generated. Solving...");
                if (onComplete!=null) onComplete.run();
                return;
            }
            int[] wall = wallList.get(index[0]++);
            int x = wall[0], y = wall[1], dir = wall[2];
            int cell1 = y*size + x;
            int cell2 = (dir==1)? cell1+1 : cell1+size;
            if (find(parent, cell1) != find(parent, cell2)) {
                union(parent, cell1, cell2);
                walls[x][y][dir] = false;
                if (dir==1) walls[x+1][y][3] = false; else walls[x][y+1][0] = false;
                // Visual emphasis: redraw
                drawMaze(canvas, walls, size, start, end, null);
                if (log!=null){
                    String dirName = (dir==1?"EAST":"SOUTH");
                    log.appendText("Removed "+dirName+" wall at ("+y+","+x+") connecting cells -> components merged.\n");
                }
            }
        });
        
        tl.getKeyFrames().add(kf);
        tl.setCycleCount(Animation.INDEFINITE);
        tl.play();
    }
    
    private int find(int[] parent, int x) {
        if (parent[x] != x) parent[x] = find(parent, parent[x]);
        return parent[x];
    }
    
    private void union(int[] parent, int x, int y) {
        parent[find(parent, x)] = find(parent, y);
    }
    
 // Solve maze using BFS
    private List<int[]> solveMaze(boolean[][][] walls, int size, int[] start, int[] end) {
        Queue<int[]> queue = new LinkedList<>();
        boolean[][] visited = new boolean[size][size];
        int[][][] parent = new int[size][size][2];
        
        queue.offer(new int[]{start[0], start[1]});
        visited[start[0]][start[1]] = true;
        parent[start[0]][start[1]] = new int[]{-1, -1};
        
        int[] dx = {0, 1, 0, -1}; // North, East, South, West
        int[] dy = {-1, 0, 1, 0};
        
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int x = current[0], y = current[1];
            
            if (x == end[0] && y == end[1]) {
                // Reconstruct path
                List<int[]> path = new ArrayList<>();
                int px = x, py = y;
                while (px != -1 && py != -1) {
                    path.add(0, new int[]{px, py});
                    int[] p = parent[px][py];
                    px = p[0];
                    py = p[1];
                }
                return path;
            }
            
            // Check all four directions
            for (int dir = 0; dir < 4; dir++) {
                if (!walls[x][y][dir]) { // No wall in this direction
                    int nx = x + dx[dir];
                    int ny = y + dy[dir];
                    
                    if (nx >= 0 && nx < size && ny >= 0 && ny < size && !visited[nx][ny]) {
                        visited[nx][ny] = true;
                        parent[nx][ny] = new int[]{x, y};
                        queue.offer(new int[]{nx, ny});
                    }
                }
            }
        }
        
        return null; // No path found
    }
    
 // Animate ball solving the maze
    private void animateBallSolution(Canvas canvas, boolean[][][] walls, int size, int[] start, int[] end, 
                                   List<int[]> path, Label statusLabel, int animationSpeed) {
        Timeline timeline = new Timeline();
        
        // Store timeline for reset functionality
        currentSolutionTimeline = timeline;
        
        for (int i = 0; i < path.size(); i++) {
            final int index = i;
            KeyFrame keyFrame = new KeyFrame(Duration.millis(i * animationSpeed), e -> {
                int[] ballPos = path.get(index);
                // Redraw maze base
                drawMaze(canvas, walls, size, start, end, null);

                // Draw solved path so far (cells 0..index) in cyan
                GraphicsContext gc = canvas.getGraphicsContext2D();
                double cellSize = Math.min(canvas.getWidth(), canvas.getHeight()) / size;
                double offsetX = (canvas.getWidth() - cellSize * size) / 2;
                double offsetY = (canvas.getHeight() - cellSize * size) / 2;
                double inset = Math.max(2, cellSize * 0.12); // leave a margin so walls stay visible

                gc.setFill(Color.web("#98CD00"));
                gc.setStroke(Color.web("#98CD00"));
                gc.setLineWidth(Math.max(2, cellSize * 0.15));

                double arc = Math.max(6, cellSize * 0.45); // large corner radius for rounded cells
                for (int p = 0; p <= index; p++) {
                    int[] c = path.get(p);
                    double x = offsetX + c[0] * cellSize + inset/2;
                    double y = offsetY + c[1] * cellSize + inset/2;
                    double sz = cellSize - inset;
                    gc.fillRoundRect(x, y, sz, sz, arc, arc);
                }

                // Optionally connect centers with thick polyline for smoother corridor
                gc.setStroke(Color.web("#00ffff"));
                gc.setLineCap(StrokeLineCap.ROUND);
                gc.setLineJoin(StrokeLineJoin.ROUND);
                gc.setLineWidth(Math.max(4, cellSize * 0.35));
                for (int p = 1; p <= index; p++) {
                    int[] a = path.get(p-1);
                    int[] b = path.get(p);
                    double ax = offsetX + a[0]*cellSize + cellSize/2;
                    double ay = offsetY + a[1]*cellSize + cellSize/2;
                    double bx = offsetX + b[0]*cellSize + cellSize/2;
                    double by = offsetY + b[1]*cellSize + cellSize/2;
                    gc.strokeLine(ax, ay, bx, by);
                }

                // Draw moving cyan ball with glow on top
                GraphicsContext gcb = gc;
                double ballX = offsetX + ballPos[0] * cellSize + cellSize / 2;
                double ballY = offsetY + ballPos[1] * cellSize + cellSize / 2;
                gcb.setFill(Color.web("#00ffff", 0.25));
                gcb.fillOval(ballX - cellSize*0.45, ballY - cellSize*0.45, cellSize*0.9, cellSize*0.9);
                gcb.setFill(Color.web("#00ffff"));
                double r = Math.max(6, cellSize * 0.22);
                gcb.fillOval(ballX - r/2, ballY - r/2, r, r);
                
                if (index == path.size() - 1) {
                    statusLabel.setText("🎉 Ball reached the exit! Maze solved!");
                    statusLabel.setTextFill(Color.web("#00ffff"));
                }
            });
            timeline.getKeyFrames().add(keyFrame);
        }
        
        timeline.play();
    }
    
    public void resetMazeState() {
        // Stop any running animations
        if (currentGenerationTimeline != null) {
            currentGenerationTimeline.stop();
            currentGenerationTimeline = null;
        }
        if (currentSolutionTimeline != null) {
            currentSolutionTimeline.stop();
            currentSolutionTimeline = null;
        }
        
        if (currentMazeSizeField != null) {
            currentMazeSizeField.setText("10");
        }
        if (currentStartX != null) {
            currentStartX.clear();
        }
        if (currentStartY != null) {
            currentStartY.clear();
        }
        if (currentEndX != null) {
            currentEndX.clear();
        }
        if (currentEndY != null) {
            currentEndY.clear();
        }
        if (currentNormalSpeed != null) {
            currentNormalSpeed.setSelected(true);
        }
        if (currentStatusLabel != null) {
            currentStatusLabel.setText("enter size <=20 to create grid");
            currentStatusLabel.setTextFill(Color.web("#00ffff"));
        }
        if (currentSolveBtn != null) {
            currentSolveBtn.setDisable(true); // Will be enabled by field listeners when fields are filled
        }
        if (currentMazeCanvas != null) {
            GraphicsContext gc = currentMazeCanvas.getGraphicsContext2D();
            gc.clearRect(0, 0, currentMazeCanvas.getWidth(), currentMazeCanvas.getHeight());
        }
        if (currentMazeStepLog != null) {
            currentMazeStepLog.clear();
        }
    }
}

package algobot;

import javafx.scene.control.Label;
import javafx.scene.shape.Circle;

public class GraphNode {
    private String id;
    private double x;
    private double y;
    private Circle circle;
    private Label label;
    private boolean visited;
    private double distance;
    private GraphNode previous;
    
    public GraphNode(String id, double x, double y) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.visited = false;
        this.distance = Double.POSITIVE_INFINITY;
        this.previous = null;
    }
    
    // Legacy constructor for integer IDs
    public GraphNode(int id, double x, double y) {
        this(String.valueOf(id), x, y);
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    
    public Circle getCircle() { return circle; }
    public void setCircle(Circle circle) { this.circle = circle; }
    
    public Label getLabel() { return label; }
    public void setLabel(Label label) { this.label = label; }
    
    public boolean isVisited() { return visited; }
    public void setVisited(boolean visited) { this.visited = visited; }
    
    public double getDistance() { return distance; }
    public void setDistance(double distance) { this.distance = distance; }
    
    public GraphNode getPrevious() { return previous; }
    public void setPrevious(GraphNode previous) { this.previous = previous; }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        GraphNode node = (GraphNode) obj;
        return id.equals(node.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
    
    @Override
    public String toString() {
        return "Node " + id;
    }
}

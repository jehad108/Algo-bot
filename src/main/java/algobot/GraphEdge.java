package algobot;

import javafx.scene.control.Label;
import javafx.scene.shape.Line;

public class GraphEdge {
    private GraphNode from;
    private GraphNode to;
    private double weight;
    private boolean directed;
    private Line line;
    private Label weightLabel;
    
    public GraphEdge(GraphNode from, GraphNode to, double weight, boolean directed) {
        this.from = from;
        this.to = to;
        this.weight = weight;
        this.directed = directed;
    }
    
    // Getters and setters
    public GraphNode getFrom() { return from; }
    public void setFrom(GraphNode from) { this.from = from; }
    
    public GraphNode getTo() { return to; }
    public void setTo(GraphNode to) { this.to = to; }
    
    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }
    
    public boolean isDirected() { return directed; }
    public void setDirected(boolean directed) { this.directed = directed; }
    
    public Line getLine() { return line; }
    public void setLine(Line line) { this.line = line; }
    
    public Label getWeightLabel() { return weightLabel; }
    public void setWeightLabel(Label weightLabel) { this.weightLabel = weightLabel; }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        GraphEdge edge = (GraphEdge) obj;
        return from.equals(edge.from) && to.equals(edge.to);
    }
    
    @Override
    public int hashCode() {
        return from.hashCode() + to.hashCode();
    }
    
    @Override
    public String toString() {
        return from + " -> " + to + " (weight: " + weight + ")";
    }
}

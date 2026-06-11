package org.example.graph;

import org.example.model.Edge;
import org.example.model.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public abstract class ProcessGraph {

    private String graphName;

    private final List<Node> nodes;
    private final List<Edge> edges;

    private HashMap<Node, List<Edge>> outgoingEdges;
    private HashMap<Node, List<Edge>> incomingEdges;

    public ProcessGraph(List<Node> nodes, List<Edge> edges) {
        this.nodes = nodes;
        this.edges = edges;

        outgoingEdges = new HashMap<>();
        incomingEdges = new HashMap<>();

        for (Node node : nodes) {
            List<Edge> out = new ArrayList<>();
            List<Edge> in = new ArrayList<>();
            for (Edge edge : edges) {
                // TODO: sourceKey
                if (Objects.equals(edge.getSourceKey(), node.getKey())) {
                    out.add(edge);
                }
                if (Objects.equals(edge.getTargetKey(), node.getKey())) {
                    in.add(edge);
                }
            }
            outgoingEdges.put(node, out);
            incomingEdges.put(node, in);
        }

        this.setNodeRoles();

    }

    // setNodeRole irgendwo
    // this method can only be used in this class, and will be automatically run by constructor
    private void setNodeRoles() {

    }

    public List<Node> getNodes() {
        return nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public HashMap<Node, List<Edge>> getOutgoingEdges() {
        return outgoingEdges;
    }

    public void setOutgoingEdges(HashMap<Node, List<Edge>> outgoingEdges) {
        this.outgoingEdges = outgoingEdges;
    }

    public HashMap<Node, List<Edge>> getIncomingEdges() {
        return incomingEdges;
    }

    public void setIncomingEdges(HashMap<Node, List<Edge>> incomingEdges) {
        this.incomingEdges = incomingEdges;
    }
}

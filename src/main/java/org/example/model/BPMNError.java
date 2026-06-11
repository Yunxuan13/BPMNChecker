package org.example.model;

import java.util.List;

public class BPMNError {

    private final String errorId;
    private final String errorName;
    private final String errorCategory;
    // scope = "Main"
    // scope = "SubprocessId1"
    private final String scope;
    private final String message;
    private final List<Node> nodes;
    private final List<Edge> edges;
    private Severity severity;

    public BPMNError(String errorId, String errorName, String errorCategory, String scope, String message, List<Node> nodes, List<Edge> edges, Severity severity) {
        this.errorId = errorId;
        this.errorName = errorName;
        this.errorCategory = errorCategory;
        this.scope = scope;
        this.message = message;
        this.nodes = nodes;
        this.edges = edges;
        this.severity = severity;
    }

    public String getErrorId() {
        return errorId;
    }

    public String getErrorName() {
        return errorName;
    }

    public String getErrorCategory() {
        return errorCategory;
    }

    public String getMessage() {
        return message;
    }

    public String getScope() {
        return scope;
    }

    public List<Node> getNode() {
        return nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }
}

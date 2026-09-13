package org.example.model;

import java.util.ArrayList;
import java.util.List;

public class Node {

    private final String id;
    private final String fullName;
    private final NodeType type;
    private String label;
    private final RawShape rawShape;
    private boolean isGateway = false;
    private String location;

    private List<Edge> incomingEdges;
    private List<Edge> outgoingEdges;


    public Node(String id, String fullName, NodeType type, String label, RawShape rawShape, String location) {
        this.fullName = fullName;
        this.id = id;
        this.label = label;
        this.rawShape = rawShape;
        this.type = type;
        if (type == NodeType.EXCLUSIVEGATEWAY || type == NodeType.INCLUSIVEGATEWAY || type == NodeType.PARALLELGATEWAY) {
            this.isGateway = true;
        }
        this.location = location;
        this.outgoingEdges = new ArrayList<>();
        this.incomingEdges = new ArrayList<>();
    }

    public String getKey() {
        return this.id + ":" + this.type.name().toLowerCase();
    }

    public String toString() {
        String left = "";
        String right = "";
        String before = id + ":" + type.name().toLowerCase() + ":";
        switch (type) {
            case SUBGRAPH -> {
                return "subgraph " + id + " [" + label + "]";
            }
            case EXCLUSIVEGATEWAY, PARALLELGATEWAY, INCLUSIVEGATEWAY -> {
                left = "{";
                right = "}";
            }
            case ENDEVENT -> {
                left = "(((";
                right = ")))";
            }
            case SUBPROCESS, TASK -> {
                left = "(";
                right = ")";
            }
            case STARTEVENT -> {
                left = "((";
                right = "))";
            }
        }
        return before + left + label + right;
    }

    public String getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public NodeType getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public RawShape getRawShape() {
        return rawShape;
    }

    public boolean isGateway() {
        return isGateway;
    }

    public void setGateway(boolean gateway) {
        isGateway = gateway;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<Edge> getIncomingEdges() {
        return incomingEdges;
    }

    public void setIncomingEdges(List<Edge> incomingEdges) {
        this.incomingEdges = incomingEdges;
    }

    public List<Edge> getOutgoingEdges() {
        return outgoingEdges;
    }

    public void setOutgoingEdges(List<Edge> outgoingEdges) {
        this.outgoingEdges = outgoingEdges;
    }
}

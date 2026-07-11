package org.example.model;

import java.util.ArrayList;
import java.util.List;

public class Node {

    // as a subprocess-node, index could be characters
    // here id = "id:type"
    private final String id;
    private final String fullName;
    private final NodeType type;
    private String label;
    private final RawShape rawShape;
    private boolean isGateway = false;
    private boolean expandedSubprocess = false;
    // main process or any subprocess
    private String location;
    private List<Edge> incomingEdges;
    private List<Edge> outgoingEdges;

    // can only be added later, will not be in the constructor
    // nodes should only be created by parsexxxx
    private List<Role> roles;


    public Node(String id, String fullName, NodeType type, String label, RawShape rawShape, String location) {
        this.fullName = fullName;
        this.id = id;
        this.label = label;
        this.rawShape = rawShape;
        this.type = type;
        if (type == NodeType.EXCLUSIVEGATEWAY || type == NodeType.INCLUSIVEGATEWAY || type == NodeType.PARALLELGATEWAY) {
            this.isGateway = true;
        }
        // should ensure there must be at least one role(?)
        this.roles = new ArrayList<>();
        this.location = location;
    }

    public String getKey() {
        return this.id + ":" + this.type.name().toLowerCase();
        // subprocess then: sub_id:subprocess
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

    public List<Role> getRoles() {
        return roles;
    }

    public void setRoles(List<Role> roles) {
        this.roles = roles;
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

    public boolean isExpandedSubprocess() {
        return expandedSubprocess;
    }

    public void setExpandedSubprocess(boolean expandedSubprocess) {
        this.expandedSubprocess = expandedSubprocess;
    }
}

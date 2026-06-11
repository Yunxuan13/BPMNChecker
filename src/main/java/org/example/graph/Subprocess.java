package org.example.graph;

import org.example.model.Edge;
import org.example.model.Node;

import java.util.List;

public class Subprocess extends ProcessGraph {

    private final ProcessGraph parentProcess;
    private final String subId;
    private final String displayName;

    public Subprocess(List<Node> nodes, List<Edge> edges, ProcessGraph parent, String subId, String displayName) {
        super(nodes, edges);
        this.parentProcess = parent;
        this.subId = subId;
        this.displayName = displayName;
    }

    public ProcessGraph getParentProcess() {
        return parentProcess;
    }

    public String getSubId() {
        return subId;
    }

    public String getDisplayName() {
        return displayName;
    }
}

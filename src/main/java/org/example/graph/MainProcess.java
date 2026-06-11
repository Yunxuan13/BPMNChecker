package org.example.graph;

import org.example.model.Edge;
import org.example.model.Node;

import java.util.List;

public class MainProcess extends ProcessGraph{

    // can include subprocesses

    public MainProcess(List<Node> nodes, List<Edge> edges) {
        super(nodes, edges);
    }

}

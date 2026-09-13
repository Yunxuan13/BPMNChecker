package org.example.checker;

import org.example.model.Edge;
import org.example.model.Node;

import java.util.LinkedHashMap;
import java.util.List;

public record TokenLabel(int branchIndex, List<Edge> history, LinkedHashMap<Node, Integer> splits) {

}

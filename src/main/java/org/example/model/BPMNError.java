package org.example.model;

import java.util.List;

public record BPMNError(String errorId, String errorName, String errorCategory, String scope, String message,
                        List<Node> nodes, List<Edge> edges, Severity severity) {

}

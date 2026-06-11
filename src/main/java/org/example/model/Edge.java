package org.example.model;

import java.util.Objects;

public class Edge {
    // instead of using Node as type, i try to use sourceIndex/fullname
    // while writing "parsing" part, this kind can be easier

    // a node's unique identifier is id:type
    // if node is a subprocess:
    private final String sourceKey;
    private final String targetKey;
    private String condition;

    public Edge(String sourceKey, String condition, String targetKey) {
        this.sourceKey = sourceKey;
        this.condition = condition;
        this.targetKey = targetKey;
    }

    public Edge(String sourceName, String targetKey) {
        this.sourceKey = sourceName;
        this.targetKey = targetKey;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public String getTargetKey() {
        return targetKey;
    }

    public String getCondition() {
        return condition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Edge edge = (Edge) o;
        return Objects.equals(sourceKey, edge.sourceKey) && Objects.equals(targetKey, edge.targetKey) && Objects.equals(condition, edge.condition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceKey, targetKey, condition);
    }
}

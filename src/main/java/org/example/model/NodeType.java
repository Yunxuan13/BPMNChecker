package org.example.model;

public enum NodeType {
    // whole subprocess can be treated as a Node
    // inner part as a graph
    STARTEVENT,
    ENDEVENT,
    TASK,
    EXCLUSIVEGATEWAY,
    PARALLELGATEWAY,
    INCLUSIVEGATEWAY,

    // special case: if subprocess as inline-node
    SUBPROCESS,
    // special case: subgraph block
    SUBGRAPH
}

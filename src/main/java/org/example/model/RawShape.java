package org.example.model;

public enum RawShape {
    // represent for shape
    // (())      ((()))    ()     {}                                                    []
    STARTEVENT,
    ENDEVENT,
    // actually task and inline-subprocess have the same shape
    TASKORSUBPROCESS,
    GATEWAY,

    // for subgraph
    SUBGRAPH
}

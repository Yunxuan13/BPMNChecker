package org.example.checker;

import org.example.model.Edge;
import org.example.model.Node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class TokenLabel {

    // private List<Node> tokenBirth;

    // private String tokenBirthNodeKey;
    // private Edge edge;
    private int branchIndex;
    private List<Edge> history;
    // TODO：为了方便找分裂点
    private LinkedHashMap<Node, Integer> splits;

    public TokenLabel(int branchIndex, List<Edge> history, LinkedHashMap<Node, Integer> splits) {
        //this.tokenBirth = tokenBirth;
        this.branchIndex = branchIndex;
        // this.edge = edge;
        this.history = history;
        this.splits = splits;
    }


    public int getBranchIndex() {
        return branchIndex;
    }

    public void setBranchIndex(int branchIndex) {
        this.branchIndex = branchIndex;
    }

//    public Edge getEdge() {
//        return edge;
//    }
//
//    public void setEdge(Edge edge) {
//        this.edge = edge;
//    }

    public List<Edge> getHistory() {
        return history;
    }

    public void setHistory(List<Edge> history) {
        this.history = history;
    }

//    public List<Node> getTokenBirth() {
//        return tokenBirth;
//    }
//
//    public void setTokenBirth(List<Node> tokenBirth) {
//        this.tokenBirth = tokenBirth;
//    }

    public LinkedHashMap<Node, Integer> getSplits() {
        return splits;
    }

    public void setSplits(LinkedHashMap<Node, Integer> splits) {
        this.splits = splits;
    }


}

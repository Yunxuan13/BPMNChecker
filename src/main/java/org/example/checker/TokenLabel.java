package org.example.checker;

import org.example.model.Edge;
import org.example.model.Node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class TokenLabel {

    private int branchIndex;
    private List<Edge> history;
    private LinkedHashMap<Node, Integer> splits;

    public TokenLabel(int branchIndex, List<Edge> history, LinkedHashMap<Node, Integer> splits) {
        this.branchIndex = branchIndex;
        this.history = history;
        this.splits = splits;
    }


    public int getBranchIndex() {
        return branchIndex;
    }

    public void setBranchIndex(int branchIndex) {
        this.branchIndex = branchIndex;
    }


    public List<Edge> getHistory() {
        return history;
    }

    public void setHistory(List<Edge> history) {
        this.history = history;
    }

    public LinkedHashMap<Node, Integer> getSplits() {
        return splits;
    }

    public void setSplits(LinkedHashMap<Node, Integer> splits) {
        this.splits = splits;
    }
}

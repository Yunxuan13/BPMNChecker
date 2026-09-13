package org.example.graph;

import org.example.checker.TokenLabel;
import org.example.model.Edge;
import org.example.model.Node;
import org.example.model.NodeType;

import java.util.*;

public class TokenLabelEngine {

    private final ProcessGraph graph;

    // these two will be edited by the existence of dummy
    private final LinkedHashMap<Node, List<Edge>> loopFreeIn;
    private final LinkedHashMap<Node, List<Edge>> loopFreeOut;

    // token states
    private final LinkedHashMap<Edge, List<TokenLabel>> edgeTokens;
    private final LinkedHashMap<Node, List<TokenLabel>> nodeTokens;

    // store each merge point and its merging splits
    private final LinkedHashMap<Node, List<Node>> CleanMergeMap;
    private final LinkedHashMap<Node, LinkedHashMap<Integer, Set<Node>>> splitMap;


    public TokenLabelEngine(ProcessGraph graph) {
        this.graph = graph;

        this.loopFreeIn = new LinkedHashMap<>(graph.getLoopFreeIn());
        this.loopFreeOut = new LinkedHashMap<>(graph.getLoopFreeOut());

        this.edgeTokens = new LinkedHashMap<>();
        this.nodeTokens = new LinkedHashMap<>();

        this.CleanMergeMap = new LinkedHashMap<>();
        this.splitMap = new LinkedHashMap<>();

        for (String scope : graph.getScopeNodes().keySet()) {
            List<Node> nodeList = graph.getScopeNodes().get(scope);
            this.distributeLabels(nodeList);
        }
    }

    private void distributeLabels(List<Node> nodeList) {

        Set<Node> starts = new LinkedHashSet<>();

        if (nodeList.isEmpty()) {
            return;
        }

        LinkedHashMap<Node, Map<Edge, Boolean>> nodeArrivalTable = new LinkedHashMap<>();

        Deque<Node> processQueue = new ArrayDeque<>();
        Node dummy = new Node("DUMMY","", NodeType.DUMMY, "", null, "");

        for (Node node : nodeList) {
            LinkedHashMap<Edge, Boolean> e = new LinkedHashMap<>();
            List<Edge> in = loopFreeIn.get(node);

            if (in.isEmpty()) {
                starts.add(node);
                processQueue.push(node);
                Edge dummyEdge = new Edge(dummy.getKey(), node.getKey());
                dummy.getOutgoingEdges().add(dummyEdge);

            } else {
                for (Edge edge : in) {
                    e.put(edge, false);
                }

                nodeArrivalTable.put(node, e);
            }
        }

        this.loopFreeOut.put(dummy, dummy.getOutgoingEdges());
        int initialIndex = 0;


        while (!processQueue.isEmpty()) {

            Node currentNode = processQueue.pop();

            if (starts.contains(currentNode)) {

                int initialBranchIndex = initialIndex++;

                List<TokenLabel> startVersion = new ArrayList<>();
                List<Edge> history = new ArrayList<>();
                LinkedHashMap<Node, Integer> splits = new LinkedHashMap<>();
                splits.put(dummy, initialBranchIndex);

                TokenLabel label = new TokenLabel(initialBranchIndex, history, splits);
                startVersion.add(label);

                this.nodeTokens.put(currentNode, startVersion);
            }


            List<Edge> outgoings = this.loopFreeOut.get(currentNode);

            for (int i = 0; i < outgoings.size(); i++) {
                Edge e = outgoings.get(i);


                int index;
                if (!graph.isLoopFreeSplit(currentNode)) {
                    index = -1;
                } else {
                    index = i;
                }

                List<TokenLabel> all = this.nodeTokens.get(currentNode);

                Node next = this.graph.getNodes().get(e.getTargetKey());

                Map<Edge, Boolean> states = nodeArrivalTable.get(next);
                states.put(e, true);

                // 在updateState中先把edge都更新了，再把更新后的存在这里
                this.updateState(e, currentNode, index, all, next);



                if (this.isReady(states)) {

                    if (graph.isLoopFreeMerge(next)) {
                        List<Edge> in = loopFreeIn.get(next);
                        this.merge(next, in);

                    } else {
                        this.updateNodeLabel(e, next);
                    }
                    processQueue.push(next);
                }
            }
        }
    }

    private void updateNodeLabel(Edge e, Node next) {

        List<TokenLabel> labels = this.edgeTokens.get(e);
        this.nodeTokens.put(next, labels);
    }

    private void merge(Node next, List<Edge> incomings) {

        LinkedHashMap<TokenLabel, LinkedHashMap<Node, Integer>> historySplits = new LinkedHashMap<>();

        for (Edge edge : incomings) {

            List<TokenLabel> labels = this.edgeTokens.get(edge);
            for (TokenLabel label : labels) {
                historySplits.put(label, label.splits());
            }

        }

        boolean alive = true;

        while (alive) {
            alive = false;

            LinkedHashMap<LinkedHashMap<Node, Integer>, List<TokenLabel>> groups = new LinkedHashMap<>();

            for (TokenLabel tokenLabel : historySplits.keySet()) {

                LinkedHashMap<Node, Integer> splitNodes = new LinkedHashMap<>(tokenLabel.splits());
                if (splitNodes.isEmpty()) {
                    continue;
                }

                // n-1
                Node lastNode = this.getLastNode(splitNodes);
                int branchIndex = splitNodes.get(lastNode);

                splitNodes.remove(lastNode);

                if (groups.containsKey(splitNodes)) {

                    List<TokenLabel> tokenLabels = new ArrayList<>(groups.get(splitNodes));

                    boolean acceptable = true;
                    for (TokenLabel label : tokenLabels) {
                        Node temp = this.getLastNode(label.splits());
                        int branchTemp = label.splits().get(temp);

                        if (!temp.equals(lastNode) || branchIndex == branchTemp) {
                            acceptable = false;
                            break;
                        }
                    }
                    if (acceptable) {
                        List<TokenLabel> tokenLabelList = groups.get(splitNodes);
                        tokenLabelList.add(tokenLabel);
                        groups.put(splitNodes, tokenLabelList);
                    }

                } else {
                    List<TokenLabel> same = new ArrayList<>();
                    same.add(tokenLabel);
                    groups.put(splitNodes, same);
                }
            }

            for (LinkedHashMap<Node, Integer> splits : groups.keySet()) {
                List<TokenLabel> tokenLabels = groups.get(splits);

                List<Integer> index = this.getIndex(tokenLabels);

                Node split = this.getLastNode(tokenLabels.get(0).splits());

                int totalBranchNumber = this.loopFreeOut.get(split).size();

                if (index.size() != totalBranchNumber) {
                    continue;
                }

                boolean isOk = true;

                for (int i = 0; i < index.size(); i++) {
                    if (!index.contains(i)) {
                        isOk = false;
                        break;
                    }
                }

                if (isOk) {
                    alive = true;

                    Set<Edge> history = new LinkedHashSet<>();

                    for (TokenLabel tokenLabel : tokenLabels) {
                        historySplits.remove(tokenLabel);
                        history.addAll(tokenLabel.history());
                    }

                    TokenLabel example = tokenLabels.get(0);


                    LinkedHashMap<Node, Integer> beforeMerge = new LinkedHashMap<>(example.splits());
                    Node last = this.getLastNode(beforeMerge);
                    beforeMerge.remove(last);
                    Node realLast = this.getLastNode(beforeMerge);

                    int branchIndex = -1;
                    if (!(realLast == null)) {
                        branchIndex = beforeMerge.get(realLast);
                    }


                    TokenLabel tokenLabel = new TokenLabel(branchIndex, history.stream().toList(),beforeMerge);
                    historySplits.put(tokenLabel, beforeMerge);

                    if (split.getType() != NodeType.DUMMY) {

                        List<Node> mergedSplits = new ArrayList<>();
                        if (this.CleanMergeMap.containsKey(next)) {
                            mergedSplits = this.CleanMergeMap.get(next);
                        }
                        mergedSplits.add(split);
                        this.CleanMergeMap.put(next, mergedSplits);
                    }
                }
            }
        }
        this.nodeTokens.put(next, historySplits.keySet().stream().toList());
    }

    private List<Integer> getIndex(List<TokenLabel> tokenLabels) {
        List<Integer> index = new ArrayList<>();
        for (TokenLabel tokenLabel : tokenLabels) {
            index.add(tokenLabel.splits().get(this.getLastNode(tokenLabel.splits())));
        }
        return index;
    }

    public Node getLastNode(LinkedHashMap<Node, Integer> splitNodes) {
        Node lastNode = null;
        for (Node n : splitNodes.keySet()) {
            lastNode = n;
        }
        return lastNode;
    }

    private void updateState(Edge e, Node currentNode, int i, List<TokenLabel> tokenLabels, Node next) {

        for (TokenLabel tokenLabel : tokenLabels) {

            LinkedHashMap<Node, Integer> splits = new LinkedHashMap<>(tokenLabel.splits());

            if (i > -1) {
                splits.put(currentNode, i);
            }

            List<Edge> history = new ArrayList<>(tokenLabel.history());
            history.add(e);

            TokenLabel label = new TokenLabel(i, history, splits);

            List<TokenLabel> labels = new ArrayList<>();
            if (this.edgeTokens.containsKey(e)) {
                labels = this.edgeTokens.get(e);
            }
            labels.add(label);
            this.edgeTokens.put(e, labels);

            for (Node split : splits.keySet()) {

                if (split.getType().equals(NodeType.DUMMY)) {
                    continue;
                }

                int branch = splits.get(split);

                LinkedHashMap<Integer, Set<Node>> branchArrival = new LinkedHashMap<>();
                Set<Node> arrivalNode = new LinkedHashSet<>();

                if (this.splitMap.containsKey(split)) {
                    branchArrival = this.splitMap.get(split);
                    if (branchArrival.containsKey(branch)) {
                        arrivalNode = branchArrival.get(branch);
                        arrivalNode.remove(currentNode);
                    }
                }
                arrivalNode.add(next);
                branchArrival.put(branch, arrivalNode);
                this.splitMap.put(split, branchArrival);

            }
        }
    }

    private boolean isReady(Map<Edge, Boolean> states) {
        return states.values().stream().allMatch(b -> b);
    }

    public ProcessGraph getGraph() {
        return graph;
    }

    public LinkedHashMap<Node, List<Edge>> getLoopFreeIn() {
        return loopFreeIn;
    }

    public LinkedHashMap<Node, List<Edge>> getLoopFreeOut() {
        return loopFreeOut;
    }

    public LinkedHashMap<Edge, List<TokenLabel>> getEdgeTokens() {
        return edgeTokens;
    }

    public LinkedHashMap<Node, List<TokenLabel>> getNodeTokens() {
        return nodeTokens;
    }

    public LinkedHashMap<Node, List<Node>> getCleanMergeMap() {
        return CleanMergeMap;
    }

    public LinkedHashMap<Node, LinkedHashMap<Integer, Set<Node>>> getSplitMap() {
        return splitMap;
    }
}

package org.example.graph;

import org.example.checker.TokenLabel;
import org.example.model.Edge;
import org.example.model.Node;
import org.example.model.NodeType;

import java.util.*;

public class TokenLabelEngine {

    private ProcessGraph graph;

    private LinkedHashMap<Node, List<Edge>> loopFreeIn;
    private LinkedHashMap<Node, List<Edge>> loopFreeOut;
    private LinkedHashMap<String, Node> nodes;

    // token states
    private LinkedHashMap<Edge, List<TokenLabel>> edgeTokens;
    private LinkedHashMap<Node, List<TokenLabel>> nodeTokens;

    // store each merge point and its merging splits
    private LinkedHashMap<Node, List<Node>> mergeMap;
    private LinkedHashMap<Node, List<Node>> splitMap;

    public TokenLabelEngine(ProcessGraph graph) {
        this.graph = graph;
        this.loopFreeIn = new LinkedHashMap<>(graph.getLoopFreeIn());
        this.loopFreeOut = new LinkedHashMap<>(graph.getLoopFreeOut());
        this.nodes = graph.getNodes();

        this.edgeTokens = new LinkedHashMap<>();
        this.nodeTokens = new LinkedHashMap<>();
        this.mergeMap = new LinkedHashMap<>();
        this.splitMap = new LinkedHashMap<>();

        for (String scope : graph.getScopeNodes().keySet()) {
            List<Node> nodeList = graph.getScopeNodes().get(scope);
            Set<Edge> backEdges = graph.getScopeBackEdges().get(scope);
            this.distributeLabels(nodeList, backEdges);
        }
    }

    private void distributeLabels(List<Node> nodeList, Set<Edge> backEdges) {
        // 直接在edgeToken上进行更改

        Set<Node> starts = new HashSet<>();

        if (nodeList.isEmpty()) {
            return;
        }

        LinkedHashMap<Node, Map<Edge, Boolean>> nodeArrivalTable = new LinkedHashMap<>();

        Deque<Node> processQueue = new ArrayDeque<>();

        for (Node node : nodeList) {
            LinkedHashMap<Edge, Boolean> e = new LinkedHashMap<>();
            List<Edge> in = loopFreeIn.get(node);

            if (in.isEmpty()) {
                starts.add(node);
                processQueue.push(node);

            } else {
                for (Edge edge : in) {
                    e.put(edge, false);
                }

                nodeArrivalTable.put(node, e);
            }
        }

        int initialIndex = 0;
        Node dummy = new Node("DUMMY","", NodeType.DUMMY, "", null, "");

        while (!processQueue.isEmpty()) {

            Node currentNode = processQueue.pop();

            if (starts.contains(currentNode)) {

                int initialBranchIndex = initialIndex++;

                Edge dummyEdge = new Edge(dummy.getKey(), currentNode.getKey());
                List<Edge> dummyOut = dummy.getOutgoingEdges();
                dummyOut.add(dummyEdge);
                this.loopFreeOut.put(dummy, dummyOut);


                List<TokenLabel> startVersion = new ArrayList<>();
                List<Edge> history = new ArrayList<>();
                // history.add(dummyEdge);
                LinkedHashMap<Node, Integer> splits = new LinkedHashMap<>();
                splits.put(dummy, initialBranchIndex);

                TokenLabel label = new TokenLabel(initialBranchIndex, history, splits);
                startVersion.add(label);

                this.nodeTokens.put(currentNode, startVersion);

            }

            // 非 start（包括无头） 节点应该带着已有的TokenLabel出现，
            //  因此在上一级处理的时候应该给下一层Node注入相应的TokenLabel，而后本层仅从Edge开始处理

            List<Edge> outgoings = this.loopFreeOut.get(currentNode);

            for (int i = 0; i < outgoings.size(); i++) {
                int index;
                if (!graph.isSplit(currentNode)) {
                    index = -1;
                } else {
                    index = i;
                }

                List<TokenLabel> all = this.nodeTokens.get(currentNode);

                Edge e = outgoings.get(i);
                Node next = this.nodes.get(e.getTargetKey());

                Map<Edge, Boolean> states = nodeArrivalTable.get(next);
                states.put(e, true);

                // 在updateState中先把edge都更新了，再把更新后的存在这里
                this.updateState(e, currentNode, index, all);

                if (this.isReady(states)) {

                    if (graph.isMerge(next)) {
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

        // 该节点前面每一个incoming edge上带的split信息（可平行？）
        LinkedHashMap<TokenLabel, LinkedHashMap<Node, Integer>> historySplits = new LinkedHashMap<>();

        for (Edge edge : incomings) {

            List<TokenLabel> labels = this.edgeTokens.get(edge);
            for (TokenLabel label : labels) {
                historySplits.put(label, label.getSplits());
            }

        }

        // 比较上一个split，计算index是否都回来了
        // 只要触发一次合并，alive就应该保持true
        boolean alive = true;

        while (alive) {
            alive = false;
            // last split node for each edge

            // 完全相同的前n-1组，完全相同的最后一组的

            // <Node, Integer> n-1, same part, the last one will be calculated by tokenLabel afterward.
            LinkedHashMap<LinkedHashMap<Node, Integer>, List<TokenLabel>> groups = new LinkedHashMap<>();

            // 为groups做准备
            for (TokenLabel tokenLabel : historySplits.keySet()) {

                LinkedHashMap<Node, Integer> splitNodes = new LinkedHashMap<>(tokenLabel.getSplits());
                if (splitNodes.isEmpty()) {
                    continue;
                }

                // n-1
                // lastNode算法保留，最后一个splitNode需要保证node相同，integer不同
                Node lastNode = this.getLastNode(splitNodes);
                int branchIndex = splitNodes.get(lastNode);

                splitNodes.remove(lastNode);

                // groups里逐一去判断是否应该去merge
                // 不急着直接put进去，最终再put

                if (groups.containsKey(splitNodes)) {

                    // 所有已经在的待合并列表
                    List<TokenLabel> tokenLabels = new ArrayList<>(groups.get(splitNodes));

                    // 每一个已经在列表里的tokenLabel对应的最后一个（其实不在splitNodes中）split都应该一样。
                    boolean acceptable = true;
                    for (TokenLabel label : tokenLabels) {
                        Node temp = this.getLastNode(label.getSplits());
                        int branchTemp = label.getSplits().get(temp);

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

                Node split = this.getLastNode(tokenLabels.get(0).getSplits());

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

                    Set<Edge> history = new HashSet<>();

                    for (TokenLabel tokenLabel : tokenLabels) {
                        historySplits.remove(tokenLabel);
                        history.addAll(tokenLabel.getHistory());
                    }

                    TokenLabel example = tokenLabels.get(0);


                    LinkedHashMap<Node, Integer> beforeMerge = new LinkedHashMap<>(example.getSplits());
                    Node last = this.getLastNode(beforeMerge);
                    beforeMerge.remove(last);
                    Node realLast = this.getLastNode(beforeMerge);

                    // 如果到最后一层了则为-1
                    int branchIndex = -1;
                    if (!(realLast == null)) {
                        branchIndex = beforeMerge.get(realLast);
                    }



                    TokenLabel tokenLabel = new TokenLabel(branchIndex, history.stream().toList(),beforeMerge);
                    historySplits.put(tokenLabel, beforeMerge);

                    if (split.getType() != NodeType.DUMMY) {
                        List<Node> mergePoints = new ArrayList<>();
                        if (this.splitMap.containsKey(split)) {
                            mergePoints = this.splitMap.get(split);
                        }
                        mergePoints.add(next);
                        this.splitMap.put(split, mergePoints);

                        // 当前node：next是merge point
                        // 查看那些split在这里merge了
                        List<Node> mergedSplits = new ArrayList<>();
                        if (this.mergeMap.containsKey(next)) {
                            mergedSplits = this.mergeMap.get(next);
                        }
                        mergedSplits.add(split);
                        this.mergeMap.put(next, mergedSplits);
                    }

                }

            }
        }

        this.nodeTokens.put(next, historySplits.keySet().stream().toList());
    }

    private List<Integer> getIndex(List<TokenLabel> tokenLabels) {
        List<Integer> index = new ArrayList<>();
        for (TokenLabel tokenLabel : tokenLabels) {
            index.add(tokenLabel.getSplits().get(this.getLastNode(tokenLabel.getSplits())));
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

    // 新用法：这里用于更新edge并返回所有的TokenLabel
    private void updateState(Edge e, Node currentNode, int i, List<TokenLabel> tokenLabels) {

        for (TokenLabel tokenLabel : tokenLabels) {

            LinkedHashMap<Node, Integer> splits = new LinkedHashMap<>(tokenLabel.getSplits());


            if (i > -1) {
                splits.put(currentNode, i);
            }

            // 处理 currentNode 后的一条线和一个 Node
            List<Edge> history = new ArrayList<>(tokenLabel.getHistory());
            history.add(e);

            TokenLabel label = new TokenLabel(i, history, splits);

            List<TokenLabel> labels = new ArrayList<>();
            if (this.edgeTokens.containsKey(e)) {
                labels = this.edgeTokens.get(e);
            }
            labels.add(label);
            this.edgeTokens.put(e, labels);

        }

    }

    private boolean isReady(Map<Edge, Boolean> states) {
        return states.values().stream().allMatch(b -> b);
    }

    public ProcessGraph getGraph() {
        return graph;
    }

    public void setGraph(ProcessGraph graph) {
        this.graph = graph;
    }

    public LinkedHashMap<Node, List<Edge>> getLoopFreeIn() {
        return loopFreeIn;
    }

    public void setLoopFreeIn(LinkedHashMap<Node, List<Edge>> loopFreeIn) {
        this.loopFreeIn = loopFreeIn;
    }

    public LinkedHashMap<Node, List<Edge>> getLoopFreeOut() {
        return loopFreeOut;
    }

    public void setLoopFreeOut(LinkedHashMap<Node, List<Edge>> loopFreeOut) {
        this.loopFreeOut = loopFreeOut;
    }

    public LinkedHashMap<Edge, List<TokenLabel>> getEdgeTokens() {
        return edgeTokens;
    }

    public void setEdgeTokens(LinkedHashMap<Edge, List<TokenLabel>> edgeTokens) {
        this.edgeTokens = edgeTokens;
    }

    public LinkedHashMap<Node, List<TokenLabel>> getNodeTokens() {
        return nodeTokens;
    }

    public void setNodeTokens(LinkedHashMap<Node, List<TokenLabel>> nodeTokens) {
        this.nodeTokens = nodeTokens;
    }

    public LinkedHashMap<Node, List<Node>> getMergeMap() {
        return mergeMap;
    }

    public void setMergeMap(LinkedHashMap<Node, List<Node>> mergeMap) {
        this.mergeMap = mergeMap;
    }

    public LinkedHashMap<Node, List<Node>> getSplitMap() {
        return splitMap;
    }

    public void setSplitMap(LinkedHashMap<Node, List<Node>> splitMap) {
        this.splitMap = splitMap;
    }

    public LinkedHashMap<String, Node> getNodes() {
        return nodes;
    }

    public void setNodes(LinkedHashMap<String, Node> nodes) {
        this.nodes = nodes;
    }
}

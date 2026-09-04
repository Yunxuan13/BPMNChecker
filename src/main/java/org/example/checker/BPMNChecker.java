package org.example.checker;

import org.example.model.*;
import org.example.parser.MermaidParser;

import java.util.*;

public class BPMNChecker {

    // private MermaidParser parser;
    private final LinkedHashMap<String, Node> nodes;
    private final List<Edge> edges;
    private List<BPMNError> errorList;
    // private final Preprocessor preprocessor;

    private LinkedHashMap<String, List<Node>> scopeNodes;
    private LinkedHashMap<String, Set<Edge>> scopeBackEdges;

    // token states
    private LinkedHashMap<Edge, List<TokenLabel>> edgeTokens;
    private final LinkedHashMap<Node, List<TokenLabel>> nodeTokens;

    // store each merge point and its merging splits
    // private LinkedHashMap<Node, List<Node>> collectedSplit; // at a merge node
    private LinkedHashMap<Node, List<Node>> mergeMap;
    private LinkedHashMap<Node, List<Node>> splitMap;

    // loop-free incoming and outgoing
    private LinkedHashMap<Node, List<Edge>> loopFreeIn;
    private LinkedHashMap<Node, List<Edge>> loopFreeOut;



    public BPMNChecker(MermaidParser parser) {
        this.nodes = parser.getNodes();
        this.edges = parser.getEdges();
        this.errorList = new ArrayList<>();
        // this.preprocessor = new Preprocessor(nodes, edges);


        List<Node> allNodes = this.nodes.values().stream().toList();

        // preset everything that can be set.
        // this.collectedSplit = new LinkedHashMap<>();
        this.mergeMap = new LinkedHashMap<>();
        this.splitMap = new LinkedHashMap<>();

        // set incoming and outgoing
        for (Node node : allNodes) {

            List<Edge> out = new ArrayList<>();
            List<Edge> in = new ArrayList<>();
            List<Role> roles = new ArrayList<>();

            for (Edge edge : edges) {
                if (Objects.equals(edge.getSourceKey(), node.getKey())) {
                    out.add(edge);
                }
                if (Objects.equals(edge.getTargetKey(), node.getKey())) {
                    in.add(edge);
                }
            }

            node.setOutgoingEdges(out);
            node.setIncomingEdges(in);

            // TODO 判断这个到底需不需要，如果需要是否应该挪到后面去？
            if (node.getIncomingEdges().size() > 1) {
                roles.add(Role.MERGE);
            }

            if (node.getOutgoingEdges().size() > 1) {
                roles.add(Role.SPLIT);
            }
            node.setRoles(roles);
        }

        // scope nodes
        this.scopeNodes = new LinkedHashMap<>();
        for (Node node : nodes.values()) {
            String nodeScope = this.getScope(node);
            if (!scopeNodes.containsKey(nodeScope)) {
                List<Node> temp = new ArrayList<>();
                temp.add(node);
                scopeNodes.put(nodeScope, temp);
            } else {
                scopeNodes.get(nodeScope).add(node);
            }
        }

        // scope back edges
        this.scopeBackEdges = new LinkedHashMap<>();

        for (String scope : this.scopeNodes.keySet()) {
            // all nodes in one scope
            List<Node> nodeList = this.scopeNodes.get(scope);
            Set<Edge> back = this.getLoopEdges(nodeList);

            this.scopeBackEdges.put(scope, back);

        }

        this.loopFreeIn = new LinkedHashMap<>();
        this.loopFreeOut = new LinkedHashMap<>();

        for (Node node : allNodes) {
            String scope = this.getScope(node);

            List<Edge> in = new ArrayList<>(node.getIncomingEdges());
            List<Edge> out = new ArrayList<>(node.getOutgoingEdges());

            in.removeIf(i -> this.scopeBackEdges.get(scope).contains(i));
            out.removeIf(o -> this.scopeBackEdges.get(scope).contains(o));

            loopFreeIn.put(node, in);
            loopFreeOut.put(node, out);
        }

        this.edgeTokens = new LinkedHashMap<>();
        this.nodeTokens = new LinkedHashMap<>();
        // Edge
        for (String scope : scopeNodes.keySet()) {
            List<Node> nodeList = this.scopeNodes.get(scope);
            Set<Edge> backEdges = this.scopeBackEdges.get(scope);
            this.distributeLabels(nodeList, backEdges);
        }
    }


    // 直接在edgeToken上进行更改
    private void distributeLabels(List<Node> nodeList, Set<Edge> backEdges) {

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
                if (!this.isSplit(currentNode, backEdges)) {
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

                    if (this.isMerge(next, backEdges)) {
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

    private Node getLastNode(LinkedHashMap<Node, Integer> splitNodes) {
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


    public void detectErrors() {

        // CON
        this.conIsolatedNode();
        this.conMissingIncomingSequenceFlow();
        this.conMissingOutgoingSequenceFlow();
        this.conUnreachableActivity();
        this.conEndeventUnreachableFromStart();
        // SE
        this.seMissingStart();
        this.seMissingEnd();
        this.seMultipleStart();
        // this should be allowed according to bpmn2.0
        // this.seMultipleEnd();
        this.seStartWithIncoming();
        this.seEndWithOutgoing();
        // GTW
        this.gtwImplicitSplit();
        this.gtwImplicitJoin();
        this.gtwMismatched();
        this.gtwNestingViolation();
        this.gtwMultipleRoles();
        this.gtwRedundant();

        // XOR
        this.xorMissingCondition();

        // AND
        this.andMismatch();

        // OR
        this.orMissingCondition();

        // SUB
        this.subEmptySubprocess();
        this.subBoundaryViolation();

        // LBL
        this.lblDuplicateName();

        // EDGE
        this.edgeDuplicateFlow();

        // LOOP
        this.loopWithoutReachableEnd();
        this.loopInvalidGateway();
    }

    private String getScope(Node node) {
        // node.getLocation() must be strictly equal to the original subgraph id
        // otherwise it will lead to problem while checking SUB-01
        String scope;
        if (node.getLocation() == null) {
            // to distinguish real main Process and subprocess with name Main
            scope = "Main:[]";
        } else {
            scope = "Subprocess:[" + node.getLocation() + "]";
        }
        return scope;
    }

    // CON-01
    public void conIsolatedNode() {
        // no in no out
        for (Node node : nodes.values()) {
            List<Node> errorNodes = new ArrayList<>();
            List<Edge> errorEdges = new ArrayList<>();
            // 缺少in和out,孤立节点
            // there is no need to distinguish the scope
            if (node.getIncomingEdges().isEmpty() && node.getOutgoingEdges().isEmpty()) {
                errorNodes.add(node);
                // public BPMNError(String errorId, String errorName, String errorCategory, String scope, String message, List<Edge> edges)
                BPMNError error = new BPMNError("CON-01", "Isolated Node",
                        "Connectivity and Reachability", this.getScope(node),
                        "Node '" + node.getKey() + "' has no incoming and no outgoing sequence flows."
                        , errorNodes, errorEdges, Severity.ERROR);
                errorList.add(error);
            }
        }
    }

    // CON-02
    public void conMissingIncomingSequenceFlow() {

        for (Node node : nodes.values()) {

            // there is no need to check whether outgoing edge list is non-empty
            // this will indeed lead to many cascading problem, this will be dealt with at Evaluation
            if (node.getIncomingEdges().isEmpty() && node.getType() != NodeType.STARTEVENT && !node.getOutgoingEdges().isEmpty()) {

                List<Node> errorNodes = new ArrayList<>();
                List<Edge> errorEdges = new ArrayList<>();

                String scope = this.getScope(node);
                errorNodes.add(node);

                BPMNError error = new BPMNError("CON-02", "Missing Incoming Sequence Flow",
                        "Connectivity and Reachability", scope,
                        "Node '" + node.getKey() + "', which is not start event and has outgoing flows, has no incoming sequence flow.",
                        errorNodes, errorEdges, Severity.ERROR);
                errorList.add(error);
            }
        }
    }

    public void conMissingOutgoingSequenceFlow() {
        for (Node node : nodes.values()) {
            if (node.getOutgoingEdges().isEmpty() && node.getType() != NodeType.ENDEVENT && !node.getIncomingEdges().isEmpty()) {

                List<Node> errorNodes = new ArrayList<>();
                List<Edge> errorEdges = new ArrayList<>();

                String scope = this.getScope(node);
                errorNodes.add(node);

                // public BPMNError(String errorId, String errorName, String errorCategory, String scope, String message, List<Edge> edges)
                BPMNError error = new BPMNError("CON-03", "Missing Outgoing Sequence Flow",
                        "Connectivity and Reachability", scope,
                        "Node '" + node.getKey() + "', which is not end event and has incoming flows, has no outgoing sequence flow.",
                        errorNodes, errorEdges, Severity.ERROR);
                errorList.add(error);
            }
        }
    }

    // scope matters
    public void conUnreachableActivity() {
        // LinkedHashMap<String, List<Node>> scopeNodes = this.getNodesByScope();

        for (List<Node> nodeList : scopeNodes.values()) {

            Set<Node> reachable = this.reachableInScope(nodeList);
            List<Node> unreachable = nodeList.stream().filter(node -> !reachable.contains(node)).toList();

            for (Node errorNode : unreachable) {
                List<Node> errorNodes = new ArrayList<>();
                errorNodes.add(errorNode);

                List<Edge> errorEdges = new ArrayList<>();

                String scope = this.getScope(errorNode);

                BPMNError error = new BPMNError("CON-04", "Unreachable Activity",
                        "Connectivity and Reachability", scope,
                        "Node '" + errorNode.getKey() + "' is not reachable from any start event in its scope.",
                        errorNodes, errorEdges, Severity.ERROR);

                errorList.add(error);
            }

        }

    }

    private Set<Node> reachableInScope(List<Node> partNodes) {
        // check whether a node is reachable from the start in this scope

        List<Edge> edgesInScope = new ArrayList<>();
        Set<Node> reachable = new HashSet<>();
        Set<String> keys = new HashSet<>();

        for (Node n : partNodes) {
            keys.add(n.getKey());
        }

        for (Edge edge : edges) {
            if (keys.contains(edge.getSourceKey()) && keys.contains(edge.getTargetKey())) {
                edgesInScope.add(edge);
            }
        }

        // first node can be NOT start event
        // if theres no start event return empty set
        for (Node start : partNodes) {
            if (start.getType() == NodeType.STARTEVENT) {
                reachable.add(start);
                reachable.addAll(this.getArrival(partNodes, edgesInScope, start));
            }
        }

        // return all reachable node in this scope
        // what if there's no start event?
        return reachable;
    }

    private List<Node> getArrival(List<Node> scopeNodes, List<Edge> relatedEdges, Node start) {
        // fifo, add and poll
        List<Node> arrivals = new ArrayList<>();
        Deque<Node> q = new ArrayDeque<>();
        // set doenst allow same
        Set<String> visited = new HashSet<>();
        q.add(start);
        visited.add(start.getKey());

        while (!q.isEmpty()) {
            Node current = q.poll();
            for (Edge edge : current.getOutgoingEdges()) {
                if (relatedEdges.contains(edge)) {
                    for (Node target : scopeNodes) {
                        if (target.getKey().equals(edge.getTargetKey()) && visited.add(target.getKey())) {
                            q.add(target);
                            arrivals.add(target);
                        }
                    }
                }

            }
        }
        return arrivals;
    }


    public void conEndeventUnreachableFromStart() {

        for (List<Node> nodeList : scopeNodes.values()) {

            Set<Node> reachable = this.reachableInScope(nodeList);

            for (Node node : nodeList) {
                if (node.getType() == NodeType.ENDEVENT && !reachable.contains(node)) {
                    List<Node> errorNodes = new ArrayList<>();
                    errorNodes.add(node);
                    List<Edge> errorEdges = new ArrayList<>();

                    String scope = this.getScope(node);

                    BPMNError error = new BPMNError("CON-05", "End Event Unreachable from Start",
                            "Connectivity and Reachability", scope,
                            "End event '" + node.getKey() + "' is not reachable from any start event in its scope.",
                            errorNodes, errorEdges, Severity.ERROR);

                    errorList.add(error);
                }
            }
        }
    }

    // SE
    public void seMissingStart() {

        for (List<Node> nodeList : scopeNodes.values()) {

            List<Node> errorNodes = new ArrayList<>();
            List<Edge> errorEdges = new ArrayList<>();

            String scope = this.getScope(nodeList.get(0));

            boolean startExist = false;

            for (Node node : nodeList) {
                if (node.getType() == NodeType.STARTEVENT) {
                    startExist = true;
                    break;
                }
            }

            if (!startExist) {
                BPMNError error = new BPMNError("SE-01", "Missing Start Event", "Start & End Event Errors", scope,
                        "No start event found in scope " + scope + ".",
                        errorNodes, errorEdges, Severity.ERROR);

                errorList.add(error);
            }
        }
    }

    public void seMissingEnd() {
        for (List<Node> nodeList : scopeNodes.values()) {
            List<Node> errorNodes = new ArrayList<>();
            List<Edge> errorEdges = new ArrayList<>();
            String scope = this.getScope(nodeList.get(0));
            boolean endExist = false;
            for (Node node : nodeList) {
                if (node.getType() == NodeType.ENDEVENT) {
                    endExist = true;
                    break;
                }
            }
            if (!endExist) {
                BPMNError error = new BPMNError("SE-02", "Missing End Event", "Start & End Event Errors", scope,
                        "No end event found in scope " + scope + ".",
                        errorNodes, errorEdges, Severity.ERROR);
                errorList.add(error);
            }
        }
    }

    public void seMultipleStart() {

        for (List<Node> nodeList : scopeNodes.values()) {

            List<Edge> errorEdges = new ArrayList<>();
            String scope = this.getScope(nodeList.get(0));

            int startNum = 0;

            List<Node> starts = new ArrayList<>();

            for (Node node : nodeList) {
                if (node.getType() == NodeType.STARTEVENT) {
                    startNum ++;
                    starts.add(node);
                }
            }
            if (startNum > 1) {
                // List<Node> errorNodes = new ArrayList<>(starts);
                BPMNError error = new BPMNError("SE-03", "Multiple Start Events", "Start & End Event Errors", scope,
                        startNum + " start events found in scope " + scope + " (expected exactly one).", starts, errorEdges, Severity.WARNING);

                errorList.add(error);
            }
        }
    }

    public void seStartWithIncoming() {
        for (Node node : nodes.values()) {

            List<Node> errorNodes = new ArrayList<>();
            String scope = this.getScope(node);

            if (node.getType() == NodeType.STARTEVENT && !node.getIncomingEdges().isEmpty()) {
                errorNodes.add(node);
                List<Edge> errorEdges = new ArrayList<>(node.getIncomingEdges());

                BPMNError error = new BPMNError("SE-04", "Start Event with Incoming Sequence Flow",
                        "Start & End Event Errors", scope,
                        "Start event '" + node.getKey() + "' has " + node.getIncomingEdges().size() + " incoming sequence flow(s).",
                        errorNodes, errorEdges, Severity.ERROR);

                errorList.add(error);
            }
        }
    }

    public void seEndWithOutgoing() {
        for (Node node : nodes.values()) {

            List<Node> errorNodes = new ArrayList<>();
            String scope = this.getScope(node);

            if (node.getType() == NodeType.ENDEVENT && !node.getOutgoingEdges().isEmpty()) {
                errorNodes.add(node);
                List<Edge> errorEdges = new ArrayList<>(node.getOutgoingEdges());

                BPMNError error = new BPMNError("SE-05", "End Event with Outgoing Sequence Flow", "Start & End Event Errors", scope,
                        "End event '" + node.getKey() + "' has " + node.getOutgoingEdges().size() + " outgoing sequence flow(s).",
                        errorNodes, errorEdges, Severity.ERROR);

                errorList.add(error);
            }
        }
    }

    // GTW
    public void gtwImplicitSplit() {

        for (Node node : nodes.values()) {

            if (!node.isGateway() && this.isSplit(node)) {
                List<Node> errorNodes = new ArrayList<>();
                errorNodes.add(node);

                List<Edge> errorEdges = new ArrayList<>(node.getOutgoingEdges());

                String scope = this.getScope(node);

                BPMNError error = new BPMNError("GTW-01", "Implicit Split", "General Gateway Errors", scope,
                        "Non-gateway node '" + node.getKey() + "' has " + node.getOutgoingEdges().size() + " outgoing flows (implicit split).",
                        errorNodes, errorEdges, Severity.WARNING);

                errorList.add(error);
            }
        }
    }

    public void gtwImplicitJoin() {

        for (Node node : nodes.values()) {

            if (!node.isGateway() && this.isMerge(node)) {

                List<Node> errorNodes = new ArrayList<>();
                errorNodes.add(node);

                List<Edge> errorEdges = new ArrayList<>(node.getIncomingEdges());

                String scope = this.getScope(node);

                BPMNError error = new BPMNError("GTW-02", "Implicit Join", "General Gateway Errors", scope,
                        "Non-gateway node '" + node.getKey() + "' has " + node.getIncomingEdges().size() + " incoming flows (implicit join).",
                        errorNodes, errorEdges, Severity.WARNING);

                errorList.add(error);
            }
        }
    }

    public void gtwMismatched() {

        for (List<Node> nodeList : scopeNodes.values()) {

            Set<Edge> loopEdges = this.getLoopEdges(nodeList);

            for (Node node : nodeList) {

                if (!node.isGateway() || !this.isSplit(node, loopEdges)) {
                    continue;
                }

                // JoinMatch match = this.strictMatchingJoin(node, loopEdges);

                Node join = this.strictMatchingJoin(node, loopEdges);

                if (join == null) {
                    continue;
                }

                // Node join  = match.join;
                if (!join.isGateway() || join.getType() == node.getType()) {
                    continue;
                }

                List<Node> errorNodes = new ArrayList<>();
                errorNodes.add(node);
                errorNodes.add(join);

                List<Edge> errorEdges = new ArrayList<>();

                String scope = this.getScope(node);

                BPMNError error = new BPMNError("GTW-03", "Mismatched Gateway Types", "General Gateway Errors",
                        scope,
                        "Split gateway '" + node.getKey() + "' is joined by '" + join.getKey() + "' of a different gateway type.",
                        errorNodes, errorEdges, Severity.ERROR);

                this.errorList.add(error);

            }
        }
    }

    private Set<Edge> getLoopEdges(List<Node> nodeList) {

        Set<String> keys = new HashSet<>();

        for (Node node : nodeList) {
            keys.add(node.getKey());
        }

        Set<String> black = new HashSet<>();
        Set<String> grey = new HashSet<>();

        Set<Edge> loopEdges = new HashSet<>();

        for (Node node : nodeList) {
            if (!black.contains(node.getKey())) {
                this.getLoopEdge(node, keys, black, grey, loopEdges);
            }
        }
        return loopEdges;
    }

    private void getLoopEdge(Node node, Set<String> keys, Set<String> black, Set<String> grey, Set<Edge> loopEdges) {
        black.add(node.getKey());
        grey.add(node.getKey());

        for (Edge edge: node.getOutgoingEdges()) {
            String key = edge.getTargetKey();
            Node target = this.nodes.get(key);

            if (target == null || !keys.contains(target.getKey())) {
                continue;
            }

            if (grey.contains(target.getKey())) {
                loopEdges.add(edge);
                continue;
            }

            if (!black.contains(target.getKey())) {
                this.getLoopEdge(target, keys, black, grey, loopEdges);
            }
        }
        grey.remove(node.getKey());
    }


    private Node branchJoin(Node branchStart, String scope, Set<Edge> loopEdges) {
        int balance  = 0;
        Node current = branchStart;
        Set<String> arrival = new HashSet<>();

        while (current != null && arrival.add(current.getKey())) {

            if (this.isMerge(current, loopEdges)) {
                if (balance == 0) {
                    return current;
                } else {
                    balance--;
                }
            }

            if (this.isSplit(current, loopEdges)) {
                balance++;
            }

            // only deal with edges not in loopEdges
            Edge nextEdge = null;
            for (Edge edge : current.getOutgoingEdges()) {
                if (!loopEdges.contains(edge)) {
                    nextEdge = edge;
                    break;
                }
            }

            if (nextEdge == null) {
                return null;
            }


            String key = nextEdge.getTargetKey();
            Node next = this.nodes.get(key);

            // 保证在同一scope中
            if (next == null || !this.getScope(next).equals(scope)) {
                return null;
            }

            current = next;

        }
        return null;
    }


    private Node strictMatchingJoin(Node split, Set<Edge> loopEdges) {
        String scope = this.getScope(split);
        Node target = null;

        for (Edge edge : split.getOutgoingEdges()) {

            if (loopEdges.contains(edge)) {
                continue;
            }

            Node start = nodes.get(edge.getTargetKey());
            Node joinNode;
            if (start == null) {
                joinNode = null;
            } else {
                joinNode = this.branchJoin(start, scope, loopEdges);
            }

            if (joinNode == null) {
                continue;
            }

            if (target == null) {
                target = joinNode;
                // reachedCount = 1;
//            } else if (target.getKey().equals(joinNode.getKey())) {
//                // reachedCount++;
            } else if (!target.getKey().equals(joinNode.getKey())) {
                return null;
            }
        }

        return target;
    }



    public void gtwNestingViolation() {

        for (List<Node> nodeList : this.scopeNodes.values()) {

            Set<Edge> loopEdges = this.getLoopEdges(nodeList);
            String scope = this.getScope(nodeList.get(0));

            // store all join nodes for second way checking
            // split nodes that are joined by the node with key
            Map<String, List<Node>> splitsByJoin = new LinkedHashMap<>();

            for (Node node : nodeList) {
                // find split gateway first
                if (node.isGateway() && this.isSplit(node, loopEdges)) {

                    Set<String> joinKeys = new HashSet<>();
                    List<Node> joinNodes = new ArrayList<>();

                    // for algorithm-vereinfachung --> not consider loop edges from graph
                    for (Edge edge : node.getOutgoingEdges()) {

                        if (loopEdges.contains(edge)) {
                            continue;
                        }

                        Node start = this.nodes.get(edge.getTargetKey());
                        Node join = null;

                        if (start != null) {
                            join = this.branchJoin(start, scope, loopEdges);
                        }

                        if (join != null && joinKeys.add(join.getKey())) {
                            joinNodes.add(join);
                        }

                    }

                    // different branches merged at more than one gateway
                    if (joinKeys.size() > 1) {

                        List<Node> errorNodes = new ArrayList<>();
                        errorNodes.add(node);
                        errorNodes.addAll(joinNodes);

                        List<Edge> errorEdges = new ArrayList<>();

                        BPMNError error = new BPMNError("GTW-04", "Gateway Nesting Violation",
                                "General Gateway Errors", scope,
                                "Branches of split gateway '" + node.getKey() + "' merge at " + joinKeys.size() + " different join nodes.",
                                errorNodes, errorEdges, Severity.WARNING);
                        errorList.add(error);
                    }

                    // DEBUG (detected by sapsam/71_ground_truth.txt)
                    if (joinKeys.size() == 1) {
                        Node joinNode = joinNodes.get(0);
                        if (joinNode.getType() != NodeType.ENDEVENT) {
                            String joinKey = joinNode.getKey();
                            List<Node> splits = splitsByJoin.get(joinKey);
                            if (splits == null) {
                                splits = new ArrayList<>();
                                splitsByJoin.put(joinKey, splits);
                            }
                            splits.add(node);
                        }
                    }
                }
            }
                for (String joinKey : splitsByJoin.keySet()) {
                    List<Node> splits = splitsByJoin.get(joinKey);
                    if (splits.size() < 2) {
                        continue;
                    }

                    List<Node> errorNodes = new ArrayList<>(splits);

                    Node join = this.nodes.get(joinKey);
                    if (join != null) {
                        errorNodes.add(join);
                    }

                    StringBuilder nodeKeys = new StringBuilder();
                    for (Node split : splits) {
                        if (!nodeKeys.isEmpty()) {
                            nodeKeys.append(", ");
                        }
                        nodeKeys.append("'")
                                .append(split.getKey())
                                .append("'");
                    }

                    BPMNError error = new BPMNError("GTW-04", "Gateway Nesting Violation",
                            "General Gateway Errors", scope,
                            "Split gateways: " + nodeKeys + " all merge at the same join node '" + joinKey
                                    + "'; the blocks share one exit.",
                            errorNodes, new ArrayList<>(), Severity.WARNING);
                    errorList.add(error);

                }

        }
    }

    public void gtwMultipleRoles() {
        for (Node node : nodes.values()) {
            if (node.isGateway() && node.getIncomingEdges().size() > 1 && node.getOutgoingEdges().size() > 1) {
                String scope = this.getScope(node);
                List<Node> errorNodes = new ArrayList<>();
                List<Edge> errorEdges = new ArrayList<>();
                errorNodes.add(node);

                BPMNError error = new BPMNError("GTW-05", "Gateway Used as Both Split and Join",
                        "General Gateway Errors", scope,
                        "Gateway '" + node.getKey() + "' is used as both split and join.",
                        errorNodes, errorEdges, Severity.WARNING);
                errorList.add(error);
            }
        }

    }

    public void gtwRedundant() {

        for (Node node : nodes.values()) {

            List<Node> errorNodes = new ArrayList<>();
            List<Edge> errorEdges = new ArrayList<>();
            String scope = this.getScope(node);

            if (node.isGateway() && node.getIncomingEdges().size() == 1
                    && node.getOutgoingEdges().size() == 1) {

                errorNodes.add(node);

                errorEdges.addAll(node.getOutgoingEdges());
                errorEdges.addAll(node.getIncomingEdges());

                BPMNError error = new BPMNError("GTW-06", "Redundant Gateway",
                        "General Gateway Errors", scope,
                        "Gateway '" + node.getKey() + "' has exactly one incoming and one outgoing flow and has no routing effect.",
                        errorNodes, errorEdges, Severity.WARNING);
                errorList.add(error);
            }
        }
    }

    public void xorMissingCondition() {

        for (Node node : nodes.values()) {

            if (node.getType() == NodeType.EXCLUSIVEGATEWAY) {

                String scope = this.getScope(node);

                List<Node> errorNode = new ArrayList<>();

                List<Edge> without = new ArrayList<>();

                int conditionNum = 0;

                for (Edge edge : node.getOutgoingEdges()) {
                    if (edge.getCondition() != null && !edge.getCondition().isEmpty()) {
                        conditionNum++;
                    } else {
                        without.add(edge);
                    }
                }

                if (conditionNum < node.getOutgoingEdges().size() - 1) {

                    errorNode.add(node);

                    List<Edge> errorEdge = new ArrayList<>(without);

                    BPMNError error = new BPMNError("XOR-01", "Missing Condition on XOR Outgoing Flow",
                            "XOR Gateway Errors", scope,
                            "XOR gateway '" + node.getKey() + "' has " + without.size() +
                                    " outgoing flow(s) without a condition (at most one default flow is allowed).",
                            errorNode, errorEdge, Severity.ERROR);

                    errorList.add(error);
                }
            }
        }
    }


    public void andMismatch() {

        for (List<Node> nodeList : this.scopeNodes.values()) {

            Set<Edge> loopEdges = this.getLoopEdges(nodeList);
            String scope = this.getScope(nodeList.get(0));

            for (Node node : nodeList) {
                if (node.getType() != NodeType.PARALLELGATEWAY || !this.isSplit(node, loopEdges)) {
                    continue;
                }

                int branchCount = 0;
                LinkedHashMap<Node, Integer> reached = new LinkedHashMap<>();

                for (Edge edge : node.getOutgoingEdges()) {
                    if (loopEdges.contains(edge)) {
                        continue;
                    }
                    branchCount++;

                    Node start = this.nodes.get(edge.getTargetKey());
                    Node join;
                    if (start == null) {
                        join = null;
                    } else {
                        join = this.branchJoin(start, scope, loopEdges);
                    }

                    if (join != null) {
                        Integer num  = reached.get(join);
                        int a;
                        if (num == null) {
                            a = 1;
                        } else {
                            a = num + 1;
                        }
                        reached.put(join, a);
                    }

                }

                // all and split gateway endlich merged at only one node(and node)
                if (reached.size() == 1) {

                    Node join = reached.keySet().iterator().next();
                    int hit = reached.get(join);

                    // not parallel --> ignore
                    // and-03 not responsible for this situation
                    if (join.getType() != NodeType.PARALLELGATEWAY) {
                        continue;
                    }

                    // no problem
                    if (hit == branchCount && this.getIncomingWithoutLoop(join, loopEdges) == branchCount) {
                        continue;
                    }

                }

                List<Node> errorNodes = new ArrayList<>();
                errorNodes.add(node);
                errorNodes.addAll(reached.keySet());

                List<Edge> errorEdges = new ArrayList<>();

                BPMNError error = new BPMNError("AND-01", "AND Split and Join Branch Count Mismatch",
                        "AND Gateway Errors", scope,
                        "Branches of AND split '" + node.getKey() + "' do not synchronize at a single matching AND join.",
                        errorNodes, errorEdges, Severity.ERROR);
                errorList.add(error);
            }
        }
    }

    private int getIncomingWithoutLoop(Node node, Set<Edge> loopEdges) {
        int incoming = 0;
        for (Edge edge : node.getIncomingEdges()) {
            if (!loopEdges.contains(edge)) {
                incoming++;
            }
        }
        return incoming;
    }


    public void orMissingCondition() {

        for (Node node : nodes.values()) {

            if (node.getType() == NodeType.INCLUSIVEGATEWAY) {

                int conditionNum = 0;
                List<Edge> without = new ArrayList<>();

                // List<Edge> invalid = new ArrayList<>();
                for (Edge edge : node.getOutgoingEdges()) {

                    if (edge.getCondition() != null && !edge.getCondition().isEmpty()) {
                        conditionNum++;
                    } else {
                        without.add(edge);

                    }
                }

                if (conditionNum < node.getOutgoingEdges().size() - 1) {

                    String scope = this.getScope(node);
                    List<Node> errorNodes = new ArrayList<>();
                    errorNodes.add(node);
                    // errorNodes.addAll(reached.keySet());
                    List<Edge> errorEdges = new ArrayList<>(without);


                    BPMNError error = new BPMNError("OR-01", "Missing Condition on OR Outgoing Flow",
                            "OR Gateway Errors", scope,
                            "OR gateway '" + node.getKey() + "' has " + without.size() + " outgoing flow(s) without a condition (at most one default flow is allowed).",
                            errorNodes, errorEdges, Severity.ERROR);

                    errorList.add(error);
                }
            }
        }
    }

    // SUB
    public void subEmptySubprocess() {
        for (Node node : nodes.values()) {
            if (node.getType() == NodeType.SUBGRAPH) {

                String subId = node.getId();

                // "Subprocess:[" + node.getLocation() + "]"
                String scopeName = "Subprocess:[" + subId + "]";

                boolean exist = scopeNodes.containsKey(scopeName);

                if (!exist) {
                    List<Node> errorNodes = new ArrayList<>();
                    errorNodes.add(node);
                    List<Edge> errorEdges = new ArrayList<>();
                    String scope = this.getScope(node);
                    BPMNError error = new BPMNError("SUB-01", "Empty Subprocess",
                            "Subprocess Errors", scope,
                            "Subprocess '" + node.getId() + "' contains no nodes.",
                            errorNodes, errorEdges, Severity.ERROR);
                    errorList.add(error);
                }
            }
        }
    }

    public void subBoundaryViolation() {
        for (Edge edge : edges) {

            Node source = nodes.get(edge.getSourceKey());
            Node target = nodes.get(edge.getTargetKey());

            // situation of source and target should be check
            if (source == null || target == null) {
                continue;
            }

            if (!Objects.equals(source.getLocation(), target.getLocation())) {
                List<Node> errorNodes = new ArrayList<>();

                String scope = this.getScope(source);
                errorNodes.add(source);
                errorNodes.add(target);

                List<Edge> errorEdges = new ArrayList<>();
                errorEdges.add(edge);

                BPMNError error = new BPMNError("SUB-02", "Subprocess Boundary Violation",
                        "Subprocess Errors", scope,
                        "Sequence flow from '" + source.getKey() + "' to '" + target.getKey() + "' crosses a subprocess boundary.",
                        errorNodes, errorEdges, Severity.ERROR);
                this.errorList.add(error);
            }
        }
    }

    // LBL
    public void  lblDuplicateName() {
        // label nodes with same label
        LinkedHashMap<String, List<Node>> labelNodes = new LinkedHashMap<>();

        for (Node node : nodes.values()) {

            if (node.getType() == NodeType.TASK) {
                String label = node.getLabel();
                if (label != null && !label.isEmpty() && !label.isBlank()) {

                    if (!labelNodes.containsKey(label)) {
                        List<Node> nodeList = new ArrayList<>();
                        nodeList.add(node);
                        labelNodes.put(label, nodeList);
                    } else {
                        labelNodes.get(label).add(node);
                    }
                }

            }

        }

        for (List<Node> ln : labelNodes.values()) {
            if (ln.size() > 1) {
                List<Node> errorNodes = new ArrayList<>(ln);
                List<Edge> errorEdges = new ArrayList<>();

                // cant define scope and we dont really need them
                BPMNError error = new BPMNError("LBL-01", "Duplicate Activity Name",
                        "Label Errors", "global",
                        "Task label '" + ln.get(0).getLabel() + "' is used by " + ln.size() + " different tasks.",
                        errorNodes, errorEdges, Severity.WARNING);

                errorList.add(error);
            }
        }
    }

    // EDGE
    public void edgeDuplicateFlow() {

        LinkedHashMap<EdgePair, List<Edge>> sameEdge = new LinkedHashMap<>();

        for (Edge edge : edges) {
            EdgePair e = new EdgePair();
            e.source = nodes.get(edge.getSourceKey());
            e.target = nodes.get(edge.getTargetKey());
            if (!sameEdge.containsKey(e)) {
                List<Edge> edgeList = new ArrayList<>();
                edgeList.add(edge);
                sameEdge.put(e, edgeList);
            } else {
                sameEdge.get(e).add(edge);
            }
        }

        for (EdgePair se : sameEdge.keySet()) {
            if (sameEdge.get(se).size() > 1) {
                List<Node> errorNodes = new ArrayList<>();
                List<Edge> errorEdges = new ArrayList<>(sameEdge.get(se));

                if (se.source != null) {
                    errorNodes.add(se.source);
                }

                if (se.target != null) {
                    errorNodes.add(se.target);
                }

                String scope;
                if (se.source != null) {
                    scope = this.getScope(se.source);
                } else {
                    scope = "There exist other errors!";
                }


                // cant define scope and we dont really need them
                BPMNError error = new BPMNError("EDGE-01", "Duplicate Sequence Flow",
                        "Edge Errors", scope,
                        errorEdges.size() + " sequence flows connect '" + errorEdges.get(0).getSourceKey() + "' to '" + errorEdges.get(0).getTargetKey() + "' (redundant: multiple flows to the same target add no routing effect).",
                        errorNodes, errorEdges, Severity.WARNING);

                errorList.add(error);
            }
        }
    }

    private static class EdgePair {
        Node source;
        Node target;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EdgePair edgePair = (EdgePair) o;
            return Objects.equals(source, edgePair.source) && Objects.equals(target, edgePair.target);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, target);
        }
    }

    // LOOP
    public void loopWithoutReachableEnd() {
        // in a loop, it cant arrive at end event of this scope
        // for one node, endevent is unreachable for it
        for (List<Node> nodeList : scopeNodes.values()) {
            boolean hasEndevent = false;
            for (Node n : nodeList) {
                if (n.getType() == NodeType.ENDEVENT) {
                    hasEndevent = true;
                    break;
                }
            }

            if (hasEndevent) {
                Set<Edge> loopEdges = this.getLoopEdges(nodeList);

                if (!loopEdges.isEmpty()) {
                    Set<String> keys = new HashSet<>();
                    for (Node n : nodeList) {
                        keys.add(n.getKey());
                    }

                    List<Edge> edgesInScope = new ArrayList<>();
                    for (Edge edge : edges) {
                        if (keys.contains(edge.getSourceKey()) && keys.contains(edge.getTargetKey())) {
                            edgesInScope.add(edge);
                        }
                    }

                    String scope = this.getScope(nodeList.get(0));

                    Set<String> result = new HashSet<>();

                    for (Edge loop : loopEdges) {
                        Node enterNode = nodes.get(loop.getTargetKey());
                        if (enterNode != null && !result.contains(enterNode.getKey())) {
                            List<Node> reachable = this.getArrival(nodeList, edgesInScope, enterNode);
                            boolean canReachEnd = false;

                            for (Node n : reachable) {
                                if (n.getType() == NodeType.ENDEVENT) {
                                    canReachEnd = true;
                                    break;
                                }
                            }

                            if (!canReachEnd) {
                                result.add(enterNode.getKey());
                                List<Node> errorNodes = new ArrayList<>();
                                errorNodes.add(enterNode);

                                List<Edge> errorEdges = new ArrayList<>();
                                errorEdges.add(loop);

                                BPMNError error = new BPMNError("LOOP-01", "Loop Without Reachable End Event",
                                        "Loop Errors", scope,
                                        "Loop entered at '" + enterNode.getKey() + "' cannot reach any end event in its scope (livelock / infinite loop).",
                                        errorNodes, errorEdges, Severity.ERROR);
                                errorList.add(error);
                            }
                        }
                    }
                }
            }
        }
    }

    // and cant be as loop control gateway
    public void loopInvalidGateway() {

        for (List<Node> nodeList : scopeNodes.values()) {
            Set<Edge> loopEdges = this.getLoopEdges(nodeList);
            String scope = this.getScope(nodeList.get(0));

            for (Edge edge : loopEdges) {
                Node exitLoop = nodes.get(edge.getSourceKey());
                Node enterLoop = nodes.get(edge.getTargetKey());

                boolean and = (exitLoop!= null && exitLoop.getType() == NodeType.PARALLELGATEWAY)
                        || (enterLoop != null && enterLoop.getType() == NodeType.PARALLELGATEWAY);

                if (and) {
                    List<Node> errorNodes = new ArrayList<>();
                    if (exitLoop != null) {
                        errorNodes.add(exitLoop);
                    }

                    if (enterLoop != null) {
                        errorNodes.add(enterLoop);
                    }

                    List<Edge> errorEdges = new ArrayList<>();
                    errorEdges.add(edge);

                    BPMNError error = new BPMNError("LOOP-02", "Loop Controlled by AND Gateway",
                            "Loop Errors", scope,
                            "Loop back-edge from '" + edge.getSourceKey() + "' to '" + edge.getTargetKey() + "' is controlled by a parallel (AND) gateway.",
                            errorNodes, errorEdges, Severity.ERROR);
                    errorList.add(error);
                }
            }
        }
    }


    private boolean isSplit(Node node) {
        return node.getOutgoingEdges().size() > 1;
    }

    private boolean isSplit(Node node, Set<Edge> loopEdges) {
        int count = 0;
        for (Edge edge : node.getOutgoingEdges()) {
            if (!loopEdges.contains(edge)) {
                count++;
            }
        }

        return count > 1;
    }

    private boolean isMerge(Node node) {
        return node.getIncomingEdges().size() > 1;
    }

    // consider the situation of graph with loop
    // for gtw0304
    private boolean isMerge(Node node, Set<Edge> loopEdges) {
        int count = 0;
        for (Edge edge : node.getIncomingEdges()) {
            if (!loopEdges.contains(edge)) {
                count++;
            }
        }

        return count > 1;
    }


    public LinkedHashMap<String, Node> getNodes() {
        return nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public List<BPMNError> getErrorList() {
        return errorList;
    }

    public void setErrorList(List<BPMNError> errorList) {
        this.errorList = errorList;
    }

    public LinkedHashMap<String, List<Node>> getScopeNodes() {
        return scopeNodes;
    }

    public void setScopeNodes(LinkedHashMap<String, List<Node>> scopeNodes) {
        this.scopeNodes = scopeNodes;
    }

    public LinkedHashMap<String, Set<Edge>> getScopeBackEdges() {
        return scopeBackEdges;
    }

    public void setScopeBackEdges(LinkedHashMap<String, Set<Edge>> scopeBackEdges) {
        this.scopeBackEdges = scopeBackEdges;
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

//    public Preprocessor getPreprocessor() {
//        return preprocessor;
//    }
}

package org.example.checker;

import org.example.model.*;
import org.example.parser.MermaidParser;

import java.util.*;

public class BPMNChecker {

    // private MermaidParser parser;
    private LinkedHashMap<String, Node> nodes;
    private List<Edge> edges;
    private List<BPMNError> errorList;
    private LinkedHashMap<String, List<Node>> scopeNodes;


    public BPMNChecker(MermaidParser parser) {
        this.nodes = parser.getNodes();
        this.edges = parser.getEdges();
        this.errorList = new ArrayList<>();

        for (Node node : nodes.values()) {
            List<Edge> out = new ArrayList<>();
            List<Edge> in = new ArrayList<>();
            List<Role> roles = new ArrayList<>();
            for (Edge edge : edges) {
                // TODO: sourceKey
                if (Objects.equals(edge.getSourceKey(), node.getKey())) {
                    out.add(edge);
                }
                if (Objects.equals(edge.getTargetKey(), node.getKey())) {
                    in.add(edge);
                }
            }
            node.setOutgoingEdges(out);
            node.setIncomingEdges(in);

            if (node.getIncomingEdges().size() > 1) {
                roles.add(Role.MERGE);
            }
            if (node.getOutgoingEdges().size() > 1) {
                roles.add(Role.SPLIT);
            }
            node.setRoles(roles);
        }

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
            if (node.getIncomingEdges().isEmpty() && node.getType() != NodeType.STARTEVENT) {

                List<Node> errorNodes = new ArrayList<>();
                List<Edge> errorEdges = new ArrayList<>();

                String scope = this.getScope(node);
                errorNodes.add(node);

                BPMNError error = new BPMNError("CON-02", "Missing Incoming Sequence Flow",
                        "Connectivity and Reachability", scope,
                        "Node '" + node.getKey() + "' has no incoming sequence flow.",
                        errorNodes, errorEdges, Severity.ERROR);
                errorList.add(error);
            }
        }
    }

    public void conMissingOutgoingSequenceFlow() {
        for (Node node : nodes.values()) {
            if (node.getOutgoingEdges().isEmpty() && node.getType() != NodeType.ENDEVENT) {

                List<Node> errorNodes = new ArrayList<>();
                List<Edge> errorEdges = new ArrayList<>();

                String scope = this.getScope(node);
                errorNodes.add(node);

                // public BPMNError(String errorId, String errorName, String errorCategory, String scope, String message, List<Edge> edges)
                BPMNError error = new BPMNError("CON-03", "Missing Outgoing Sequence Flow",
                        "Connectivity and Reachability", scope,
                        "Node '" + node.getKey() + "' has no outgoing sequence flow.",
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
    // TODO need to consider main process and subprocesses
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

//    private static class JoinMatch {
//        private Node join;
//        private int branchCount;
//        private int merged;
//
//    }


    private Node strictMatchingJoin(Node split, Set<Edge> loopEdges) {
        String scope = this.getScope(split);
        Node target = null;
        // branch number --> AND
//        int reachedCount = 0;
//        int branchCount = 0;

        for (Edge edge : split.getOutgoingEdges()) {

            if (loopEdges.contains(edge)) {
                continue;
            }

            // branchCount++;

            Node start = nodes.get(edge.getTargetKey());
            Node joinNode;
            if (start == null) {
                joinNode = null;
            } else {
                joinNode = this.branchJoin(start, scope, loopEdges);
            }
            // Node joinNode = this.branchJoin(start, scope);

            if (joinNode == null) {
                continue;
            }

            if (target == null) {
                target = joinNode;
                // reachedCount = 1;
            } else if (target.getKey().equals(joinNode.getKey())) {
                // reachedCount++;
            } else {
                return null;
            }
        }

        if (target == null) {
            return null;
        }

//        JoinMatch joinMatch = new JoinMatch();
//        joinMatch.branchCount = branchCount;
//        joinMatch.merged = reachedCount;
//        joinMatch.join = target;
        return target;
    }



    public void gtwNestingViolation() {

        for (List<Node> nodeList : this.scopeNodes.values()) {

            Set<Edge> loopEdges = this.getLoopEdges(nodeList);
            String scope = this.getScope(nodeList.get(0));

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
                }
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

    // currently we dont consider adhoc box
//    public BPMNError subIllegalElement() {
//        return null;
//    }

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

    public void setNodes(LinkedHashMap<String, Node> nodes) {
        this.nodes = nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public void setEdges(List<Edge> edges) {
        this.edges = edges;
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


}

package org.example.checker;

import org.example.graph.ProcessGraph;
import org.example.graph.TokenLabelEngine;
import org.example.model.*;
import org.example.parser.MermaidParser;

import java.util.*;
import java.util.stream.Collectors;

public class BPMNChecker {

    // private MermaidParser parser;
    private final LinkedHashMap<String, Node> nodes;
    private final List<Edge> edges;
    private List<BPMNError> errorList;
    private ProcessGraph graph;
    private TokenLabelEngine tokenLabelEngine;

    private static final String CON = "Connectivity and Reachability";
    private static final String SE = "Start and End Event";
    private static final String GTW = "General Gateway Issues";
    private static final String XOR = "Exclusive Gateway (XOR) Issues";
    private static final String AND = "Parallel Gateway (AND) Issues";
    private static final String OR = "Inclusive Gateway (OR) Issues";
    private static final String SUB = "Subprocess Issues";
    private static final String LBL = "Label Issues";
    private static final String EDGE = "Edge Issues";
    private static final String LOOP = "LOOP Issues";

    public BPMNChecker(MermaidParser parser) {
        this.nodes = parser.getNodes();
        this.edges = parser.getEdges();
        this.graph = new ProcessGraph(nodes, edges);
        this.tokenLabelEngine = new TokenLabelEngine(graph);

        this.errorList = new ArrayList<>();
    }

    public void detectErrors() {

        // CON
        this.conIsolatedNode();
        this.conMissingIncomingSequenceFlow();
        this.conMissingOutgoingSequenceFlow();
        this.conUnreachableActivity();
        this.conEndEventUnreachableFromStart();
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

// -----------------------------------------⬇️Connectivity and Reachability ⬇️-----------------------------------------

    // ✅CON-01, normal check
    public void conIsolatedNode() {

        for (Node node : nodes.values()) {

            // the node who is not connected to the model
            List<Node> errorNodes = new ArrayList<>();

            // no edge relevant
            List<Edge> errorEdges = new ArrayList<>();

            if (node.getIncomingEdges().isEmpty() && node.getOutgoingEdges().isEmpty()) {

                errorNodes.add(node);

                // node in form id:type:shape+label (key = id:type)
                String message = "Node '" + node + "' is an isolated node, which has no incoming and no outgoing sequence flows.";

                BPMNError error = new BPMNError("CON-01", "Isolated Node", CON, graph.getScope(node),
                        message, errorNodes, errorEdges, Severity.ERROR);

                errorList.add(error);
            }
        }
    }

    // ✅CON-02, normal check
    public void conMissingIncomingSequenceFlow() {

        for (Node node : nodes.values()) {

            // one-side checking: has out but no ins
            if (node.getIncomingEdges().isEmpty() && node.getType() != NodeType.STARTEVENT && !node.getOutgoingEdges().isEmpty()) {

                // one node that has this issue
                List<Node> errorNodes = new ArrayList<>();
                // no relevant edge
                List<Edge> errorEdges = new ArrayList<>();

                String scope = graph.getScope(node);
                errorNodes.add(node);
                String message = "Node '" + node + "' which is not a start event has no incoming sequence flow.";

                BPMNError error = new BPMNError("CON-02", "Missing Incoming Sequence Flow", CON, scope,
                        message, errorNodes, errorEdges, Severity.ERROR);

                errorList.add(error);
            }
        }
    }

    // ✅CON-03, normal check
    public void conMissingOutgoingSequenceFlow() {

        for (Node node : nodes.values()) {

            if (node.getOutgoingEdges().isEmpty() && node.getType() != NodeType.ENDEVENT && !node.getIncomingEdges().isEmpty()) {

                // similar to CON-02
                List<Node> errorNodes = new ArrayList<>();
                List<Edge> errorEdges = new ArrayList<>();

                String scope = graph.getScope(node);
                errorNodes.add(node);
                String message = "Node '" + node.getKey() + "' which is not an end event has no outgoing sequence flow.";

                BPMNError error = new BPMNError("CON-03", "Missing Outgoing Sequence Flow", CON, scope,
                        message, errorNodes, errorEdges, Severity.ERROR);

                errorList.add(error);
            }
        }
    }

    // ✅CON-04, need scope check and reachability check
    // back edge tolerant
    public void conUnreachableActivity() {

        // ignore all edges that cross scopes
        for (List<Node> nodeList : graph.getScopeNodes().values()) {

            Set<Node> reachable = this.graph.reachableInScope(nodeList);
            List<Node> unreachable = nodeList.stream().filter(node -> !reachable.contains(node)).toList();

            for (Node errorNode : unreachable) {

                // the node that is unreachable (report one by one)
                List<Node> errorNodes = new ArrayList<>();
                errorNodes.add(errorNode);

                // no related edges
                List<Edge> errorEdges = new ArrayList<>();

                String scope = graph.getScope(errorNode);
                String message = "Node '" + errorNode + "' is not reachable from any start event in its scope.";

                BPMNError error = new BPMNError("CON-04", "Unreachable Activity", CON, scope, message,
                        errorNodes, errorEdges, Severity.ERROR);

                errorList.add(error);
            }

        }

    }

    // ✅CON-05, need scope check and reachability check
    public void conEndEventUnreachableFromStart() {

        for (List<Node> nodeList : graph.getScopeNodes().values()) {

            Set<Node> reachable = this.graph.reachableInScope(nodeList);

            for (Node node : nodeList) {
                if (node.getType() == NodeType.ENDEVENT && !reachable.contains(node)) {

                    // single error node
                    List<Node> errorNodes = new ArrayList<>();
                    errorNodes.add(node);

                    List<Edge> errorEdges = new ArrayList<>();

                    String scope = graph.getScope(node);
                    String message = "End event '" + node + "' is not reachable from any start event in its scope.";

                    BPMNError error = new BPMNError("CON-05", "End Event Unreachable from Start", CON,
                            scope, message, errorNodes, errorEdges, Severity.ERROR);

                    errorList.add(error);
                }
            }
        }
    }

// ---------------------------------------------------------------------------------------------------------------------

    // ✅SE-01, normal check
    public void seMissingStart() {

        for (List<Node> nodeList : graph.getScopeNodes().values()) {

            // no related node needed
            List<Node> errorNodes = new ArrayList<>();
            // no related edge needed
            List<Edge> errorEdges = new ArrayList<>();

            String scope = graph.getScope(nodeList.get(0));

            boolean startExist = false;

            for (Node node : nodeList) {
                if (node.getType() == NodeType.STARTEVENT) {
                    startExist = true;
                    break;
                }
            }

            if (!startExist) {

                String message = "No start event found in scope " + scope + ".";

                BPMNError error = new BPMNError("SE-01", "Missing Start Event", SE, scope, message,
                        errorNodes, errorEdges, Severity.ERROR);

                errorList.add(error);
            }
        }
    }

    // ✅SE-02, normal check
    public void seMissingEnd() {

        for (List<Node> nodeList : graph.getScopeNodes().values()) {

            // no related node needed
            List<Node> errorNodes = new ArrayList<>();
            // no related edge needed
            List<Edge> errorEdges = new ArrayList<>();

            String scope = graph.getScope(nodeList.get(0));

            boolean endExist = false;
            for (Node node : nodeList) {
                if (node.getType() == NodeType.ENDEVENT) {
                    endExist = true;
                    break;
                }
            }

            if (!endExist) {
                String message = "No end event found in scope " + scope + ".";

                BPMNError error = new BPMNError("SE-02", "Missing End Event", SE, scope, message,
                        errorNodes, errorEdges, Severity.ERROR);

                errorList.add(error);
            }
        }
    }

    // ✅SE-03, normal check
    public void seMultipleStart() {

        for (List<Node> nodeList : graph.getScopeNodes().values()) {

            // equivalent to final error list
            List<Node> starts = nodeList.stream().filter(node -> node.getType().equals(NodeType.STARTEVENT)).toList();
            // no related edge needed
            List<Edge> errorEdges = new ArrayList<>();

            String scope = graph.getScope(nodeList.get(0));

            int number = starts.size();

            String message = "There exists" + number + " start events in scope " + scope + " (expected exactly one).";

            if (number > 1) {

                BPMNError error = new BPMNError("SE-03", "Multiple Start Events", SE, scope, message,
                        starts, errorEdges, Severity.WARNING);

                errorList.add(error);
            }
        }
    }

    // ✅SE-04, normal check
    public void seStartWithIncoming() {

        for (Node node : nodes.values()) {

            List<Node> errorNodes = new ArrayList<>();
            String scope = graph.getScope(node);

            if (node.getType() == NodeType.STARTEVENT && !node.getIncomingEdges().isEmpty()) {

                errorNodes.add(node);
                List<Edge> errorEdges = new ArrayList<>(node.getIncomingEdges());

                String message = "Start event '" + node + "' has " + node.getIncomingEdges().size() + " incoming sequence flow(s).";

                BPMNError error = new BPMNError("SE-04", "Start Event with Incoming Sequence Flow", SE,
                        scope, message, errorNodes, errorEdges, Severity.ERROR);

                errorList.add(error);
            }
        }
    }

    // ✅SE-05, normal check
    public void seEndWithOutgoing() {

        for (Node node : nodes.values()) {

            List<Node> errorNodes = new ArrayList<>();
            String scope = graph.getScope(node);

            if (node.getType() == NodeType.ENDEVENT && !node.getOutgoingEdges().isEmpty()) {

                errorNodes.add(node);
                List<Edge> errorEdges = new ArrayList<>(node.getOutgoingEdges());

                String message = "End event '" + node + "' has " + node.getOutgoingEdges().size() + " outgoing sequence flow(s).";

                BPMNError error = new BPMNError("SE-05", "End Event with Outgoing Sequence Flow", SE,
                        scope, message, errorNodes, errorEdges, Severity.ERROR);

                errorList.add(error);
            }
        }
    }

// ---------------------------------------------------------------------------------------------------------------------

    // ✅GTW-01, normal check
    public void gtwImplicitSplit() {

        for (Node node : nodes.values()) {

            // use normal split check (not loop free)
            if (!node.isGateway() && this.graph.isSplit(node)) {

                // one node each time
                List<Node> errorNodes = new ArrayList<>();
                errorNodes.add(node);

                // related edges of "split" = outgoings
                List<Edge> errorEdges = new ArrayList<>(node.getOutgoingEdges());

                String scope = graph.getScope(node);

                String message = "Non-gateway node '" + node + "' has " + node.getOutgoingEdges().size() +
                        " outgoing flows (implicit split).";

                BPMNError error = new BPMNError("GTW-01", "Implicit Split", GTW, scope, message
                        , errorNodes, errorEdges, Severity.WARNING);

                errorList.add(error);
            }
        }
    }

    // ✅GTW-02, normal check
    public void gtwImplicitJoin() {

        for (Node node : nodes.values()) {

            if (!node.isGateway() && this.graph.isMerge(node)) {

                List<Node> errorNodes = new ArrayList<>();
                errorNodes.add(node);

                List<Edge> errorEdges = new ArrayList<>(node.getIncomingEdges());

                String scope = graph.getScope(node);

                String message = "Non-gateway node '" + node + "' has " + node.getIncomingEdges().size() +
                        " incoming flows (implicit join).";

                BPMNError error = new BPMNError("GTW-02", "Implicit Join", GTW, scope, message
                        , errorNodes, errorEdges, Severity.WARNING);

                errorList.add(error);
            }
        }
    }

    // ✅GTW-03, with token check
    public void gtwMismatched() {

        List<Node> gateways = this.nodes.values().stream().filter(Node::isGateway).toList();

        for (Node gateway : gateways) {
            if (!this.graph.isLoopFreeMerge(gateway)) {
                continue;
            }

            List<Edge> incomings = graph.getLoopFreeIn().get(gateway);

            int num = 0;
            Set<Node> errorNodes = new LinkedHashSet<>();
            Set<Edge> errorEdges = new LinkedHashSet<>();

            for (Edge in : incomings) {

                List<TokenLabel> labelList = this.tokenLabelEngine.getEdgeTokens().get(in);

                for (TokenLabel label : labelList) {
                    Node split = this.tokenLabelEngine.getLastNode(label.getSplits());
                    if (!split.getType().equals(gateway.getType()) && split.isGateway()) {

                        errorEdges.add(in);
                        errorNodes.add(split);
                        num++;
                    }
                }
            }

            String scope = this.graph.getScope(gateway);

            if (num > 0) {

                StringBuilder splits = new StringBuilder();
                for (Node s : errorNodes) {
                    splits.append(s);
                }

                String message = "Merge " + gateway.getType().name().toLowerCase() + " '" + gateway + "' joins " +
                        "split gateways: [" + splits + "], that have different type.";

                BPMNError error = new BPMNError("GTW-03", "Mismatched Gateway Types", GTW, scope, message
                        , errorNodes.stream().toList(), errorEdges.stream().toList(), Severity.ERROR);

                errorList.add(error);
            }
        }
    }

    // ✅GTW-04, with token check
    public void gtwNestingViolation() {

        for (String scope : graph.getScopeNodes().keySet()) {

            List<Node> nodesInScope = graph.getScopeNodes().get(scope);

            // 找到所有的merge点，查看当前状态（nodeTokens）mergeMap
            List<Node> joins = nodesInScope.stream().filter(node ->
                    graph.isLoopFreeMerge(node) && !node.getType().equals(NodeType.ENDEVENT)
            ).toList();

            for (Node join : joins) {

                List<Edge> incomings = graph.getLoopFreeIn().get(join);

                List<TokenLabel> inLabels = incomings.stream()
                        // a list of tokenLabels, with flat and final stream()
                        .flatMap(in -> tokenLabelEngine.getEdgeTokens().get(in).stream())
                        .toList();

                List<Node> splits = inLabels.stream()
                        // to another type with map (only one object)
                        .map(label -> tokenLabelEngine.getLastNode(label.getSplits()))
                        .filter(split -> split != null && split.getType() != NodeType.DUMMY)
                        .distinct().toList();

                List<Node> errorNodes = new ArrayList<>();
                errorNodes.add(join);
                errorNodes.addAll(splits);

                // TODO 拼edge
                List<Edge> errorEdges = new ArrayList<>();

                StringBuilder splitReport = new StringBuilder();

                for (int i = 0; i < splits.size(); i++) {
                    if (i != splits.size() - 1) {
                        splitReport.append("'").append(splits.get(i)).append("', ");
                    } else {
                        splitReport.append("'").append(splits.get(i)).append("'");
                    }

                }

                // only one split

                if (splits.size() == 1 && !tokenLabelEngine.getCleanMergeMap().containsKey(join)) {
                    Node singleS = splits.get(0);

                    LinkedHashMap<Integer, Set<Node>> map = new LinkedHashMap<>(tokenLabelEngine.getSplitMap().get(singleS));

                    // branches that meet at this merge point
                    List<Integer> meet = inLabels.stream()
                            .map(label -> {
                                Node lastSplit = tokenLabelEngine.getLastNode(label.getSplits());
                                return label.getSplits().get(lastSplit);
                            })
                            .distinct()
                            .toList();

                    Set<Node> mergedFinal = new LinkedHashSet<>();
                    for (int b : meet) {
                        mergedFinal.addAll(map.get(b));
                        map.remove(b);
                    }

                    boolean report = this.isReport(mergedFinal, map);

                    if (report) {
                        String message = "Merge Node '" + join + "' joins only " + meet.size() +
                                " branch(es) of split gateway '" + singleS +
                                "', while the other branches could be merged with them afterward. " +
                                "There exist violated nesting issues.";

                        BPMNError error = new BPMNError("GTW-04", "Gateway Nesting Violation", GTW, scope, message
                                , errorNodes, errorEdges, Severity.WARNING);

                        errorList.add(error);
                    }


                } else if (splits.size() > 1) {

                    String message = "Merge Node '" + join + "' joins " + splits.size() +
                            " split gateways: [" + splitReport + "], there exist violated nesting issues.";

                    BPMNError error = new BPMNError("GTW-04", "Gateway Nesting Violation", GTW, scope, message
                            , errorNodes, errorEdges, Severity.WARNING);

                    errorList.add(error);
                }
            }

        }

    }

    // ✅GTW-05, normal check
    public void gtwMultipleRoles() {

        for (Node node : nodes.values()) {

            if (node.isGateway() && node.getIncomingEdges().size() > 1 && node.getOutgoingEdges().size() > 1) {

                String scope = graph.getScope(node);

                List<Node> errorNodes = new ArrayList<>();
                errorNodes.add(node);

                List<Edge> errorEdges = new ArrayList<>();
                errorEdges.addAll(node.getIncomingEdges());
                errorEdges.addAll(node.getOutgoingEdges());

                String message = "Gateway '" + node + "' is used as both split and join.";

                BPMNError error = new BPMNError("GTW-05", "Gateway Used as Both Split and Join",
                        GTW, scope, message, errorNodes, errorEdges, Severity.WARNING);

                errorList.add(error);
            }
        }

    }

    // ✅GTW-06, normal check
    public void gtwRedundant() {

        for (Node node : nodes.values()) {

            List<Node> errorNodes = new ArrayList<>();
            List<Edge> errorEdges = new ArrayList<>();
            String scope = graph.getScope(node);

            // all cross-scope-related put in sub
            if (node.isGateway() && node.getIncomingEdges().size() == 1
                    && node.getOutgoingEdges().size() == 1) {

                errorNodes.add(node);

                errorEdges.addAll(node.getIncomingEdges());
                errorEdges.addAll(node.getOutgoingEdges());

                String message = "Gateway '" + node + "' has exactly one incoming and one outgoing flow and has no routing effect.";

                BPMNError error = new BPMNError("GTW-06", "Redundant Gateway", GTW, scope, message,
                        errorNodes, errorEdges, Severity.WARNING);

                errorList.add(error);
            }
        }
    }

// ---------------------------------------------------------------------------------------------------------------------

    // ✅XOR-01, normal check
    public void xorMissingCondition() {

        for (Node node : nodes.values()) {

            if (node.getType() == NodeType.EXCLUSIVEGATEWAY) {

                String scope = graph.getScope(node);

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

                    String message = "XOR gateway '" + node + "' has " + without.size() +
                            " outgoing flow(s) without a condition (at most one default flow is allowed).";

                    BPMNError error = new BPMNError("XOR-01", "Missing Condition on XOR Outgoing Flow",
                            XOR, scope, message, errorNode, without, Severity.ERROR);

                    errorList.add(error);
                }
            }
        }
    }

// ---------------------------------------------------------------------------------------------------------------------

    // ❓AND-01, with token check
    public void andMismatch() {

        for (String scope : graph.getScopeNodes().keySet()) {

            List<Node> nodeInScope = graph.getScopeNodes().get(scope);

            List<Node> parallelMerge = nodeInScope.stream()
                    .filter(node -> node.getType().equals(NodeType.PARALLELGATEWAY) && getGraph().isLoopFreeMerge(node))
                    .toList();

            // prepare arrivalTokens (flat)
            for (Node parallel : parallelMerge) {
                List<Edge> incomings = graph.getLoopFreeIn().get(parallel);

                List<TokenLabel> arrivals = new ArrayList<>();
                //LinkedHashMap<TokenLabel, LinkedHashMap<Node, Integer>> allWaySplits = new LinkedHashMap<>();

                for (Edge in : incomings) {
                    arrivals.addAll(tokenLabelEngine.getEdgeTokens().get(in));
//                    for (TokenLabel l : tokenLabelEngine.getEdgeTokens().get(in)) {
//                        allWaySplits.put(l, l.getSplits());
//                    }
                }

                LinkedHashMap<Node, Set<Integer>> lasts = this.getLastSplitMap(arrivals);

//                for (TokenLabel label : arrivals) {
//                    Node last = tokenLabelEngine.getLastNode(label.getSplits());
//                    int branchIndex = label.getSplits().get(last);
//                    Set<Integer> branches = new HashSet<>();
//                    if (lasts.containsKey(last)) {
//                        branches = lasts.get(last);
//                    }
//                    branches.add(branchIndex);
//                    lasts.put(last, branches);
//                }

                List<Node> lastSplits = lasts.keySet().stream().toList();

                List<Node> errorNodes = new ArrayList<>();

                // TODO 拼edge
                List<Edge> errorEdges = new ArrayList<>();
                StringBuilder builder = new StringBuilder();

                boolean report = false;

                // if only one split, then check only the type: parallel or task which with only condition-free branches
                if (lastSplits.size() == 1) {
                    Node split = lastSplits.get(0);

                    boolean isTask = split.getType().equals(NodeType.TASK);
                    Set<Integer> taskBranches = new HashSet<>();

                    if (!split.getType().equals(NodeType.PARALLELGATEWAY)
                            && !split.getType().equals(NodeType.DUMMY)) {

                        if (isTask) {
                            // 如果有带condition的则需要报
                            taskBranches = this.branchWithConditions(split, lasts.get(split));
                        }

                        if (!taskBranches.isEmpty() || !isTask) {
                            errorNodes.add(parallel);
                            errorNodes.add(split);

                            builder.append("'").append(split).append("'");
                            report = true;
                        }

                    }
                } else if (lastSplits.size() > 1) {
                    List<Node> issueSplits = this.findIssueSplit(arrivals);

                    if (!issueSplits.isEmpty()) {
                        errorNodes.add(parallel);
                        errorNodes.addAll(issueSplits);
                        report = true;
                    }
                }


                if (report) {
                    String message = "Parallel join-gateway '" + parallel + "' has risk not to be activated due to " +
                            "ancestor-non-parallel-split node: [" + builder + "].";

                    BPMNError error = new BPMNError("AND-01", "AND Join Deadlock Risk", AND, scope, message,
                            errorNodes, errorEdges, Severity.ERROR);

                    errorList.add(error);
                }

            }
        }

    }

// ---------------------------------------------------------------------------------------------------------------------

    // ✅OR-01, normal check
    public void orMissingCondition() {

        for (Node node : nodes.values()) {

            if (node.getType() == NodeType.INCLUSIVEGATEWAY) {

                int conditionNum = 0;
                List<Edge> without = new ArrayList<>();

                for (Edge edge : node.getOutgoingEdges()) {

                    if (edge.getCondition() != null && !edge.getCondition().isEmpty()) {
                        conditionNum++;
                    } else {
                        without.add(edge);
                    }
                }

                if (conditionNum < node.getOutgoingEdges().size() - 1) {

                    String scope = graph.getScope(node);

                    List<Node> errorNodes = new ArrayList<>();
                    errorNodes.add(node);

                    String message = "OR gateway '" + node + "' has " + without.size() +
                            " outgoing flow(s) without a condition (at most one default flow is allowed).";

                    BPMNError error = new BPMNError("OR-01", "Missing Condition on OR Outgoing Flow",
                            OR, scope, message, errorNodes, without, Severity.ERROR);

                    errorList.add(error);
                }
            }
        }
    }

// ---------------------------------------------------------------------------------------------------------------------

    // ✅SUB-01, normal check
    public void subEmptySubprocess() {

        for (Node node : nodes.values()) {

            if (node.getType() == NodeType.SUBGRAPH) {

                String subId = node.getId();

                // "Subprocess:[" + node.getLocation() + "]"
                String scopeName = "Subprocess:[" + subId + "]";

                if (!graph.getScopeNodes().containsKey(scopeName)) {

                    List<Node> errorNodes = new ArrayList<>();
                    errorNodes.add(node);

                    List<Edge> errorEdges = new ArrayList<>();

                    String scope = graph.getScope(node);

                    String message = "Subprocess '" + node + "' does not contain any nodes.";

                    BPMNError error = new BPMNError("SUB-01", "Empty Subprocess", SUB, scope, message
                            ,errorNodes, errorEdges, Severity.ERROR);

                    errorList.add(error);
                }
            }
        }
    }

    // ✅SUB-02, normal check
    public void subBoundaryViolation() {

        for (Edge edge : edges) {

            Node source = nodes.get(edge.getSourceKey());
            Node target = nodes.get(edge.getTargetKey());

            if (source == null || target == null) {
                continue;
            }

            if (!graph.getScope(source).equals(graph.getScope(target))) {

                List<Node> errorNodes = new ArrayList<>();
                errorNodes.add(source);
                errorNodes.add(target);

                String scope = graph.getScope(source);

                List<Edge> errorEdges = new ArrayList<>();
                errorEdges.add(edge);

                String message = "Sequence flow from '" + source.getKey() + "' to '" + target.getKey() + "' crosses a subprocess boundary.";

                BPMNError error = new BPMNError("SUB-02", "Subprocess Boundary Violation", SUB, scope,
                        message, errorNodes, errorEdges, Severity.ERROR);

                this.errorList.add(error);
            }
        }
    }

// ---------------------------------------------------------------------------------------------------------------------

    // ✅LBL-01, normal check
    public void lblDuplicateName() {

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

                List<Edge> errorEdges = new ArrayList<>();

                StringBuilder s = new StringBuilder();

                for (int i = 0; i < ln.size(); i++) {
                    if (i == 0) {
                        s.append("'").append(ln.get(i)).append("'");
                    } else {
                        s.append(", '").append(ln.get(i)).append("'");
                    }
                }

                String message = "Task label '" + ln.get(0).getLabel() + "' is repeatedly used by tasks: [" + s + "].";
                String scope = "global";

                BPMNError error = new BPMNError("LBL-01", "Duplicate Activity Name", LBL, scope, message,
                        ln, errorEdges, Severity.WARNING);

                errorList.add(error);
            }
        }
    }

// ---------------------------------------------------------------------------------------------------------------------

    // ✅EDGE-01, normal check
    public void edgeDuplicateFlow() {

        LinkedHashMap<String, List<Edge>> sameEdge = new LinkedHashMap<>();

        for (Edge edge : edges) {

            String key = edge.getSourceKey() + edge.getTargetKey();
            if (!sameEdge.containsKey(key)) {
                List<Edge> edgeList = new ArrayList<>();
                edgeList.add(edge);
                sameEdge.put(key, edgeList);
            } else {
                sameEdge.get(key).add(edge);
            }
        }

        for (String key : sameEdge.keySet()) {

            if (sameEdge.get(key).size() > 1) {

                Edge edge = sameEdge.get(key).get(0);
                Node source = nodes.get(edge.getSourceKey());
                Node target = nodes.get(edge.getTargetKey());

                List<Node> errorNodes = new ArrayList<>();
                List<Edge> errorEdges = sameEdge.get(key);

                errorNodes.add(source);
                errorNodes.add(target);

                String scope = "global";

                String message = errorEdges.size() + " sequence flows are between '" + source + "' and '" + target
                        + "', which is redundant.";

                BPMNError error = new BPMNError("EDGE-01", "Duplicate Sequence Flow",
                        EDGE, scope, message, errorNodes, errorEdges, Severity.WARNING);

                errorList.add(error);
            }
        }
    }

// ---------------------------------------------------------------------------------------------------------------------

    // ✅LOOP-01, reachability, back edge related
    public void loopWithoutReachableEnd() {

        for (List<Node> nodeList : graph.getScopeNodes().values()) {

            boolean hasEndevent = false;

            for (Node n : nodeList) {
                if (n.getType() == NodeType.ENDEVENT) {
                    hasEndevent = true;
                    break;
                }
            }

            if (hasEndevent) {

                String scope = graph.getScope(nodeList.get(0));

                Set<Edge> backEdges = graph.getScopeBackEdges().get(scope);

                if (!backEdges.isEmpty()) {

                    List<Edge> edgesInScope = graph.getScopeEdges().get(scope);

                    Set<String> result = new HashSet<>();

                    for (Edge loop : backEdges) {

                        // find all reachable node from loop-start
                        Node enterNode = nodes.get(loop.getTargetKey());

                        if (enterNode != null && !result.contains(enterNode.getKey())) {

                            List<Node> reachable = this.graph.getArrival(edgesInScope, enterNode);
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

                                String message = "Loop entered at '" + enterNode +
                                        "' cannot reach any end event in its scope. It is a live lock.";

                                BPMNError error = new BPMNError("LOOP-01", "Loop Without Reachable End Event",
                                        LOOP, scope, message, errorNodes, errorEdges, Severity.ERROR);

                                errorList.add(error);
                            }
                        }
                    }
                }
            }
        }
    }

    // ✅LOOP-02, back edge related
    public void loopInvalidGateway() {

        for (List<Node> nodeList : graph.getScopeNodes().values()) {

            String scope = graph.getScope(nodeList.get(0));
            Set<Edge> loopEdges = this.graph.getScopeBackEdges().get(scope);

            for (Edge edge : loopEdges) {

                Node exitLoop = nodes.get(edge.getSourceKey());
                Node enterLoop = nodes.get(edge.getTargetKey());

                boolean parallel = exitLoop.getType() == NodeType.PARALLELGATEWAY
                        || enterLoop.getType() == NodeType.PARALLELGATEWAY;

                if (parallel) {

                    List<Node> errorNodes = new ArrayList<>();
                    errorNodes.add(exitLoop);
                    errorNodes.add(enterLoop);

                    List<Edge> errorEdges = new ArrayList<>();
                    errorEdges.add(edge);

                    String message = "Loop with back-edge '" + exitLoop + "' to '" +
                            enterLoop + "' is controlled by a parallel (AND) gateway.";

                    BPMNError error = new BPMNError("LOOP-02", "Loop Controlled by AND Gateway",
                            LOOP, scope, message, errorNodes, errorEdges, Severity.ERROR);

                    errorList.add(error);
                }
            }
        }
    }

// ---------------------------------------------------------------------------------------------------------------------

    // private Set<Integer> branchWithConditions(Node split, List<TokenLabel> tokenLabels) {
    private Set<Integer> branchWithConditions(Node split, Set<Integer> lasts) {
        // boolean noWay = false;
        //Set<Integer> index = lasts.get(split);
        LinkedHashMap<Integer, Boolean> conditionStates = graph.getConditionSplitTask().get(split);

        return lasts.stream()
                .filter(integer -> conditionStates.containsKey(integer) && conditionStates.get(integer))
                .collect(Collectors.toSet());
    }

    private LinkedHashMap<Node, Set<Integer>> getLastSplitMap(List<TokenLabel> tokenLabels) {

        LinkedHashMap<Node, Set<Integer>> lasts = new LinkedHashMap<>();

        for (TokenLabel label : tokenLabels) {
            Node last = tokenLabelEngine.getLastNode(label.getSplits());
            addToPair(lasts, label, last);
        }

        return lasts;
    }

    private LinkedHashMap<Node, Set<Integer>> getSplitAllTogether(List<TokenLabel> tokenLabels) {
        LinkedHashMap<Node, Set<Integer>> splits = new LinkedHashMap<>();

        for (TokenLabel label : tokenLabels) {

            for (Node node : label.getSplits().keySet()) {
                addToPair(splits, label, node);
            }

        }

        return splits;
    }

    private void addToPair(LinkedHashMap<Node, Set<Integer>> splits, TokenLabel label, Node node) {
        int branchIndex = label.getSplits().get(node);
        Set<Integer> branches = new HashSet<>();
        if (splits.containsKey(node)) {
            branches = splits.get(node);
        }
        branches.add(branchIndex);
        splits.put(node, branches);
    }


    private boolean hasCondition(Node node, List<TokenLabel> tokenLabels, boolean onlyLast) {
        if (!onlyLast) {
            return !this.branchWithConditions(node, this.getSplitAllTogether(tokenLabels).get(node)).isEmpty();
        } else {
            return !this.branchWithConditions(node, this.getLastSplitMap(tokenLabels).get(node)).isEmpty();
        }


    }

    private boolean isReport(Set<Node> mergedFinal,LinkedHashMap<Integer, Set<Node>> map) {

        for (Set<Node> others : map.values()) {
            // 如果相同则直接报，如果不同则判断一下新的是否去了一个end-event，如果不是end-event也报
            for (Node otherFinal : others) {
                if (mergedFinal.contains(otherFinal)) {
                    return true;
                } else {
                    if (!otherFinal.getType().equals(NodeType.ENDEVENT)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private List<Node> findIssueSplit(List<TokenLabel> tokenLabels) {

        // TODO 1. 找共同的祖先

        // tokenLabel <-> getSplits()
        Set<Node> issues = new LinkedHashSet<>();

        // 在比较的时候需要额外比较ancestor的branch是否一致
        Node ancestor = null;

        TokenLabel shortest = tokenLabels.stream()
                .min(Comparator.comparingInt(tokenLabel -> tokenLabel.getSplits().size()))
                .orElse(tokenLabels.get(0));


        for (Node split : shortest.getSplits().keySet()) {
            boolean exist = true;
            for (TokenLabel other : tokenLabels) {
                if (other.equals(shortest)) {
                    continue;
                }
                if (!other.getSplits().containsKey(split)) {
                    exist = false;
                    break;
                }
            }
            if (exist) {
                ancestor = split;
            }
        }

        Set<Node> between = new LinkedHashSet<>();

        for (TokenLabel label : tokenLabels) {

            boolean start = false;
            for (Node node : label.getSplits().keySet()) {
                if (!start) {
                    if (node == ancestor) {
                        start = true;
                    }
                }

                if (start) {
                    if (node != ancestor) {
                        between.add(node);
                    }
                }
            }
        }

        Set<Integer> ancestorIndex = this.getSplitAllTogether(tokenLabels).get(ancestor);

        // ancestor in report: not parallel, not dummy, if task has edge with condition
        if (ancestor != null && !ancestor.getType().equals(NodeType.DUMMY)) {
            if ((!ancestor.getType().equals(NodeType.PARALLELGATEWAY)
                    && !(ancestor.getType().equals(NodeType.TASK) && !this.hasCondition(ancestor, tokenLabels, false)))
                    && ancestorIndex.size() > 1) {
                issues.add(ancestor);

            }
        }

        for (Node node : between) {
            if ((!node.getType().equals(NodeType.PARALLELGATEWAY)
                    && !(node.getType().equals(NodeType.TASK) && !this.hasCondition(node, tokenLabels, false)))) {
                issues.add(node);
            }
        }

        return issues.stream().toList();
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

    public ProcessGraph getGraph() {
        return graph;
    }

    public void setGraph(ProcessGraph graph) {
        this.graph = graph;
    }

    public TokenLabelEngine getTokenLabelEngine() {
        return tokenLabelEngine;
    }

    public void setTokenLabelEngine(TokenLabelEngine tokenLabelEngine) {
        this.tokenLabelEngine = tokenLabelEngine;
    }

}

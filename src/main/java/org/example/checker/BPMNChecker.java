package org.example.checker;

import org.example.graph.ProcessGraph;
import org.example.graph.TokenLabelEngine;
import org.example.model.*;
import org.example.parser.MermaidParser;

import java.util.*;

public class BPMNChecker {

    // private MermaidParser parser;
    private final LinkedHashMap<String, Node> nodes;
    private final List<Edge> edges;
    private List<BPMNError> errorList;
    private ProcessGraph graph;
    private TokenLabelEngine tokenLabelEngine;

    private LinkedHashMap<String, List<Node>> scopeNodes;
    private LinkedHashMap<String, Set<Edge>> scopeBackEdges;
    private LinkedHashMap<String, List<Edge>> scopeEdges;

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

        // preset everything that can be set.

        // scope nodes
        this.scopeNodes = this.graph.getScopeNodes();

        // scope back edges
        this.scopeBackEdges = this.graph.getScopeBackEdges();
        this.scopeEdges = this.graph.getScopeEdges();

        this.loopFreeIn = graph.getLoopFreeIn();
        this.loopFreeOut = graph.getLoopFreeOut();

        this.edgeTokens = tokenLabelEngine.getEdgeTokens();
        this.nodeTokens = tokenLabelEngine.getNodeTokens();

        this.mergeMap = this.tokenLabelEngine.getMergeMap();
        this.splitMap = this.tokenLabelEngine.getSplitMap();
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

                BPMNError error = new BPMNError("CON-01", "Isolated Node", CON, this.getScope(node),
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

                String scope = this.getScope(node);
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

                String scope = this.getScope(node);
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
        for (List<Node> nodeList : scopeNodes.values()) {

            Set<Node> reachable = this.graph.reachableInScope(nodeList);
            List<Node> unreachable = nodeList.stream().filter(node -> !reachable.contains(node)).toList();

            for (Node errorNode : unreachable) {

                // the node that is unreachable (report one by one)
                List<Node> errorNodes = new ArrayList<>();
                errorNodes.add(errorNode);

                // no related edges
                List<Edge> errorEdges = new ArrayList<>();

                String scope = this.getScope(errorNode);
                String message = "Node '" + errorNode + "' is not reachable from any start event in its scope.";

                BPMNError error = new BPMNError("CON-04", "Unreachable Activity", CON, scope, message,
                        errorNodes, errorEdges, Severity.ERROR);

                errorList.add(error);
            }

        }

    }

    // ✅CON-05, need scope check and reachability check
    public void conEndEventUnreachableFromStart() {

        for (List<Node> nodeList : scopeNodes.values()) {

            Set<Node> reachable = this.graph.reachableInScope(nodeList);

            for (Node node : nodeList) {
                if (node.getType() == NodeType.ENDEVENT && !reachable.contains(node)) {

                    // single error node
                    List<Node> errorNodes = new ArrayList<>();
                    errorNodes.add(node);

                    List<Edge> errorEdges = new ArrayList<>();

                    String scope = this.getScope(node);
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

        for (List<Node> nodeList : scopeNodes.values()) {

            // no related node needed
            List<Node> errorNodes = new ArrayList<>();
            // no related edge needed
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

                String message = "No start event found in scope " + scope + ".";

                BPMNError error = new BPMNError("SE-01", "Missing Start Event", SE, scope, message,
                        errorNodes, errorEdges, Severity.ERROR);

                errorList.add(error);
            }
        }
    }

    // ✅SE-02, normal check
    public void seMissingEnd() {

        for (List<Node> nodeList : scopeNodes.values()) {

            // no related node needed
            List<Node> errorNodes = new ArrayList<>();
            // no related edge needed
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
                String message = "No end event found in scope " + scope + ".";

                BPMNError error = new BPMNError("SE-02", "Missing End Event", SE, scope, message,
                        errorNodes, errorEdges, Severity.ERROR);

                errorList.add(error);
            }
        }
    }

    // ✅SE-03, normal check
    public void seMultipleStart() {

        for (List<Node> nodeList : scopeNodes.values()) {

            // equivalent to final error list
            List<Node> starts = nodeList.stream().filter(node -> node.getType().equals(NodeType.STARTEVENT)).toList();
            // no related edge needed
            List<Edge> errorEdges = new ArrayList<>();

            String scope = this.getScope(nodeList.get(0));

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
            String scope = this.getScope(node);

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
            String scope = this.getScope(node);

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

                String scope = this.getScope(node);

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

                String scope = this.getScope(node);

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

    // GTW-04, with token check
    // TODO with TokenNode
    public void gtwNestingViolation() {


//                        BPMNError error = new BPMNError("GTW-04", "Gateway Nesting Violation",
//                                "General Gateway Errors", scope,
//                                "Branches of split gateway '" + node.getKey() + "' merge at " + joinKeys.size() + " different join nodes.",
//                                errorNodes, errorEdges, Severity.WARNING);
//                        errorList.add(error);


//                    BPMNError error = new BPMNError("GTW-04", "Gateway Nesting Violation",
//                            "General Gateway Errors", scope,
//                            "Split gateways: " + nodeKeys + " all merge at the same join node '" + joinKey
//                                    + "'; the blocks share one exit.",
//                            errorNodes, new ArrayList<>(), Severity.WARNING);
//                    errorList.add(error);
    }

    // GTW-05, normal check
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

    // GTW-06, normal check
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

// ---------------------------------------------------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------------------------------------------------

    // TODO new AND-01 logic
    public void andMismatch() {


//                BPMNError error = new BPMNError("AND-01", "AND Split and Join Branch Count Mismatch",
//                        "AND Gateway Errors", scope,
//                        "Branches of AND split '" + node.getKey() + "' do not synchronize at a single matching AND join.",
//                        errorNodes, errorEdges, Severity.ERROR);
//                errorList.add(error);

    }

// ---------------------------------------------------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------------------------------------------------

    // LBL
    public void lblDuplicateName() {
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

// ---------------------------------------------------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------------------------------------------------

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
                String scope = this.getScope(nodeList.get(0));
                Set<Edge> loopEdges = graph.getScopeBackEdges().get(scope);

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

                    Set<String> result = new HashSet<>();

                    for (Edge loop : loopEdges) {
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

            String scope = this.getScope(nodeList.get(0));
            Set<Edge> loopEdges = this.getScopeBackEdges().get(scope);

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

// ---------------------------------------------------------------------------------------------------------------------

    public LinkedHashMap<String, List<Edge>> getScopeEdges() {
        return scopeEdges;
    }

    public void setScopeEdges(LinkedHashMap<String, List<Edge>> scopeEdges) {
        this.scopeEdges = scopeEdges;
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

    //    private Node branchJoin(Node branchStart, String scope, Set<Edge> loopEdges) {
//        int balance  = 0;
//        Node current = branchStart;
//        Set<String> arrival = new HashSet<>();
//
//        while (current != null && arrival.add(current.getKey())) {
//
//            if (this.isMerge(current, loopEdges)) {
//                if (balance == 0) {
//                    return current;
//                } else {
//                    balance--;
//                }
//            }
//
//            if (this.isSplit(current, loopEdges)) {
//                balance++;
//            }
//
//            // only deal with edges not in loopEdges
//            Edge nextEdge = null;
//            for (Edge edge : current.getOutgoingEdges()) {
//                if (!loopEdges.contains(edge)) {
//                    nextEdge = edge;
//                    break;
//                }
//            }
//
//            if (nextEdge == null) {
//                return null;
//            }
//
//
//            String key = nextEdge.getTargetKey();
//            Node next = this.nodes.get(key);
//
//            // 保证在同一scope中
//            if (next == null || !this.getScope(next).equals(scope)) {
//                return null;
//            }
//
//            current = next;
//
//        }
//        return null;
//    }


//    private Node strictMatchingJoin(Node split, Set<Edge> loopEdges) {
//        String scope = this.getScope(split);
//        Node target = null;
//
//        for (Edge edge : split.getOutgoingEdges()) {
//
//            if (loopEdges.contains(edge)) {
//                continue;
//            }
//
//            Node start = nodes.get(edge.getTargetKey());
//            Node joinNode;
//            if (start == null) {
//                joinNode = null;
//            } else {
//                joinNode = this.branchJoin(start, scope, loopEdges);
//            }
//
//            if (joinNode == null) {
//                continue;
//            }
//
//            if (target == null) {
//                target = joinNode;
//                // reachedCount = 1;
////            } else if (target.getKey().equals(joinNode.getKey())) {
////                // reachedCount++;
//            } else if (!target.getKey().equals(joinNode.getKey())) {
//                return null;
//            }
//        }
//
//        return target;
//    }

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

    private String getScope(Node node) {
        return this.graph.getScope(node);
    }

}

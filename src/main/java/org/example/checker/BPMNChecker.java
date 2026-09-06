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

    // ❓GTW-04, with token check
    public void gtwNestingViolation() {
        // TODO 检查所有的merge点以及它们前序来的edge来自什么最近split
        //  1️⃣如果来自两个及两个以上的不同的split则视为一型gtw-04
        //  2️⃣如果来自同一个分支的部分非完整token，且未归branch没有自己到另一个不同的end-event

        // TODO 每个scope逐个检查
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
                if (splits.size() == 1) {
                    // whether exists in mergeMap?
                    if (!tokenLabelEngine.getMergeMap().containsKey(join)) {
                        // if other not arrival branches arrive at different end event, leave it.
                        // ONLY ONE SPLIT
                        Node singleS = splits.get(0);
                        
                        LinkedHashMap<Integer, Set<Node>> map = new LinkedHashMap<>(tokenLabelEngine.getSplitMap().get(singleS));

                        // branches that meet at this merge point
                        List<Integer> meet = inLabels.stream()
                                .map(label -> {
                                    Node lastSplit = tokenLabelEngine.getLastNode(label.getSplits());
                                    return label.getSplits().get(lastSplit);
                                })
                                .toList();

                        // TODO check the final point of other branches that didn't meet at this 'join',
                        //  compare them with all other branches' final point,
                        //  if any of them matches --> report error

                        // TODO 1: find all final point of branches who meet here

                        Set<Node> mergedFinal = new LinkedHashSet<>();
                        for (int b : meet) {
                            mergedFinal.addAll(map.get(b));
                            // 直接用map，移除之后只剩当前不在的
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
                        
                    }
                } else if (splits.size() > 1) {
                    // --> direct report
                    
                    String message = "Merge Node '" + join + "' joins " + splits.size() +
                            " split gateways: [" + splitReport + "], there exist violated nesting issues.";

                    BPMNError error = new BPMNError("GTW-04", "Gateway Nesting Violation", GTW, scope, message
                            , errorNodes, errorEdges, Severity.WARNING);

                    errorList.add(error);
                }
            }

        }

    }

    // ❓GTW-05, normal check
    public void gtwMultipleRoles() {
        for (Node node : nodes.values()) {
            if (node.isGateway() && node.getIncomingEdges().size() > 1 && node.getOutgoingEdges().size() > 1) {
                String scope = graph.getScope(node);
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

    // ❓GTW-06, normal check
    public void gtwRedundant() {

        for (Node node : nodes.values()) {

            List<Node> errorNodes = new ArrayList<>();
            List<Edge> errorEdges = new ArrayList<>();
            String scope = graph.getScope(node);

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

    // ❓XOR-01, normal check
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
    // ❓AND-01, with token check
    public void andMismatch() {


//                BPMNError error = new BPMNError("AND-01", "AND Split and Join Branch Count Mismatch",
//                        "AND Gateway Errors", scope,
//                        "Branches of AND split '" + node.getKey() + "' do not synchronize at a single matching AND join.",
//                        errorNodes, errorEdges, Severity.ERROR);
//                errorList.add(error);

    }

// ---------------------------------------------------------------------------------------------------------------------

    // ❓OR-01, normal check
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

                    String scope = graph.getScope(node);
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

    // ❓SUB-01, normal check
    public void subEmptySubprocess() {
        for (Node node : nodes.values()) {
            if (node.getType() == NodeType.SUBGRAPH) {

                String subId = node.getId();

                // "Subprocess:[" + node.getLocation() + "]"
                String scopeName = "Subprocess:[" + subId + "]";

                boolean exist = graph.getScopeNodes().containsKey(scopeName);

                if (!exist) {
                    List<Node> errorNodes = new ArrayList<>();
                    errorNodes.add(node);
                    List<Edge> errorEdges = new ArrayList<>();
                    String scope = graph.getScope(node);
                    BPMNError error = new BPMNError("SUB-01", "Empty Subprocess",
                            "Subprocess Errors", scope,
                            "Subprocess '" + node.getId() + "' contains no nodes.",
                            errorNodes, errorEdges, Severity.ERROR);
                    errorList.add(error);
                }
            }
        }
    }

    // ❓SUB-02, normal check
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

                String scope = graph.getScope(source);
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

    // ❓LBL-01, normal check
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

    // ❓EDGE-01, normal check
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
                    scope = graph.getScope(se.source);
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

    // ❓LOOP-01, reachability, back edge related
    public void loopWithoutReachableEnd() {
        // in a loop, it cant arrive at end event of this scope
        // for one node, endevent is unreachable for it
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

    // ❓LOOP-02, back edge related
    public void loopInvalidGateway() {

        for (List<Node> nodeList : graph.getScopeNodes().values()) {

            String scope = graph.getScope(nodeList.get(0));
            Set<Edge> loopEdges = this.graph.getScopeBackEdges().get(scope);

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

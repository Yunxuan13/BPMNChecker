package org.example.repair;

import org.example.model.BPMNError;
import org.example.model.Edge;
import org.example.model.Node;

import java.util.List;

public final class SuggestionBuilder {

    public static String suggest (BPMNError error) {
        // main error node
        String key = nodeKey(error, 0);



        switch (error.getErrorId()) {
            // Isolated Node
            case "CON-01":
                return "Connect '" + key + "' to the process, add an incoming sequence flow " +
                        "from a preceding element and an outgoing flow to a following one, " +
                        "or remove the node if it is not needed.";
            // missing  incoming
            case "CON-02":
                return "Add an incoming sequence flow to '" + key +
                        "' from a preceding element (for example the start event or an upstream task).";
            // missing outgoing
            case "CON-03":
                return "Add an outgoing sequence flow from '" + key +
                        "' to a following element (for example the next task or an end event).";
            // unreachable
            case "CON-04":
                return "Connect '" + key + "' to the flow so it becomes reachable from start, " +
                        "or remove it if it is not needed.";
            // end event unreachable from start
            case "CON-05":
                return "Create a path from a start event to end event '" + key + "', " +
                        "look for missing or misdirected sequence flows on the way.";
            // missing start
            case "SE-01":
                return "Declare a start event in this scope and connect it to the first element of the flow.";
            // missing end
            case "SE-02":
                return "Declare an end event in this scope and connect the final element of the flow to it.";
            // multiple start
            case "SE-03":
                return "Keep a single start event in this scope; if the extra start events mark alternative entries, " +
                        "merge them into one start event followed by a gateway.";
            // start with in
            case "SE-04":
                return "Remove the incoming sequence flow(s) of start event '" + key + "'.";
            // end with out
            case "SE-05":
                return "Remove the outgoing sequence flow(s) of end event '" + key + "'.";
            // implicit split
            case "GTW-01":
                return "Insert an explicit gateway after '" + key + "' and move its multiple outgoing flows onto that gateway.";
            // implicit merge
            case "GTW-02":
                return "Insert an explicit gateway before '" + key + "' and move its multiple incoming flows through that gateway.";
            // mismatched
            case "GTW-03":
                return "Make the gateway types same of this block: split '"+ key + "' and join '" + nodeKey(error, 1) + "'.";
            // nested
            case "GTW-04":
                return "Restructure the branches of split '" + key + "' so they all merge at a single join gateway before the block closes.";
            // both split and join
            case "GTW-05":
                return "Split gateway '" + key + "' into two: one gateway that only joins the incoming flows, " +
                        "then followed by one that only splits the outgoing flows.";
            // redundant
            case "GTW-06":
                return "Remove gateway '" + key + "' and connect its incoming flow's source directly to its outgoing flow's target.";
            // missing condition
            case "XOR-01":
                return "Add a condition label to each unlabelled outgoing flow of XOR gateway '" + key +
                        "' (syntax: '-->|condition|'); at most one flow may stay unlabelled as the default.";
            // branch mismatch
            case "AND-01":
                return "Rebalance the split parallel gateway '" + key + "', let every branch leaving the split reach one matching AND join, " +
                        "adding the missing branch flows or removing extra ones.";
            // missing condition
            case "OR-01":
                return "Add a condition label to each unlabelled outgoing flow of OR gateway '" + key +
                        "' (syntax: '-->|condition|'); at most one flow may stay unlabelled as the default.";
            // empty
            case "SUB-01":
                return "Nothing to change here. (It is acceptable, if subprocess is in form 'id:subprocess:(label)')";
            // boundary
            case "SUB-02":
                return "Remove the sequence flow from '" + edgeSource(error) + "' to '" + edgeTarget(error) +
                        "' that crosses the subprocess boundary.";
            // duplicate
            case "LBL-01":
                return "Rename the duplicated activities so that each activity label is unique.";
            // duplicate
            case "EDGE-01":
                return "Remove the duplicate sequence flow between '" + edgeSource(error) + "' and '" + edgeTarget(error) +
                        "', keeping a single flow.";
            // no reachable end
            case "LOOP-01":
                return "Add an exit to the loop entered at '" + key +
                        "', for example give one gateway inside the loop a conditional flow that leads towards an end event.";
            // and as loop gateway
            case "LOOP-02":
                return "Let an exclusive gateway control the loop between '" + edgeSource(error) + "' and '" +
                        edgeTarget(error) + "' instead of a parallel gateway.";
            default:
                return null;
        }
    }

    private static String nodeKey (BPMNError error, int index) {
        List<Node> nodes = error.getNodes();
        if (nodes == null || nodes.size() <= index || nodes.get(index) == null) {
            return "unknown node";
        }
        return nodes.get(index).getKey();
    }

    private static String edgeSource(BPMNError error) {
        List<Edge> edgeList = error.getEdges();
        if (edgeList == null || edgeList.isEmpty() || edgeList.get(0) == null) {
            return "unknown edge";
        }
        return edgeList.get(0).getSourceKey();
    }

    private static String edgeTarget(BPMNError error) {
        List<Edge> edgeList = error.getEdges();
        if (edgeList == null || edgeList.isEmpty() || edgeList.get(0) == null) {
            return "unknown edge";
        }
        return edgeList.get(0).getTargetKey();
    }

}

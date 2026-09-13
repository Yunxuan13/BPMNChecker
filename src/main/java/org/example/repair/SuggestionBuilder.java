package org.example.repair;

import org.example.model.BPMNError;
import org.example.model.Edge;
import org.example.model.Node;

import java.util.List;

public final class SuggestionBuilder {


    public static String suggest (BPMNError error) {

        // main error node
        String node = "";
        if (error.nodes() != null && !error.nodes().isEmpty()) {
            node = error.nodes().get(0).toString();
        }


        StringBuilder suggestion = new StringBuilder();

        String body = switch (error.errorId()) {

            // ✅Isolated Node
            case "CON-01" -> "Connect '" + node + "' to the process, " +
                    "either according to the process description, add an incoming sequence flow to a end-event, " +
                    "from a preceding element add an outgoing flow to a following one for a start-event, " +
                    "add appropriate incoming and outgoing edge(s) to the node if it is neither start nor end, " +
                    "or remove the node if it is not needed.";

            // ✅missing incoming
            case "CON-02" -> "Try to add an incoming sequence flow to '" + node +
                    "' from a preceding element. Keep the original meaning in process description.";

            // ✅missing outgoing
            case "CON-03" -> "Try to add an outgoing sequence flow from '" + node +
                    "' to a following element. Keep the original meaning in process description.";

            // ✅unreachable
            case "CON-04" -> "Try connect '" + node + "' to the flow so it becomes reachable from start, " +
                    "or remove it if it is not needed.";

            // ✅end event unreachable from start
            case "CON-05" -> "Try to create a path from a start event to end event '" + node + "', " +
                    "look for missing or misdirected sequence flows on the way.";

            // ✅missing start
            case "SE-01" -> "Declare a start event in this scope and connect it to the first element of the flow.";

            // ✅missing end
            case "SE-02" -> "Declare an end event in this scope and connect the final element of the flow to it.";

            // ✅multiple start
            case "SE-03" -> "Keep only single start event in this scope.";

            // ✅start with in
            case "SE-04" -> "Remove the incoming sequence flow(s) of start event '" + node + "'.";

            // ✅end with out
            case "SE-05" -> "Remove the outgoing sequence flow(s) of end event '" + node + "'.";

            // ✅implicit split
            case "GTW-01" -> "Any non-gateway node shouldn't have more than one outgoing sequence flows. " +
                    "Insert an appropriate gateway after '" + node + "' and move its multiple outgoing flows onto that gateway.";

            // ✅implicit merge
            case "GTW-02" -> "Any non-gateway node shouldn't have more than one incoming sequence flows. " +
                    "Insert an appropriate gateway before '" + node + "' and move its multiple incoming flows through that gateway.";

            // ✅mismatched
            case "GTW-03" -> "The type of the splits (" + getCompactNode(error, false) + ") merging at the join gateway '"
                    + node + "' should keep the same as the join." +
                    "Please keep, any split gateway is only merged at the paired, same-type, single join gateway.";

            // ✅nested
            case "GTW-04" -> "Restructure the blocks that violated the single-enter single-exit. " +
                    "Join node '" + node + "' should not merge more than one split gateway. " +
                    "Also, it cannot merge only part of branches while the other branches do not stop at OTHER end event than current branches'. " +
                    "Involved split nodes are: [" + getCompactNode(error, false) + "]. " +
                    "Try to close inner block before merging outer blocks. " +
                    "All branches that terminate at the same end event should converge at one and only one common merge node. " +
                    "Many splits merge at one join gateway is also not recommended, even if all branches are correctly merged.";

            // ✅both split and join
            case "GTW-05" -> "Gateway '" + node + "' played two roles (split and join). " +
                    "Try to add a split gateway after it and carry on the outgoing sequence flows of it. " +
                    "This current gateway keep the merging function, add a single sequence flow from current to the new generated split gateway.";

            // ✅redundant
            case "GTW-06" -> "Remove gateway '" + node + "' and connect its incoming flow's source directly to its outgoing flow's target.";

            // ✅missing condition
            case "XOR-01" -> "Add a condition label to each unlabelled outgoing flow of XOR gateway '" + node +
                    "' (syntax: '-->|condition|'); at most one flow may stay unlabelled as the default.";

            // ✅Deadlock risk
            case "AND-01" -> "There exist risk at the merge parallel gateway '" + node + "', " +
                    "split nodes: [" + getCompactNode(error, false) + "] lead to the issue, " +
                    "there exist possibility that merge gateway could not be activated " +
                    "because of the endless waiting for incoming sequence flows that will never arrive.";

            // ✅missing condition
            case "OR-01" -> "Add a condition label to each unlabelled outgoing flow of OR gateway '" + node +
                    "' (syntax: '-->|condition|'); at most one flow may stay unlabelled as the default.";

            // ✅empty
            case "SUB-01" -> "Add at least one element inside subprocess '" + node + "'.";

            // ✅boundary
            case "SUB-02" -> "Try to remove the sequence flow from (without label block) '" + edgeSource(error) + "' to '" + edgeTarget(error) +
                    "' that crosses the subprocess boundary or restructure the nodes in subgraph.";

            // ✅duplicate
            case "LBL-01" -> "Rename the duplicated activities so that each activity label is unique if it is not in conflict with the process description.";

            // ✅duplicate
            case "EDGE-01" -> "Remove the duplicate sequence flow between node with key (without label block) '"
                    + edgeSource(error) + "' and '" + edgeTarget(error) +
                    "', keep only single flow with accurate condition if it is needed.";

            // ✅no reachable end
            case "LOOP-01" -> "Add an exit to the loop entered at '" + node +
                    "', for example give one gateway inside the loop a conditional flow that leads towards an end event.";

            // ✅and as loop gateway
            case "LOOP-02" -> "Let an exclusive gateway control the loop. Parallel gateway is definitely prohibited for controlling loop. " +
                    "Involved parallel gateway that directly influenced the loop: [" + getCompactNode(error, true) + "].";

            default -> null;
        };

        suggestion.append(body);

        return suggestion.toString();
    }

    private static String getCompactNode(BPMNError error, boolean withMain) {

        List<Node> nodes = error.nodes();
        if (nodes == null) {
            return "unknown node";
        }

        StringBuilder builder = new StringBuilder();
        int start = withMain ? 0 : 1;

        for (int i = start; i < nodes.size(); i++) {
            if (i == start) {
                builder.append("'").append(nodes.get(i)).append("'");
            } else {
                builder.append(", '").append(nodes.get(i)).append("'");
            }
        }
        return builder.toString();
    }

    private static String edgeSource(BPMNError error) {
        List<Edge> edgeList = error.edges();
        if (edgeList == null || edgeList.isEmpty() || edgeList.get(0) == null) {
            return "unknown edge";
        }
        return edgeList.get(0).getSourceKey();
    }

    private static String edgeTarget(BPMNError error) {
        List<Edge> edgeList = error.edges();
        if (edgeList == null || edgeList.isEmpty() || edgeList.get(0) == null) {
            return "unknown edge";
        }
        return edgeList.get(0).getTargetKey();
    }

}

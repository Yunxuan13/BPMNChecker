package org.example.parser;

import org.example.graph.ProcessGraph;
import org.example.model.Edge;
import org.example.model.Node;
import org.example.model.NodeType;
import org.example.model.RawShape;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class MermaidParser {

    private final LinkedHashMap<String, Node> nodes;
    private final List<Edge> edges;

    private static final String SYNTAX_REMINDER = "The file was not structurally analyzed.";

    //
    private int currentInnerSubId = 0;


    public MermaidParser(String mermaidPath) throws IOException, InputValidationException {
        this.nodes = new LinkedHashMap<>();

        this.edges = new ArrayList<>();
        this.parse(mermaidPath);
    }

    private void parse(String mermaidPath) throws InputValidationException, IOException {

        try {
            List<String> lines = Files.readAllLines(Path.of(mermaidPath));

            // can contain same value
            Deque<String> subs = new ArrayDeque<>();

            Set<String> subgraphIds = new HashSet<>();

            for (String rawLine : lines) {
                String line = rawLine.strip();
                // here an invalid subgraph will not be added to the set due to getSubgraphId
                if (this.isSubgraph(line)) {
                    subgraphIds.add(this.getSubgraphId(line));
                    currentInnerSubId++;
                }
            }
            currentInnerSubId = 0;

            if (!this.hasStartLine(lines)) {
                throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "There must exist a start line starts with " +
                        "'graph LR' or 'graph TD' or 'flowchart LR'. " + SYNTAX_REMINDER);
            }

            for (String rawLine : lines) {
                String line = rawLine.strip();

                if (line.isEmpty() || line.equals("graph LR") || line.equals("graph TD") || line.equals("flowchart LR") || line.startsWith("%%") || line.equals("direction TD") || line.equals("direction LR")) {
                    continue;

                } else if (this.isSubgraph(line)) {


                    // possible to be parsed subgraph apple / subgraph apple banana / subgraph apple [banana orange] / subgraph apple[banana]
                    String subId = this.getSubgraphId(line);

                    // in mermaid, if a sentence begins with a "subgraph", the shape must be [], otherwise there will be syntax-error
                    // if subId already be register, then the new label will not be updated to the node
                    String key = subId + ":subgraph";
                    String subLabel;

                    // according to mermaid.live (tested on 7.15)
                    // node will be displayed with the name that appear afterward while subgraph remains the name that appear for the first time
                    if (nodes.containsKey(key)) {
                        subLabel = nodes.get(key).getLabel();
                    } else {
                        subLabel = this.getSubgraphLabel(line);
                    }

                    NodeType type = NodeType.SUBGRAPH;
                    RawShape rawShape = RawShape.SUBGRAPH;

                    this.updateNode(subs, key, subId, line, type, subLabel, rawShape);

                    subs.push(subId);
                    currentInnerSubId++;

                } else if (this.isSubgraphEnd(line)) {

                    if (subs.isEmpty()) {
                        throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "Unmatched 'end': no open subgraph block to close. " + SYNTAX_REMINDER);
                    }

                    subs.pop();


                } else if (this.isEdge(line)) {

                    String[] seperated = line.split("-->", -1);

                    if (seperated.length != 2 || seperated[0].isBlank() || seperated[1].isBlank()) {
                        throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "An edge declaration must contain exactly one '-->' and two endpoints. "
                                + SYNTAX_REMINDER);
                    }


                    String source = seperated[0].strip();
                    String right = seperated[1].strip();

                    String condition = null;
                    String target;

                    String targetKey;
                    String sourceKey;

                    if (right.startsWith("|")) {
                        int pos = right.indexOf("|", 1);
                        if (pos == -1) {
                            throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "In the line '" + line + "' has invalid condition block. " +SYNTAX_REMINDER);
                        }
                        condition = right.substring(1, pos).strip();
                        if (condition.isBlank()) {
                            condition = null;
                        }

                        target = right.substring(pos + 1).strip();
                    } else {
                        target = right;
                    }

                    // generate nodes
                    sourceKey = this.resolveEndpoint(source, subs, subgraphIds, line);
                    targetKey = this.resolveEndpoint(target, subs, subgraphIds, line);

                    // generate edge
                    Edge edge = new Edge(sourceKey, condition, targetKey);
                    this.edges.add(edge);

                } else if (this.isNode(line)) {

                    this.parseNode(line, subs);

                } else if (this.isNonNumericId(line)) {
                    throw new InputValidationException(Reason.NON_NUMERIC_BPMN_NODE_ID, "The ID of a node in non-numeric ("+ line + ") is out of scope. " + SYNTAX_REMINDER);
                } else {
                    throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "Unrecognized line '" + line + "' is neither a node declaration, an edge nor a subgraph construct. " + SYNTAX_REMINDER);
                }
            }

            if (!subs.isEmpty()) {
                throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "At least one subgraph block is not closed. " + SYNTAX_REMINDER);
            }

            if (this.nodes.isEmpty()) {
                throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "This file does not contain any node. " + SYNTAX_REMINDER);
            }

        } catch (IOException e) {
            throw new IOException("There exists errors while reading the lines of the file of this path '" + mermaidPath + "', " + e.getMessage());
        }
    }

    private boolean hasStartLine(List<String> lines) {
        for (String rawLine : lines) {
            String line = rawLine.strip();

            if (line.isEmpty() || line.startsWith("%%")) {
                continue;
            }

            return line.equals("graph LR") || line.equals("graph TD") || line.equals("flowchart LR");
        }
        return false;
    }

    private String getSubgraphId(String line) throws InputValidationException {
        String info = line.substring(8).strip();

        int labelBegin = info.indexOf("[");
        int labelEnd = info.lastIndexOf("]");

        if (this.hasLabelBlock(labelBegin, labelEnd)) {
            String middle = info.substring(0, labelBegin).strip();
            if (middle.contains(" ") || middle.isEmpty()) {
                throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "The subgraph id of line '"+ line + "' contains a space which cannot be connected by a sequence flow. " + SYNTAX_REMINDER);
            } else {
                return middle;
            }
        } else {
            if (info.isBlank() || info.isEmpty()) {
                throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "The subgraph of line '"+ line + "' contains neither an id nor a label, which is an invalid subgraph. " + SYNTAX_REMINDER);
            } else {
                if (info.contains(" ")) {
                    return "subGraph" + currentInnerSubId;
                } else {
                    return info;
                }
            }
        }
    }

    private boolean hasLabelBlock(int begin, int end) {
        return begin != -1 && end != -1 && end > begin;
    }

    private String getSubgraphLabel(String line) throws InputValidationException {
        String info = line.substring(8).strip();
        int labelBegin = info.indexOf("[");
        int labelEnd = info.lastIndexOf("]");

        if (hasLabelBlock(labelBegin, labelEnd)) {
            return info.substring(labelBegin + 1, labelEnd).strip();
        } else {
            return info;
        }
    }

    private String resolveEndpoint(String node, Deque<String> subs, Set<String> subgraphIds, String line) throws InputValidationException {
        if (this.isNode(node)) {
            return this.parseNode(node, subs).getKey();
        }
        if (subgraphIds.contains(node)) {
            return node + ":" + "subgraph";
        }

        if (this.isNonNumericId(node)) {
            throw new InputValidationException(Reason.NON_NUMERIC_BPMN_NODE_ID, "The ID of a node in non-numeric ("+ line + ") is out of scope. " + SYNTAX_REMINDER);
        }
        throw new InputValidationException(Reason.BARE_ID_PROBLEM, "Bare-ID node in Edge ("+ line + ") is out of scope. " + SYNTAX_REMINDER);
    }

    private boolean isNode(String a) {
        // checking the validity of a node is currently not a work of this method.
        // if true, this method will activate parseNode() anyway --> check there
        return a.matches("\\d+:\\w+:.*") && !this.isEdge(a);
    }

    // test type as gtw3:exclusivegateway:{x} (NON_NUMERIC_BPMN_NODE_ID)
    private boolean isNonNumericId(String a) {
        return a.matches("\\w+:\\w+:.*") && !this.isEdge(a);
    }

    private boolean isEdge(String a) {
        return a.contains("-->");
    }

    private boolean isSubgraph(String a) {
        return a.startsWith("subgraph");
    }

    private boolean isSubgraphEnd(String a) {
        return a.equals("end");
    }

    // except Subgraph
    private Node parseNode(String nodeLine, Deque<String> subs) throws InputValidationException {

        String[] seperated = nodeLine.split(":", 3);
        String id = seperated[0];
        String typ = seperated[1];
        String shape = seperated[2];
        NodeType type = this.parseNodeType(typ);
        RawShape rawShape;
        String label;

        // if type and shape don't match --> throw exception
        if (shape.matches("\\(\\(\\([^(){}]*\\)\\)\\)")) {

            rawShape = RawShape.ENDEVENT;
            if (!type.equals(NodeType.ENDEVENT)) {
                throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "Type and shape at this line: '" + nodeLine + "' do not match. " + SYNTAX_REMINDER);
            }
            label = shape.substring(3, shape.length() - 3);

        } else if (shape.matches("\\(\\([^(){}]*\\)\\)")) {
            rawShape = RawShape.STARTEVENT;
            if (!type.equals(NodeType.STARTEVENT)) {
                throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "Type and shape at this line: '" + nodeLine + "' do not match. " + SYNTAX_REMINDER);
            }
            label = shape.substring(2, shape.length() - 2);

        } else if (shape.matches("\\([^(){}]*\\)")) {

            rawShape = RawShape.TASKORSUBPROCESS;
            if (!(type.equals(NodeType.TASK) || type.equals(NodeType.SUBPROCESS))) {
                throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "Type and shape at this line: '" + nodeLine + "' do not match. " + SYNTAX_REMINDER);
            }
            label = shape.substring(1, shape.length() - 1);

        } else if (shape.matches("\\{[^{}()]*\\}")) {
            rawShape = RawShape.GATEWAY;
            if (!(type.equals(NodeType.EXCLUSIVEGATEWAY) || type.equals(NodeType.INCLUSIVEGATEWAY) || type.equals(NodeType.PARALLELGATEWAY))) {
                throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "Type and shape at this line: '" + nodeLine + "' do not match. " + SYNTAX_REMINDER);
            }
            label = shape.substring(1, shape.length() - 1);

        } else {
            throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "Maybe one/several of a shapes of nodes is/are out of scope in this checker (acceptable: '(...)', '((...))', '(((...)))', '{}') or the label(s) is(are) not encased. " + SYNTAX_REMINDER);
        }

        String key = id + ":" + type.name().toLowerCase();



        updateNode(subs, key, id, nodeLine, type, label, rawShape);

        return this.nodes.get(key);
    }


    private NodeType parseNodeType(String t) throws InputValidationException {
        return switch (t) {
            case "startevent" -> NodeType.STARTEVENT;
            case "endevent" -> NodeType.ENDEVENT;
            case "task" -> NodeType.TASK;
            case "exclusivegateway" -> NodeType.EXCLUSIVEGATEWAY;
            case "inclusivegateway" -> NodeType.INCLUSIVEGATEWAY;
            case "parallelgateway" -> NodeType.PARALLELGATEWAY;
            case "subprocess" -> NodeType.SUBPROCESS;
            default -> throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "This type appear unexpected. " + SYNTAX_REMINDER);
        };
    }

    private void updateNode(Deque<String> subs, String key, String id, String fullname, NodeType type, String label, RawShape rawShape) {
        String location;
        Node node;

        if (this.nodes.containsKey(key)) {
            node = nodes.get(key);
            // label will be updated anyway
            node.setLabel(label);

            String currentLocation = node.getLocation();

            if (!subs.isEmpty() && (currentLocation == null || subs.contains(currentLocation))) {
                // subs has sub-graphs, before no location for this node or current(before) location is parent-subgraph of the location now
                node.setLocation(subs.peek());
            }

        } else {
            location = subs.isEmpty() ? null : subs.peek();
            node = new Node(id, fullname, type, label, rawShape, location);
            nodes.put(key, node);
        }
    }


    public LinkedHashMap<String, Node> getNodes() {
        return nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }
}
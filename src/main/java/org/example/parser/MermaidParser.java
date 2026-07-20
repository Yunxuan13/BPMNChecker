package org.example.parser;

import org.example.graph.MainProcess;
import org.example.graph.ProcessGraph;
import org.example.graph.Subprocess;
import org.example.model.Edge;
import org.example.model.Node;
import org.example.model.NodeType;
import org.example.model.RawShape;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class MermaidParser {

    private LinkedHashMap<String, Node> nodes;
    private List<Edge> edges;

    private MainProcess mainProcess;

    private List<Subprocess> subprocesses;
    private List<ProcessGraph> processes;

    private static final String SYNTAX_REMINDER = "The file was not structurally analyzed.";

    //
    private int currentInnerSubId = 0;


    public MermaidParser(String mermaidPath) throws IOException, InputValidationException {
        this.nodes = new LinkedHashMap<>();

        this.edges = new ArrayList<>();
        this.subprocesses = new ArrayList<>();
        this.processes = new ArrayList<>();
        this.parse(mermaidPath);
    }

    private void parse(String mermaidPath) throws InputValidationException, IOException {

        try {
            List<String> lines = Files.readAllLines(Path.of(mermaidPath));

            // 应该先检查身处何处，再加入新的subprocess

            // can contain same value
            Deque<String> subs = new ArrayDeque<>();

            // for situation like /Users/xuan/Documents/thesis/llm-generated-mermaid-models/gpt-4o/mad150/project_management_process_3.txt
            // register all subgraph "id", if not in this situation:
            // like 1 --> 2 parse error/throw exception
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

            for (String rawLine : lines) {
                String line = rawLine.strip();

                // no need to deal with graph TD/flowchart or something similar
                // ignore empty lines and comment lines
                // ignore direction
                if (line.isEmpty() || line.equals("graph LR") || line.equals("graph TD") || line.equals("flowchart LR") || line.startsWith("%%") || line.equals("direction TD") || line.equals("direction LR")) {
                    continue;

                } else if (this.isSubgraph(line)) {

                    // e.g. subgraph subId [subgraph-label]
                    // (with space instead of ":")
                    // ！！not only this type
                    // but subgraph apple / subgraph apple banana / subgraph apple [banana orange] / subgraph apple[banana]

                    // subgraph should also be considered as a node for using
                    // will throw exception if invalid
                    String subId = this.getSubgraphId(line);

                    // in mermaid, if a sentence begins with a "subgraph", the shape must be [], otherwise there will be syntax-error
                    // if subId already be register, then the new label will not be updated to the node
                    // DONE 处理如果inline subprocess和expaned subprocess撞id的问题 maybe other name in second part like key = "<id>:subgraph"
                    String key = subId + ":subgraph";
                    String subLabel;

                    // according to mermaid.live (tested on 7.15)
                    // node will be displayed with the name that appear afterward while subgraph remains the name that appear for the first time
                    if (nodes.containsKey(key)) {
                        subLabel = nodes.get(key).getLabel();
                    } else {
                        subLabel = this.getSubgraphLabel(line);
                    }

                    // this is actually already checked at getSubgraphId()
//                if (subId.isEmpty()) {
//                    throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "Subgraph line '" + line + "' does not have an id. This file was not structurally analyzed.");
//                }

                    //public Node(String id, String fullName, NodeType type, String label, String rawShape, String location)
                    NodeType type = NodeType.SUBGRAPH;
                    // String key = subId + ":subprocess";
                    RawShape rawShape = RawShape.SUBGRAPH;
                    // String location;

                    this.updateNode(subs, key, subId, line, type, subLabel, rawShape);

                    // this.nodes.get(key).setExpandedSubprocess(true);

                    // TODO subs should work for subgraph--End block
                    //  Due to the same-id problem, this cant work for naming location
                    subs.push(subId);
                    currentInnerSubId++;

                } else if (this.isSubgraphEnd(line)) {

                    if (subs.isEmpty()) {
                        throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "Unmatched 'end': no open subgraph block to close. " + SYNTAX_REMINDER);
                    }
                    // end and only end
                    // pop current subprocess
                    subs.pop();


                } else if (this.isEdge(line)) {

                    // e.g. form like id1:type1:shape --> id:type:shape
                    // & id:type:shape -->|condition-label| id:type:shape
                    // cut at -->
                    String[] seperated = line.split("-->");

                    // node1 --> node2 --> node3 ..... out of scope of our checker
                    if (seperated.length != 2) {
                        throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "Line '" + line + "' may have multiple endpoints." + SYNTAX_REMINDER);
                    }


                    String source = seperated[0].strip();
                    // which can contains condition part
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
                        condition = right.substring(1, pos);

                        target = right.substring(pos + 1).strip();
                    } else {
                        target = right;
                    }

                    // generate Nodes
                    // version 1: we simply consider, that there will only be either nodes or subprocess-id
                    // other situation (e.g. include space or some illegal situation) can temporarily be ignored
                    // situation of subprocess-name will not be considered here

                    sourceKey = this.resolveEndpoint(source, subs, subgraphIds, line);
                    targetKey = this.resolveEndpoint(target, subs, subgraphIds, line);

                    // generate edge
                    // public Edge(String sourceKey, String condition, String targetKey)
                    Edge edge = new Edge(sourceKey, condition, targetKey);
                    // we do not eliminate duplication here --> BPMNChecker
                    this.edges.add(edge);

                } else if (this.isNode(line)) {

                    // Node
                    // id:type:shape
                    this.parseNode(line, subs);

                } else if (this.isNonNumericId(line)) {
                    //
                    throw new InputValidationException(Reason.NON_NUMERIC_BPMN_NODE_ID, "The ID of a node in non-numeric ("+ line + ") is out of scope. " + SYNTAX_REMINDER);
                } else {
                    // throw exceptions for invalid lines
                    throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "Unrecognized line '" + line + "' is neither a node declaration, an edge nor a subgraph construct. " + SYNTAX_REMINDER);
                }
            }

            if (!subs.isEmpty()) {
                throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "At least one subgraph block is not closed. " + SYNTAX_REMINDER);
            }

        } catch (IOException e) {
            throw new IOException("There exists errors while reading the lines of the file of this path '" + mermaidPath + "', " + e.getMessage());
        }



    }

    private String getSubgraphId(String line) throws InputValidationException {
        String info = line.substring(8).strip();

        // TODO after test on mermaid.live, if a label contains "[], (), {}, single ", @, |"
        //  It will fail to parse in mermaid.live.
        //  But it is difficult to consider all possible situation
        //  Current decision: since it seems like, there are no file has those problems, we can try to set them free
        //  --> do not catch any character

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

    // single node?
    private boolean isNode(String a) {
        // checking the validity of a node is currently not a work of this method.
        // if true, this method will activate parseNode() anyway --> check there
        return a.matches("\\d+:\\w+:.*") && !this.isEdge(a);
    }

    // test type as gtw3:exclusivegateway:{x} (NON_NUMERIC_BPMN_NODE_ID)
    private boolean isNonNumericId(String a) {
        return a.matches("\\w+:\\w+:.*") && !this.isEdge(a);
    }


    // edge?
    private boolean isEdge(String a) {
        // same as isNode(), this method is not responsible for checking the validity of an edge
        return a.contains("-->");
    }

    // TODO no start with does not mean it is 100% a valid subgraph
    // this is actually "should be parsed as a possible subgraph"
    // i am not trying to add anything complex here, whether there is something violated, decided by further checking
    private boolean isSubgraph(String a) {
        return a.startsWith("subgraph");
    }

    private boolean isSubgraphEnd(String a) {
        return a.equals("end");
    }

    // except Subgraph
    private Node parseNode(String nodeLine, Deque<String> subs) throws InputValidationException {

        // id:type:shape
        String[] seperated = nodeLine.split(":", 3);
        String id = seperated[0];
        String typ = seperated[1];
        String shape = seperated[2];
        NodeType type = this.parseNodeType(typ);
        RawShape rawShape;
        String label;

        // if type and shape dont match --> throw exception
        if (shape.startsWith("(((") && shape.endsWith(")))")) {
            rawShape = RawShape.ENDEVENT;
            if (!type.equals(NodeType.ENDEVENT)) {
                throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "Type and shape at this line: '" + nodeLine + "' do not match. " + SYNTAX_REMINDER);
            }
            label = shape.substring(3, shape.length() - 3);
        } else if (shape.startsWith("((") && shape.endsWith("))")) {
            rawShape = RawShape.STARTEVENT;
            if (!type.equals(NodeType.STARTEVENT)) {
                throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "Type and shape at this line: '" + nodeLine + "' do not match. " + SYNTAX_REMINDER);
            }
            label = shape.substring(2, shape.length() - 2);
        } else if (shape.startsWith("(") && shape.endsWith(")")) {
            rawShape = RawShape.TASKORSUBPROCESS;
            if (!(type.equals(NodeType.TASK) || type.equals(NodeType.SUBPROCESS))) {
                throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "Type and shape at this line: '" + nodeLine + "' do not match. " + SYNTAX_REMINDER);
            }
            label = shape.substring(1, shape.length() - 1);
        } else if (shape.startsWith("{") && shape.endsWith("}")) {
            rawShape = RawShape.GATEWAY;
            if (!(type.equals(NodeType.EXCLUSIVEGATEWAY) || type.equals(NodeType.INCLUSIVEGATEWAY) || type.equals(NodeType.PARALLELGATEWAY))) {
                throw new InputValidationException(Reason.UNRECOGNIZED_SYNTAX, "Type and shape at this line: '" + nodeLine + "' do not match. " + SYNTAX_REMINDER);
            }
            label = shape.substring(1, shape.length() - 1);
        }

        // situation here:
        // 1. complete block but not defined shape like []
        // 2. not a block at shape e.g. id:type:something random here, with space or with xxx but without a shape outside
        else {
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
            // case "subgraph" -> NodeType.SUBGRAPH will not appear here
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
            // TODO: consider the situation, a node already be recorded in a mainprocess, but here in "null"
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

    public void setNodes(LinkedHashMap<String, Node> nodes) {
        this.nodes = nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public void setEdges(List<Edge> edges) {
        this.edges = edges;
    }

    public MainProcess getMainProcess() {
        return mainProcess;
    }

    public void setMainProcess(MainProcess mainProcess) {
        this.mainProcess = mainProcess;
    }

    public List<Subprocess> getSubprocesses() {
        return subprocesses;
    }

    public void setSubprocesses(List<Subprocess> subprocesses) {
        this.subprocesses = subprocesses;
    }

    public List<ProcessGraph> getProcesses() {
        return processes;
    }

    public void setProcesses(List<ProcessGraph> processes) {
        this.processes = processes;
    }
}
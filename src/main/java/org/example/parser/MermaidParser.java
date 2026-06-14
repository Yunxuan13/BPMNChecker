package org.example.parser;

import org.example.graph.MainProcess;
import org.example.graph.ProcessGraph;
import org.example.graph.Subprocess;
import org.example.model.Edge;
import org.example.model.Node;
import org.example.model.NodeType;
import org.example.model.RawShape;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class MermaidParser {

    // nodes need to be updated, arrayList --> linkedHashMap
    private LinkedHashMap<String, Node> nodes;
    private List<Edge> edges;

    private MainProcess mainProcess;
    // include main (or not?)
    private List<Subprocess> subprocesses;
    private List<ProcessGraph> processes;


    public MermaidParser(String mermaidPath) throws Exception {
        this.nodes = new LinkedHashMap<>();
        this.edges = new ArrayList<>();
        this.subprocesses = new ArrayList<>();
        this.processes = new ArrayList<>();
        try {
            this.parse(mermaidPath);
        } catch (Exception e) {
            throw new Exception("Failed to parse Mermaid file '" + mermaidPath + "': " +e.getMessage());
        }

    }

    private void parse(String mermaidPath) throws Exception {

        List<String> lines = Files.readAllLines(Path.of(mermaidPath));

        // TODO：node（包括subprocess）都应该出现在“最内层” 应加上nodeDepth，若同层则不更新location，若更深层则更新最内层的位置
        // 应该先检查身处何处，再加入新的subprocess
        Deque<String> subs = new ArrayDeque<>();

        for (String rawLine : lines) {
            String line = rawLine.strip();

            // no need to deal with graph TD/flowchart or something similar
            // ignore empty lines and comment lines
            // ignore direction
            if (line.isEmpty() || line.startsWith("graph") || line.startsWith("flowchart") || line.startsWith("%%") || line.startsWith("direction")) {
                continue;

            } else if (this.isSubgraph(line)){

                // e.g. subgraph subId [subgraph-label]
                // (with space instead of ":")
                // subgraph should also be considered as a node for using
                String info = line.substring(8).strip();
                String subId = info.substring(0, info.indexOf(" "));

                // in mermaid, if a sentence begins with a "subgraph", the shape must be [], otherwise there will be syntax-error
                String subLabel = info.substring(info.indexOf("[") + 1, info.indexOf("]"));

                //public Node(String id, String fullName, NodeType type, String label, String rawShape, String location)
                NodeType type = NodeType.SUBPROCESS;
                String key = subId + ":subprocess";
                RawShape rawShape = RawShape.SUBPROCESS;
                String location;

                // TODO 如果嵌套自己则throw error

                this.updateNode(subs, key, subId, line, type, subLabel, rawShape);

//                Node node;
//                if (this.nodes.containsKey(key)) {
//                    node = nodes.get(key);
//                    node.setLabel(subLabel);
//                    String currentLocation = node.getLocation();
//
//                        // case already in a subprocess
//                        // do not update location
//                        // update anyway, otherwise we still need to use if ()
//
//
//                    if (currentLocation != null && subs.contains(currentLocation)) {
//                        node.setLocation(subs.peek());
//                    }
//
//
//
////                    else {
////                        // case in main process
////                        // direct update
////
////                    }
//
//
//                } else {
//                    location = subs.isEmpty() ? null : subs.peek();
//                    node = new Node(subId, line, type, subLabel, rawShape, location);
//                    this.nodes.put(key, node);
//                }

                subs.push(subId);

            } else if (this.isSubgraphEnd(line)) {

                if (subs.isEmpty()) {
                    // TODO: Exception sentence
                    throw new Exception("Unmatched 'end'");
                }
                // end and only end
                // pop current subprocess
                subs.pop();


            } else if (this.isEdge(line)) {

                // e.g. id1:type1:shape --> id:type:shape
                // & id:type:shape -->|condition-label| id:type:shape
                // cut at -->
                String[] seperated = line.split("-->");
                String source = seperated[0].strip();
                String right = seperated[1].strip();

                String condition = null;
                String target;

                String targetKey;
                String sourceKey;

                if (right.startsWith("|")) {
                    int pos = right.indexOf("|", 1);
                    condition = right.substring(1, pos);
                    target = right.substring(pos + 1).strip();
                } else {
                    target = right;
                }

                // generate Nodes
                // version 1: we simply consider, that there will only be either nodes or subprocess-id
                // other situation (e.g. include space or some illegal situation) can temporarily be ignored
                // situation of subprocess-name will not be considered here
                if (this.isNode(source)) {
                    Node n = this.parseNode(source, subs);
                    sourceKey = n.getKey();
                } else {
                    // TODO
                    sourceKey = source + ":" + "subprocess";
                    if (!this.nodes.containsKey(sourceKey)) {
                        String l = subs.isEmpty() ? null : subs.peek();
                        Node n = new Node(source, "subgraph " + source +" [" + source +"]", NodeType.SUBPROCESS, source, RawShape.SUBPROCESS, l);
                        this.nodes.put(sourceKey, n);
                    }
                }
                if (this.isNode(target)) {
                    Node n = this.parseNode(target, subs);
                    targetKey = n.getKey();
                } else {
                    targetKey = target + ":" + "subprocess";
                    if (!this.nodes.containsKey(targetKey)) {
                        String l = subs.isEmpty() ? null : subs.peek();
                        Node n = new Node(target, "subgraph " + source +" [" + source +"]", NodeType.SUBPROCESS, source, RawShape.SUBPROCESS, l);
                        this.nodes.put(targetKey, n);
                    }
                }

                // generate edge
                // public Edge(String sourceKey, String condition, String targetKey)
                Edge edge = new Edge(sourceKey, condition, targetKey);
                this.edges.add(edge);

            } else if (this.isNode(line)) {

                // Node
                // id:type:shape
                this.parseNode(line, subs);

            } else {
                // throw exceptions for invalid lines
                // TODO
            }
        }

        // TODO: find all nodes in original file
        //  1. id:type:shape+label
        //  2. (subprocess) subgraph id [subprocess-label]

        // TODO: id:type should be considered at the same time and label can be updated


        // TODO: nodes should be located to an exact process (String processId)
        //  but need to think about how to name main process
        // TODO: subprocess with list/queue/other possibilities

        // TODO: find all edge
        //  this happens after generating nodeList
        //  since we

    }

    // single node?
    private boolean isNode(String a) {
        return a.matches("\\d+:\\w+:.*") && !this.isEdge(a);
    }

    // edge?
    private boolean isEdge(String a) {
        return a.contains("-->");
    }

    private boolean isSubgraph(String a) {
        return a.startsWith("subgraph");
    }

    private boolean isSubgraphEnd(String a) {
        return a.equals("end");
    }

    // except SUBPROCESS
    private Node parseNode(String nodeLine, Deque<String> subs) {

        // id:type:shape
        String[] seperated = nodeLine.split(":", 3);
        String id = seperated[0];
        String t = seperated[1];
        String shape = seperated[2];
        NodeType type = this.parseNodeType(t);
        RawShape rawShape;
        String label;

//        String location;
//        if (subs.isEmpty()) {
//            location = null;
//        } else {
//            location = subs.peek();
//        }

        // can I just make assumptions: we don't make mistakes here
        if (shape.startsWith("(((")) {
            rawShape = RawShape.ENDEVENT;
            label = shape.substring(3, shape.length() - 3);
        } else if (shape.startsWith("((")) {
            rawShape = RawShape.STARTEVENT;
            label = shape.substring(2, shape.length() - 2);
        } else if (shape.startsWith("(")) {
            rawShape = RawShape.TASKORSUB;
            label = shape.substring(1, shape.length() - 1);
        } else if (shape.startsWith("{")) {
            rawShape = RawShape.GATEWAY;
            label = shape.substring(1, shape.length() - 1);
        }
        else {
            rawShape = RawShape.UNKNOWN;
            label = shape;
        }

        String key = id + ":" + type.name().toLowerCase();

        updateNode(subs, key, id, nodeLine, type, label, rawShape);

        return this.nodes.get(key);
    }

//    private RawShape parseRawShape(String shape) {
//        // can I just make assumptions: we don't make mistakes here
//        if (shape.startsWith("(((")) {
//            return RawShape.ENDEVENT;
//            // label = shape.substring(2, shape.length() - 2);
//        } else if (shape.startsWith("((")) {
//            return RawShape.STARTEVENT;
//            // label = shape.substring(1, shape.length() - 1);
//        } else if (shape.startsWith("(")) {
//            return RawShape.TASKORSUB;
//            // label = shape.substring(1, shape.length() - 1);
//        } else if (shape.startsWith("{")) {
//            return RawShape.GATEWAY;
//            // label = shape.substring(1, shape.length() - 1);
//        } else {
//            return RawShape.UNKNOWN;
//            // label = shape;
//        }
//    }

    private NodeType parseNodeType(String t) {
        return switch (t) {
            case "startevent" -> NodeType.STARTEVENT;
            case "endevent" -> NodeType.ENDEVENT;
            case "task" -> NodeType.TASK;
            case "exclusivegateway" -> NodeType.EXCLUSIVEGATEWAY;
            case "inclusivegateway" -> NodeType.INCLUSIVEGATEWAY;
            case "parallelgateway" -> NodeType.PARALLELGATEWAY;
            case "subprocess" -> NodeType.SUBPROCESS;
            default -> NodeType.UNKNOWN;
        };
    }

    private void updateNode(Deque<String> subs, String key, String id, String fullname, NodeType type, String label, RawShape rawShape) {
        String location;
        Node node;
        if (this.nodes.containsKey(key)) {
            node = nodes.get(key);
            node.setLabel(label);
            String currentLocation = node.getLocation();
            // TODO: consider the situation, a node already be recorded in a mainprocess, but here in "null"
            if (!subs.isEmpty() && (currentLocation == null || subs.contains(currentLocation))) {
                node.setLocation(subs.peek());
            }
        } else {
            location = subs.isEmpty() ? null : subs.peek();
            node = new Node(id, fullname, type, label, rawShape, location);
            nodes.put(key, node);
        }
    }

//    private Node addNode(String nodeLine, Deque<String> subs) {
//        Node n = this.parseNode(nodeLine, subs);
//        nodes.removeIf(node -> node.getKey().equals(n.getKey()));
//        nodes.add(n);
//        return n;
//    }

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
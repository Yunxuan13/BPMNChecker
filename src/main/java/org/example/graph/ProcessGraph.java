package org.example.graph;

import org.example.model.Edge;
import org.example.model.Node;
import org.example.model.NodeType;
import org.example.model.Role;

import java.util.*;

public class ProcessGraph {

    private final LinkedHashMap<String, Node> nodes;
    private final List<Edge> edges;

    private LinkedHashMap<String, List<Node>> scopeNodes;
    private LinkedHashMap<String, Set<Edge>> scopeBackEdges;

    // loop-free incoming and outgoing
    private LinkedHashMap<Node, List<Edge>> loopFreeIn;
    private LinkedHashMap<Node, List<Edge>> loopFreeOut;

    public ProcessGraph(LinkedHashMap<String, Node> nodes, List<Edge> edges) {
        this.nodes = nodes;
        this.edges = edges;

        this.scopeNodes = new LinkedHashMap<>();
        this.scopeBackEdges = new LinkedHashMap<>();
        this.loopFreeIn = new LinkedHashMap<>();
        this.loopFreeOut = new LinkedHashMap<>();

        this.setGraphs();
        this.buildScopeNodes();
        this.buildScopeBackEdges();
        this.buildLoopFreeEdges();

    }

    public Set<Node> reachableInScope(List<Node> partNodes) {
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

    public List<Node> getArrival(List<Node> scopeNodes, List<Edge> relatedEdges, Node start) {
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

    public boolean isSplit(Node node) {
        return node.getOutgoingEdges().size() > 1;
    }

    public boolean isSplit(Node node, Set<Edge> loopEdges) {
        int count = 0;
        for (Edge edge : node.getOutgoingEdges()) {
            if (!loopEdges.contains(edge)) {
                count++;
            }
        }

        return count > 1;
    }

    public boolean isMerge(Node node) {
        return node.getIncomingEdges().size() > 1;
    }

    // consider the situation of graph with loop
    // for gtw0304
    public boolean isMerge(Node node, Set<Edge> loopEdges) {
        int count = 0;
        for (Edge edge : node.getIncomingEdges()) {
            if (!loopEdges.contains(edge)) {
                count++;
            }
        }

        return count > 1;
    }

    private void setGraphs() {
        // set incoming and outgoing
        for (Node node : nodes.values()) {

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
    }

    private void buildScopeNodes() {
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
    }

    private void buildScopeBackEdges() {
        // scope back edges
        this.scopeBackEdges = new LinkedHashMap<>();

        for (String scope : this.scopeNodes.keySet()) {
            // all nodes in one scope
            List<Node> nodeList = this.scopeNodes.get(scope);
            Set<Edge> back = this.getLoopEdges(nodeList);

            this.scopeBackEdges.put(scope, back);

        }
    }

    private void buildLoopFreeEdges() {
        for (Node node : nodes.values()) {
            String scope = this.getScope(node);

            List<Edge> in = new ArrayList<>(node.getIncomingEdges());
            List<Edge> out = new ArrayList<>(node.getOutgoingEdges());

            in.removeIf(i -> this.scopeBackEdges.get(scope).contains(i));
            out.removeIf(o -> this.scopeBackEdges.get(scope).contains(o));

            loopFreeIn.put(node, in);
            loopFreeOut.put(node, out);
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


    public String getScope(Node node) {
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


    public LinkedHashMap<String, Node> getNodes() {
        return nodes;
    }

    public List<Edge> getEdges() {
        return edges;
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

    public LinkedHashMap<Node, List<Edge>> getLoopFreeOut() {
        return loopFreeOut;
    }

    public void setLoopFreeOut(LinkedHashMap<Node, List<Edge>> loopFreeOut) {
        this.loopFreeOut = loopFreeOut;
    }

    public LinkedHashMap<Node, List<Edge>> getLoopFreeIn() {
        return loopFreeIn;
    }

    public void setLoopFreeIn(LinkedHashMap<Node, List<Edge>> loopFreeIn) {
        this.loopFreeIn = loopFreeIn;
    }
}

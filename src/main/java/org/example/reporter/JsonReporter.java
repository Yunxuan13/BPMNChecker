package org.example.reporter;

import org.example.Main;
import org.example.model.*;
import org.example.parser.MermaidParser;
import org.example.repair.SuggestionBuilder;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class JsonReporter {

    private JsonMeta meta = new JsonMeta();

    List<JsonIssue> issues = new ArrayList<>();

    public JsonReporter(String path, MermaidParser parser, List<BPMNError> errorList) {

        this.meta.setFile(path);
        this.meta.setTimestamp(ZonedDateTime.now().toString());
        this.meta.setNodeCount(parser.getNodes().size());
        this.meta.setEdgeCount(parser.getEdges().size());

        int errorCount = 0;
        int warningCount = 0;
        // int infoCount = 0;

        for (BPMNError error : errorList) {
            if (error.getSeverity() == Severity.ERROR) {
                errorCount++;
            } else {
                warningCount++;
            }

            JsonIssue issue = new JsonIssue();
            issue.setErrorId(error.getErrorId());
            issue.setErrorName(error.getErrorName());
            issue.setCategory(error.getErrorCategory());
            issue.setSeverity(error.getSeverity().name().toLowerCase());
            issue.setScope(convertScope(error.getScope()));

            issue.setMessage(error.getMessage());

            issue.setSuggestion(SuggestionBuilder.suggest(error));

            List<JsonNode> involvedNodes = getJsonNodes(error);
            issue.setInvolvedNodes(involvedNodes);


            List<JsonEdge> errorEdges = getJsonEdges(error);

            issue.setInvolvedEdges(errorEdges);

            this.issues.add(issue);
        }
        this.meta.setErrorCount(errorCount);
        this.meta.setWarningCount(warningCount);
        // this.meta.setInfoCount(infoCount);

        this.meta.setTotalIssues(this.issues.size());
    }

    private static List<JsonEdge> getJsonEdges(BPMNError error) {
        List<JsonEdge> errorEdges = new ArrayList<>();
        for (Edge edge : error.getEdges()) {
            JsonEdge e = new JsonEdge();
            e.setSource(edge.getSourceKey());
            e.setTarget(edge.getTargetKey());
            e.setCondition(edge.getCondition());
            errorEdges.add(e);
        }
        return errorEdges;
    }

    private static List<JsonNode> getJsonNodes(BPMNError error) {
        List<JsonNode> errorNodes = new ArrayList<>();
        for (Node node : error.getNode()) {
            JsonNode n = new JsonNode();
            n.setKey(node.getKey());
            n.setLabel(node.getLabel());
            n.setType(node.getType().name().toLowerCase());
            n.setSubprocess(node.getLocation());
            List<String> roles = new ArrayList<>();
            for (Role role : node.getRoles()) {
                roles.add(role.name().toLowerCase());
            }
            n.setRoles(roles);
            errorNodes.add(n);
        }
        return errorNodes;
    }

    private static String convertScope(String scope) {
        if (scope == null || scope.equals("Main:[]")) {
            return "main";
        }
        if (scope.startsWith("Subprocess:[") && scope.endsWith("]")) {
            return "subprocess:" + scope.substring("Subprocess:[".length(), scope.length() - 1);
        }
        return scope;
    }

    public JsonMeta getMeta() {
        return meta;
    }

    public void setMeta(JsonMeta meta) {
        this.meta = meta;
    }

    public List<JsonIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<JsonIssue> issues) {
        this.issues = issues;
    }
}

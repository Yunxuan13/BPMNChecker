package org.example.reporter;

import org.example.Main;
import org.example.model.*;
import org.example.parser.MermaidParser;

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
        int infoCount = 0;

        for (BPMNError error : errorList) {
            if (error.getSeverity() == Severity.ERROR) {
                errorCount++;
            } else if (error.getSeverity() == Severity.WARNING){
                warningCount++;
            } else {
                // for new LBL02, if not X/O/AND
                // according to prompt
                infoCount++;
            }

            JsonIssue issue = new JsonIssue();
            issue.setErrorId(error.getErrorId());
            issue.setErrorName(error.getErrorName());
            issue.setCategory(error.getErrorCategory());
            issue.setSeverity(error.getSeverity().name());
            issue.setScope(convertScope(error.getScope()));
            // TODO we havent add any messages for each error
            issue.setMessage(error.getMessage());
            // TODO add attribute suggestion
            issue.setSuggestion(null);

            List<JsonNode> errorNodes = new ArrayList<>();
            for (Node node : error.getNode()) {
                JsonNode n = new JsonNode();
                n.setKey(node.getKey());
                n.setLabel(node.getLabel());
                n.setType(node.getType().name().toLowerCase());
                n.setLocation(node.getLocation());
                List<String> roles = new ArrayList<>();
                for (Role role : node.getRoles()) {
                    roles.add(role.name().toLowerCase());
                }
                n.setRoles(roles);
                errorNodes.add(n);
            }
            issue.setErrorNodes(errorNodes);


            List<JsonEdge> errorEdges = new ArrayList<>();
            for (Edge edge : error.getEdges()) {
                JsonEdge e = new JsonEdge();
                e.setSourceKey(edge.getSourceKey());
                e.setTargetKey(edge.getTargetKey());
                e.setCondition(edge.getCondition());
                errorEdges.add(e);
            }
            issue.setErrorEdges(errorEdges);

            this.issues.add(issue);
        }
        this.meta.setErrorCount(errorCount);
        this.meta.setWarningCount(warningCount);
        this.meta.setInfoCount(infoCount);

        this.meta.setTotalIssues(this.issues.size());
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

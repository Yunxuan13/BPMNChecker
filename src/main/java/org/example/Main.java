package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.example.checker.BPMNChecker;
import org.example.model.*;
import org.example.parser.MermaidParser;

import java.security.spec.ECField;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {

        if (args.length < 1) {
            System.out.println("We need to receive a path to Mermaid-File.");
            return;
        }

        String path = args[0];

        MermaidParser parser;
        try {
            parser = new MermaidParser(path);

        } catch (Exception e) {
            System.err.println("Failed to parse file: " + e.getMessage());
            System.exit(1);
            return;
        }

        BPMNChecker checker = new BPMNChecker(parser);
        checker.detectErrors();
        List<BPMNError> errorList = checker.getErrorList();

        JsonReport report = generateReport(path, parser, errorList);
        // we need Json format
        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create();
        System.out.println(gson.toJson(report));
    }


    private static JsonReport generateReport(String path, MermaidParser parser, List<BPMNError> errorList) {
        JsonReport jsonReport = new JsonReport();

        jsonReport.meta.file = path;
        jsonReport.meta.timestamp = ZonedDateTime.now().toString();
        jsonReport.meta.nodeCount = parser.getNodes().size();
        jsonReport.meta.edgeCount = parser.getEdges().size();


        for (BPMNError error : errorList) {
            if (error.getSeverity() == Severity.ERROR) {
                jsonReport.meta.errorCount++;
            } else if (error.getSeverity() == Severity.WARNING){
                jsonReport.meta.warningCount++;
            } else {
                // for new LBL02, if not X/O/AND
                // according to prompt
                jsonReport.meta.infoCount++;
            }

            JsonIssue issue = new JsonIssue();
            issue.errorId = error.getErrorId();
            issue.errorName = error.getErrorName();
            issue.category = error.getErrorCategory();
            issue.severity = error.getSeverity().name();
            issue.scope = convertScope(error.getScope());
            // TODO we havent add any messages for each error
            issue.message = error.getMessage();
            // TODO add attribute suggestion
            issue.suggestion = null;

            for (Node node : error.getNode()) {
                JsonNode n = new JsonNode();
                n.key = node.getKey();
                n.label = node.getLabel();
                n.type = node.getType().name().toLowerCase();
                n.location = node.getLocation();
                for (Role role : node.getRoles()) {
                    n.roles.add(role.name().toLowerCase());
                }
                issue.errorNodes.add(n);
            }

            for (Edge edge : error.getEdges()) {
                JsonEdge e = new JsonEdge();
                e.sourceKey = edge.getSourceKey();
                e.targetKey = edge.getTargetKey();
                e.condition = edge.getCondition();
                issue.errorEdges.add(e);
            }

            jsonReport.issues.add(issue);
        }

        jsonReport.meta.totalIssues = jsonReport.issues.size();
        return jsonReport;
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

    private static class JsonReport {
        // remove to meta info part
//        int errorCount = 0;
//        int warningCount = 0;
//        int totalIssues = 0;
        JsonMeta meta = new JsonMeta();

        List<JsonIssue> issues = new ArrayList<>();
    }

    private static class JsonMeta {
        String file;
        String timestamp;

        int nodeCount;
        int edgeCount;

        int errorCount = 0;
        int warningCount = 0;
        int infoCount = 0;

        int totalIssues = 0;
    }

    private static class JsonIssue {
        String errorId;
        String errorName;
        String category;
        String severity;
        String scope;
        String message;
        // while repairing, extract suggestion as part of new prompt back to llm
        String suggestion;
        List<JsonNode> errorNodes = new ArrayList<>();
        List<JsonEdge> errorEdges = new ArrayList<>();
    }

    private static class JsonNode {
        String key;
        String label;
        String type;
        String location;
        List<String> roles = new ArrayList<>();
    }

    private static class JsonEdge {
        String sourceKey;
        String targetKey;
        String condition;
    }
}
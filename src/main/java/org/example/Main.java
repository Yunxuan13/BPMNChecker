package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.example.checker.BPMNChecker;
import org.example.model.BPMNError;
import org.example.model.Edge;
import org.example.model.Node;
import org.example.model.Severity;
import org.example.parser.MermaidParser;

import java.security.spec.ECField;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {

        if (args.length < 1) {
            System.out.println("We need to receive a path to Mermaid-File.");
        }

        String path = args[0];

        MermaidParser parser;
        try {
            parser = new MermaidParser(path);

        } catch (Exception e) {
            System.err.println("Failed to parse file: " + e.getMessage());
            return;
        }

        BPMNChecker checker = new BPMNChecker(parser);
        checker.detectErrors();
        List<BPMNError> errorList = checker.getErrorList();

        JsonReport report = generateReport(errorList);
        // we need Json format
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        System.out.println(gson.toJson(report));
    }

    private static JsonReport generateReport(List<BPMNError> errorList) {
        JsonReport jsonReport = new JsonReport();

        for (BPMNError error : errorList) {
            if (error.getSeverity() == Severity.ERROR) {
                jsonReport.errorCount++;
            } else {
                jsonReport.warningCount++;
            }

            JsonIssue issue = new JsonIssue();
            issue.errorId = error.getErrorId();
            issue.errorName = error.getErrorName();
            issue.category = error.getErrorCategory();
            issue.severity = error.getSeverity().name();
            issue.scope = error.getScope();
            // TODO we havent add any messages for each error
            issue.message = error.getMessage();

            for (Node node : error.getNode()) {
                JsonNode n = new JsonNode();
                n.key = node.getKey();
                n.label = node.getLabel();
                n.type = node.getType().name();
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


        return jsonReport;
    }

    private static class JsonReport {
        int errorCount = 0;
        int warningCount = 0;
        List<JsonIssue> issues = new ArrayList<>();
    }

    private static class JsonIssue {
        String errorId;
        String errorName;
        String category;
        String severity;
        String scope;
        String message;
        List<JsonNode> errorNodes = new ArrayList<>();
        List<JsonEdge> errorEdges = new ArrayList<>();
    }

    private static class JsonNode {
        String key;
        String label;
        String type;
    }

    private static class JsonEdge {
        String sourceKey;
        String targetKey;
        String condition;
    }
}
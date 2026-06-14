package org.example.reporter;

import org.example.Main;

import java.util.ArrayList;
import java.util.List;

public class JsonIssue {

    private String errorId;
    private String errorName;
    private String category;
    private String severity;
    private String scope;
    private String message;
    // while repairing, extract suggestion as part of new prompt back to llm
    private String suggestion;
    private List<JsonNode> involvedNodes = new ArrayList<>();
    private List<JsonEdge> involvedEdges = new ArrayList<>();

//    public JsonIssue(String errorId, String errorName, String category, String severity, String scope, String message, String suggestion, List<JsonNode> errorNodes, List<JsonEdge> errorEdges) {
//        this.errorId = errorId;
//        this.errorName = errorName;
//        this.category = category;
//        this.scope = scope;
//        this.severity = severity;
//        this.message = message;
//        this.suggestion = suggestion;
//        this.errorNodes = new ArrayList<>();
//        this.errorEdges = new ArrayList<>();
//    }

    public String getErrorId() {
        return errorId;
    }

    public String getErrorName() {
        return errorName;
    }

    public String getCategory() {
        return category;
    }

    public String getSeverity() {
        return severity;
    }

    public String getScope() {
        return scope;
    }

    public String getMessage() {
        return message;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public List<JsonNode> getErrorNodes() {
        return involvedNodes;
    }

    public List<JsonEdge> getErrorEdges() {
        return involvedEdges;
    }

    public void setInvolvedNodes(List<JsonNode> errorNodes) {
        this.involvedNodes = errorNodes;
    }

    public void setInvolvedEdges(List<JsonEdge> errorEdges) {
        this.involvedEdges = errorEdges;
    }

    public void setErrorId(String errorId) {
        this.errorId = errorId;
    }

    public void setErrorName(String errorName) {
        this.errorName = errorName;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }
}


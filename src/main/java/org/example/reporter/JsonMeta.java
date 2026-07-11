package org.example.reporter;

public class JsonMeta {

    private String file;
    private String timestamp;

    private int nodeCount;
    private int edgeCount;

    private int errorCount;
    private int warningCount;
    // private int infoCount;

    private int totalIssues;



    public String getFile() {
        return file;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public int getEdgeCount() {
        return edgeCount;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public int getTotalIssues() {
        return totalIssues;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public void setWarningCount(int warningCount) {
        this.warningCount = warningCount;
    }

    public void setTotalIssues(int totalIssues) {
        this.totalIssues = totalIssues;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public void setEdgeCount(int edgeCount) {
        this.edgeCount = edgeCount;
    }

    public void setNodeCount(int nodeCount) {
        this.nodeCount = nodeCount;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}

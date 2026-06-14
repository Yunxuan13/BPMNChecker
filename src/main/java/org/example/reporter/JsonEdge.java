package org.example.reporter;

public class JsonEdge {

    private String sourceKey;
    private String targetKey;
    private String condition;

//    public JsonEdge(String sourceKey, String targetKey, String condition) {
//        this.sourceKey = sourceKey;
//        this.targetKey = targetKey;
//        this.condition = condition;
//    }

    public String getSourceKey() {
        return sourceKey;
    }

    public String getTargetKey() {
        return targetKey;
    }

    public String getCondition() {
        return condition;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceKey = sourceKey;
    }

    public void setTargetKey(String targetKey) {
        this.targetKey = targetKey;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }
}

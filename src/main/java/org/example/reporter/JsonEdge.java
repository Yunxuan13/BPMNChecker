package org.example.reporter;

public class JsonEdge {

    private String source;
    private String target;
    private String condition;



    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    public String getCondition() {
        return condition;
    }

    public void setSource(String sourceKey) {
        this.source = source;
    }

    public void setTarget(String targetKey) {
        this.target = target;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }
}

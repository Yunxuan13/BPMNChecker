package org.example.parser;

public class InputValidationException extends Exception {

    private final Reason reason;

    public InputValidationException(Reason reason, String content) {
        super(content);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}

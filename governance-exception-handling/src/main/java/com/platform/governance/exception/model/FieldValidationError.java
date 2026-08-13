package com.platform.governance.exception.model;

public final class FieldValidationError {

    private final String field;
    private final String message;

    public FieldValidationError(String field, String message) {
        this.field = field;
        this.message = message;
    }

    public String getField() { return field; }
    public String getMessage() { return message; }
}

package com.acme.payment.dto;

import java.util.Arrays;

public enum AccountType {
    SAVING("saving"),
    CURRENT("current");

    private final String type;

    // Enum constructor (implicitly private)
    AccountType(String type) {
        this.type = type;
    }

    /**
     * Getter for the underlying string value.
     */
    public String getType() {
        return type;
    }

    /**
     * Reverse lookup: Resolve enum constant by string value ("saving" -> SAVING).
     */
    public static AccountType fromType(String type) {
        if (type == null) {
            return null;
        }
        return Arrays.stream(AccountType.values())
                .filter(acc -> acc.type.equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown AccountType: " + type));
    }

    @Override
    public String toString() {
        return type;
    }
}

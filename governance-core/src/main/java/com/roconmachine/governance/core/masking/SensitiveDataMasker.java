package com.roconmachine.governance.core.masking;

import com.roconmachine.governance.core.annotation.Sensitive;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Produces a masked, log-safe representation of any object graph, honouring
 * {@link Sensitive} field annotations - plus a direct string-masking method
 * for values that don't come from an annotated field at all (HTTP headers
 * like Authorization/Cookie, query params, etc.). Every governance module
 * (audit, http-logging, ...) shares this single instance and policy so a
 * card PAN is masked identically wherever it's logged.
 */
public class SensitiveDataMasker {

    private static final String MASK = "****";
    private final Map<Class<?>, Field[]> sensitiveFieldCache = new ConcurrentHashMap<>();

    /** Masks an object graph, redacting any field annotated {@link Sensitive}. */
    public String mask(Object target) {
        if (target == null) {
            return "null";
        }
        if (isSimpleValue(target)) {
            return String.valueOf(target);
        }

        Class<?> clazz = target.getClass();
        Field[] sensitiveFields = sensitiveFieldCache.computeIfAbsent(clazz, this::findSensitiveFields);

        if (sensitiveFields.length == 0) {
            return String.valueOf(target);
        }

        StringBuilder sb = new StringBuilder(clazz.getSimpleName()).append("{");
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            sb.append(field.getName()).append("=");
            try {
                Object value = field.get(target);
                boolean isSensitive = field.isAnnotationPresent(Sensitive.class);
                sb.append(isSensitive ? maskValue(value, field.getAnnotation(Sensitive.class).strategy()) : value);
            } catch (IllegalAccessException e) {
                sb.append("<unreadable>");
            }
            sb.append(", ");
        }
        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);
        }
        return sb.append("}").toString();
    }

    /**
     * Masks a raw value directly - for callers that don't have an annotated
     * field to inspect, e.g. an HTTP header value keyed by name against a
     * configured list of sensitive header names.
     */
    public String maskValue(Object value, Sensitive.MaskStrategy strategy) {
        if (value == null) {
            return "null";
        }
        String str = String.valueOf(value);
        return switch (strategy) {
            case FULL -> MASK;
            case PARTIAL -> partialMask(str);
            case HASH -> hash(str);
        };
    }

    private Field[] findSensitiveFields(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(Sensitive.class))
                .toArray(Field[]::new);
    }

    private boolean isSimpleValue(Object target) {
        return target instanceof CharSequence
                || target instanceof Number
                || target instanceof Boolean
                || target.getClass().isPrimitive();
    }

    private String partialMask(String value) {
        if (value.length() <= 4) {
            return MASK;
        }
        return MASK + value.substring(value.length() - 4);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes());
            return "sha256:" + Base64.getEncoder().encodeToString(hashed).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            return MASK;
        }
    }
}

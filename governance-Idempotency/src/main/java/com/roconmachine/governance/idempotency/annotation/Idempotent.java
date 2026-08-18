package com.roconmachine.governance.idempotency.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    boolean validateFingerprint() default true;
}
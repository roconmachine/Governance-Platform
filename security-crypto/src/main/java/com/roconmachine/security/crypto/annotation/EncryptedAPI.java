package com.roconmachine.security.crypto.annotation;

import java.lang.annotation.*;

/**
 * Marks a REST endpoint (method or, applied at class level, every method in
 * that controller) for whole-payload encryption: the RESPONSE body is
 * always encrypted into a single JSON field (see
 * {@code security.crypto.encrypted-api.payload-field-name}), and the
 * REQUEST body is decrypted from that same JSON shape whenever the caller
 * sends the configured header (default {@code X-Encrypted: true}) - see
 * {@code security.crypto.encrypted-api.header-name}.
 *
 * This is a DIFFERENT concern from {@link Encrypted} (field-level JPA
 * encryption at rest): this annotation encrypts the entire HTTP payload in
 * transit, for endpoints that need payload-level confidentiality beyond
 * TLS - e.g. a compliance requirement that application-layer payloads
 * remain encrypted even if TLS is terminated somewhere upstream you don't
 * fully trust.
 *
 * <pre>{@code
 * @RestController
 * public class PaymentController {
 *
 *     @EncryptedAPI
 *     @PostMapping("/transfer")
 *     public TransferResult transfer(@RequestBody TransferRequest request) {
 *         // `request` here is the DECRYPTED object - @EncryptedAPI's request-side
 *         // decryption already ran before Spring bound this argument.
 *         // The response (TransferResult) is automatically encrypted before
 *         // being sent - no code here needs to know that's happening.
 *         ...
 *     }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface EncryptedAPI {
}

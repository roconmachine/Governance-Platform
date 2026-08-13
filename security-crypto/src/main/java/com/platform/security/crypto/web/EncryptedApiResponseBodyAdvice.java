package com.platform.security.crypto.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.security.crypto.annotation.EncryptedAPI;
import com.platform.security.crypto.config.SecurityCryptoProperties;
import com.platform.security.crypto.engine.EncryptionException;
import com.platform.security.crypto.engine.EncryptionService;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Map;

/**
 * Encrypts the response body for any method (or class) carrying
 * {@code @EncryptedAPI}, using Spring MVC's standard {@link ResponseBodyAdvice}
 * extension point - the supported hook for exactly this kind of "transform
 * the body right before it's converted to JSON" need, rather than something
 * more invasive like a raw response-wrapping Filter.
 *
 * {@code @ControllerAdvice} (not {@code @RestControllerAdvice}, and no
 * {@code @ExceptionHandler} methods) is required here specifically because
 * Spring's {@code RequestMappingHandlerAdapter} only discovers
 * {@code ResponseBodyAdvice} beans that are ALSO annotated
 * {@code @ControllerAdvice} - implementing the interface alone is not
 * sufficient for Spring to find and invoke it.
 */
@ControllerAdvice
public class EncryptedApiResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final EncryptionService encryptionService;
    private final SecurityCryptoProperties properties;
    private final ObjectMapper objectMapper;

    public EncryptedApiResponseBodyAdvice(EncryptionService encryptionService,
                                           SecurityCryptoProperties properties,
                                           ObjectMapper objectMapper) {
        this.encryptionService = encryptionService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.hasMethodAnnotation(EncryptedAPI.class)
                || returnType.getContainingClass().isAnnotationPresent(EncryptedAPI.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                   Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                   ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null) {
            // Nothing to encrypt (e.g. a 204 No Content) - leave as-is rather
            // than producing a confusing encrypted-empty-string envelope.
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(body);
            String ciphertext = encryptionService.encrypt(json);

            SecurityCryptoProperties.EncryptedApi config = properties.getEncryptedApi();
            response.getHeaders().set(config.getHeaderName(), config.getHeaderTrueValue());

            return Map.of(config.getPayloadFieldName(), ciphertext);
        } catch (JsonProcessingException e) {
            throw new EncryptionException("Failed to serialize response body for @EncryptedAPI encryption", e);
        }
    }
}

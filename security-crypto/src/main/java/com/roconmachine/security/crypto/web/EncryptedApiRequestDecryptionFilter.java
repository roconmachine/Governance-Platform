package com.roconmachine.security.crypto.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roconmachine.security.crypto.annotation.EncryptedAPI;
import com.roconmachine.security.crypto.config.SecurityCryptoProperties;
import com.roconmachine.security.crypto.engine.EncryptionException;
import com.roconmachine.security.crypto.engine.EncryptionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;


public class EncryptedApiRequestDecryptionFilter extends OncePerRequestFilter {

    private final RequestMappingHandlerMapping handlerMapping;
    private final EncryptionService encryptionService;
    private final SecurityCryptoProperties properties;
    private final ObjectMapper objectMapper;

    public EncryptedApiRequestDecryptionFilter(RequestMappingHandlerMapping handlerMapping,
                                                EncryptionService encryptionService,
                                                SecurityCryptoProperties properties,
                                                ObjectMapper objectMapper) {
        this.handlerMapping = handlerMapping;
        this.encryptionService = encryptionService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

//        HandlerMethod handlerMethod = resolveHandlerMethod(request);
//        boolean isEncryptedApiEndpoint = handlerMethod != null && isAnnotated(handlerMethod);

        SecurityCryptoProperties.EncryptedApi config = properties.getEncryptedApi();
        String headerValue = request.getHeader(config.getHeaderName());
        boolean callerSignalsEncryptedBody = config.getHeaderTrueValue().equalsIgnoreCase(headerValue);

        //if (!isEncryptedApiEndpoint || !callerSignalsEncryptedBody) {
        if (!callerSignalsEncryptedBody) {
            filterChain.doFilter(request, response);
            return;
        }

        byte[] rawBody = request.getInputStream().readAllBytes();
        String plaintext = decryptEnvelope(rawBody, config);
        filterChain.doFilter(new EncryptedRequestWrapper(request, plaintext), response);
    }

    private String decryptEnvelope(byte[] rawBody, SecurityCryptoProperties.EncryptedApi config) {
        try {
            JsonNode envelope = objectMapper.readTree(rawBody);
            JsonNode ciphertextNode = envelope.get(config.getPayloadFieldName());
            if (ciphertextNode == null || ciphertextNode.isNull()) {
                throw new EncryptionException(
                        "Expected JSON field \"" + config.getPayloadFieldName() +
                                "\" containing the encrypted payload, but it was missing from the request body", null);
            }
            return encryptionService.decrypt(ciphertextNode.asText());
        } catch (IOException e) {
            throw new EncryptionException(
                    "Request body was not valid JSON - expected {\"" + config.getPayloadFieldName() + "\": \"<ciphertext>\"}", e);
        }
    }

    private HandlerMethod resolveHandlerMethod(HttpServletRequest request) {
        try {
            HandlerExecutionChain chain = handlerMapping.getHandler(request);
            if (chain != null && chain.getHandler() instanceof HandlerMethod handlerMethod) {
                return handlerMethod;
            }
        } catch (Exception e) {
            // No matching handler (e.g. this request will 404), or resolution
            // failed for some other reason - either way, treat this as "not
            // an @EncryptedAPI endpoint" and let the request proceed
            // unmodified; the real dispatch will produce whatever error
            // response is actually appropriate.
            logger.debug("Could not resolve handler method for @EncryptedAPI check", e);
        }
        return null;
    }

    private boolean isAnnotated(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(EncryptedAPI.class)
                || handlerMethod.getBeanType().isAnnotationPresent(EncryptedAPI.class);
    }
}

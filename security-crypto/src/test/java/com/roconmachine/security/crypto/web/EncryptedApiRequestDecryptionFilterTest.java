package com.roconmachine.security.crypto.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roconmachine.security.crypto.annotation.EncryptedAPI;
import com.roconmachine.security.crypto.config.SecurityCryptoProperties;
import com.roconmachine.security.crypto.engine.AesGcmEncryptionService;
import com.roconmachine.security.crypto.engine.EncryptionService;
import com.roconmachine.security.crypto.engine.EnvKeyProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EncryptedApiRequestDecryptionFilterTest {

    static class SampleController {
        @EncryptedAPI
        public void encryptedEndpoint() { }
        public void plainEndpoint() { }
    }

    private String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private SecurityCryptoProperties propertiesWithKey() {
        SecurityCryptoProperties properties = new SecurityCryptoProperties();
        properties.getKeys().put("default", randomBase64Key());
        return properties;
    }

    private HandlerMethod handlerMethodFor(String methodName) throws NoSuchMethodException {
        Method method = SampleController.class.getMethod(methodName);
        return new HandlerMethod(new SampleController(), method);
    }

    @Test
    void decryptsBodyWhenHandlerIsAnnotatedAndHeaderIsPresent() throws Exception {
        SecurityCryptoProperties properties = propertiesWithKey();
        EncryptionService service = new AesGcmEncryptionService(new EnvKeyProvider(properties), properties);
        String ciphertext = service.encrypt("{\"amount\":50}");

        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        HandlerExecutionChain chain = new HandlerExecutionChain(handlerMethodFor("encryptedEndpoint"));
        when(handlerMapping.getHandler(org.mockito.ArgumentMatchers.any())).thenReturn(chain);

        EncryptedApiRequestDecryptionFilter filter = new EncryptedApiRequestDecryptionFilter(
                handlerMapping, service, properties, new ObjectMapper());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/transfer");
        request.addHeader("X-Encrypted", "true");
        request.setContent(("{\"data\":\"" + ciphertext + "\"}").getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        StringBuilder capturedBody = new StringBuilder();
        filter.doFilter(request, response, (req, res) ->
                capturedBody.append(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8)));

        assertThat(capturedBody.toString()).isEqualTo("{\"amount\":50}");
    }

    @Test
    void doesNotDecryptWhenHeaderIsAbsentEvenIfHandlerIsAnnotated() throws Exception {
        SecurityCryptoProperties properties = propertiesWithKey();
        EncryptionService service = new AesGcmEncryptionService(new EnvKeyProvider(properties), properties);

        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        HandlerExecutionChain chain = new HandlerExecutionChain(handlerMethodFor("encryptedEndpoint"));
        when(handlerMapping.getHandler(org.mockito.ArgumentMatchers.any())).thenReturn(chain);

        EncryptedApiRequestDecryptionFilter filter = new EncryptedApiRequestDecryptionFilter(
                handlerMapping, service, properties, new ObjectMapper());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/transfer");
        // no X-Encrypted header this time
        request.setContent("{\"amount\":50}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        StringBuilder capturedBody = new StringBuilder();
        filter.doFilter(request, response, (req, res) ->
                capturedBody.append(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8)));

        assertThat(capturedBody.toString()).isEqualTo("{\"amount\":50}"); // unchanged - never touched
    }

    @Test
    void doesNotDecryptWhenHandlerIsNotAnnotatedEvenIfHeaderIsPresent() throws Exception {
        SecurityCryptoProperties properties = propertiesWithKey();
        EncryptionService service = new AesGcmEncryptionService(new EnvKeyProvider(properties), properties);

        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        HandlerExecutionChain chain = new HandlerExecutionChain(handlerMethodFor("plainEndpoint"));
        when(handlerMapping.getHandler(org.mockito.ArgumentMatchers.any())).thenReturn(chain);

        EncryptedApiRequestDecryptionFilter filter = new EncryptedApiRequestDecryptionFilter(
                handlerMapping, service, properties, new ObjectMapper());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/plain");
        request.addHeader("X-Encrypted", "true");
        request.setContent("{\"amount\":50}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        StringBuilder capturedBody = new StringBuilder();
        filter.doFilter(request, response, (req, res) ->
                capturedBody.append(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8)));

        assertThat(capturedBody.toString()).isEqualTo("{\"amount\":50}"); // unchanged - endpoint never opted in
    }
}

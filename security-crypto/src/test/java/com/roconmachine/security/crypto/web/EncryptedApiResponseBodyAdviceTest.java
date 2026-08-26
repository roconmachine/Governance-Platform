package com.roconmachine.security.crypto.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roconmachine.security.crypto.annotation.EncryptedAPI;
import com.roconmachine.security.crypto.config.SecurityCryptoProperties;
import com.roconmachine.security.crypto.engine.AesGcmEncryptionService;
import com.roconmachine.security.crypto.engine.EncryptionService;
import com.roconmachine.security.crypto.engine.EnvKeyProvider;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.HttpHeaders;

import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EncryptedApiResponseBodyAdviceTest {

    static class SampleController {
        @EncryptedAPI
        public String encryptedEndpoint() { return null; }
        public String plainEndpoint() { return null; }
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

    private MethodParameter returnTypeOf(String methodName) throws NoSuchMethodException {
        Method method = SampleController.class.getMethod(methodName);
        return new MethodParameter(method, -1);
    }

    @Test
    void supportsReturnsTrueOnlyForAnnotatedMethods() throws NoSuchMethodException {
        SecurityCryptoProperties properties = propertiesWithKey();
        EncryptionService service = new AesGcmEncryptionService(new EnvKeyProvider(properties), properties);
        EncryptedApiResponseBodyAdvice advice = new EncryptedApiResponseBodyAdvice(service, properties, new ObjectMapper());

        assertThat(advice.supports(returnTypeOf("encryptedEndpoint"), null)).isTrue();
        assertThat(advice.supports(returnTypeOf("plainEndpoint"), null)).isFalse();
    }

    @Test
    void beforeBodyWriteReturnsEncryptedPayloadEnvelope() throws NoSuchMethodException {
        SecurityCryptoProperties properties = propertiesWithKey();
        EncryptionService service = new AesGcmEncryptionService(new EnvKeyProvider(properties), properties);
        EncryptedApiResponseBodyAdvice advice = new EncryptedApiResponseBodyAdvice(service, properties, new ObjectMapper());

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        HttpHeaders headers = new HttpHeaders();
        when(response.getHeaders()).thenReturn(headers);

        Object result = advice.beforeBodyWrite("hello world", returnTypeOf("encryptedEndpoint"), null, null,
                request, response);

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> envelope = (Map<String, String>) result;
        String ciphertext = envelope.get("data");
        assertThat(ciphertext).isNotNull();
        assertThat(ciphertext).isNotEqualTo("hello world");
        assertThat(service.decrypt(ciphertext)).isEqualTo("\"hello world\""); // JSON-serialized form of the String

        assertThat(headers.getFirst("X-Encrypted")).isEqualTo("true");
    }

    @Test
    void nullBodyPassesThroughUnchanged() throws NoSuchMethodException {
        SecurityCryptoProperties properties = propertiesWithKey();
        EncryptionService service = new AesGcmEncryptionService(new EnvKeyProvider(properties), properties);
        EncryptedApiResponseBodyAdvice advice = new EncryptedApiResponseBodyAdvice(service, properties, new ObjectMapper());

        Object result = advice.beforeBodyWrite(null, returnTypeOf("encryptedEndpoint"), null, null, null, null);

        assertThat(result).isNull();
    }
}

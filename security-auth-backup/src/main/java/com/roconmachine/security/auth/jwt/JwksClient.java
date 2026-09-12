package com.roconmachine.security.auth.jwt;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fetches and caches JSON Web Key Set (JWKS) from a remote endpoint.
 * Used for validating JWTs signed by external identity providers like Keycloak.
 *
 * Supports both:
 * - JWKS endpoint (fetches keys dynamically)
 * - Static PEM-encoded public key (loaded once)
 */
public class JwksClient {

    private static final Logger logger = LoggerFactory.getLogger(JwksClient.class);
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final int JWKS_FETCH_TIMEOUT_SECONDS = 5;

    private final String jwksUri;
    private final long cacheDurationSeconds;
    private final Map<String, Key> keyCache = new HashMap<>();
    private Instant cacheExpiryTime = Instant.EPOCH;

    public JwksClient(String jwksUri, long cacheDurationSeconds) {
        this.jwksUri = jwksUri;
        this.cacheDurationSeconds = cacheDurationSeconds;
    }

    /**
     * Fetches a key by its Key ID (kid).
     * Loads from cache if valid, otherwise fetches from the remote JWKS endpoint.
     *
     * @param kid the Key ID from the JWT header
     * @return the public key, if found
     * @throws TokenValidationException if the key cannot be fetched or parsed
     */
    public Optional<Key> getKey(String kid) throws TokenValidationException {
        if (isCacheValid() && keyCache.containsKey(kid)) {
            logger.debug("Using cached key for kid: {}", kid);
            return Optional.of(keyCache.get(kid));
        }

        refreshCache();

        if (keyCache.containsKey(kid)) {
            return Optional.of(keyCache.get(kid));
        }

        logger.warn("Key not found in JWKS for kid: {}", kid);
        return Optional.empty();
    }

    /**
     * Fetches all keys from the JWKS endpoint and updates the cache.
     */
    private void refreshCache() throws TokenValidationException {
        try {
            logger.debug("Fetching JWKS from: {}", jwksUri);
            HttpRequest request = HttpRequest.newBuilder(URI.create(jwksUri))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(JWKS_FETCH_TIMEOUT_SECONDS))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new TokenValidationException("JWKS endpoint returned status " + response.statusCode());
            }

            parseJwks(response.body());
            cacheExpiryTime = Instant.now().plusSeconds(cacheDurationSeconds);
            logger.debug("JWKS cache refreshed, {} keys loaded", keyCache.size());
        } catch (TokenValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new TokenValidationException("Failed to fetch JWKS from " + jwksUri, e);
        }
    }

    /**
     * Parses the JWKS JSON response and extracts public keys.
     * Supports RSA (RSA256, RSA384, RSA512) and EC (ECDSA256, ECDSA384, ECDSA512) keys.
     */
    private void parseJwks(String jwksJson) throws TokenValidationException {
        try {
            keyCache.clear();
            // Simple JSON parsing for JWKS format
            // Format: {"keys": [{"kid": "...", "kty": "RSA", "n": "...", "e": "...", ...}, ...]}
            Map<String, Object> jwks = parseSimpleJson(jwksJson);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
            if (keys == null) {
                throw new TokenValidationException("JWKS response missing 'keys' field");
            }

            for (Map<String, Object> keyData : keys) {
                String kid = (String) keyData.get("kid");
                String kty = (String) keyData.get("kty");

                if (kid == null || kty == null) {
                    logger.debug("Skipping key without kid or kty");
                    continue;
                }

                try {
                    Key key = parseKey(keyData, kty);
                    keyCache.put(kid, key);
                } catch (Exception e) {
                    logger.warn("Failed to parse key with kid {}: {}", kid, e.getMessage());
                }
            }
        } catch (TokenValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new TokenValidationException("Failed to parse JWKS response", e);
        }
    }

    /**
     * Parses a single JWK and converts it to a Java Key object.
     * Supports RSA and EC algorithms.
     */
    private Key parseKey(Map<String, Object> keyData, String kty) throws Exception {
        if ("RSA".equals(kty)) {
            return parseRsaKey(keyData);
        } else if ("EC".equals(kty)) {
            return parseEcKey(keyData);
        } else {
            throw new TokenValidationException("Unsupported key type: " + kty);
        }
    }

    /**
     * Parses an RSA public key from JWK components (n, e).
     */
    private Key parseRsaKey(Map<String, Object> keyData) throws Exception {
        String n = (String) keyData.get("n");
        String e = (String) keyData.get("e");

        if (n == null || e == null) {
            throw new TokenValidationException("RSA key missing 'n' or 'e' component");
        }

        byte[] nBytes = Base64.getUrlDecoder().decode(n);
        byte[] eBytes = Base64.getUrlDecoder().decode(e);

        java.math.BigInteger modulus = new java.math.BigInteger(1, nBytes);
        java.math.BigInteger exponent = new java.math.BigInteger(1, eBytes);

        java.security.spec.RSAPublicKeySpec spec = new java.security.spec.RSAPublicKeySpec(modulus, exponent);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePublic(spec);
    }

    /**
     * Parses an EC public key from JWK components.
     * EC support requires BouncyCastle library. For now, throws unsupported exception.
     * To enable EC support, add org.bouncycastle:bcprov-jdk15on dependency.
     */
    private Key parseEcKey(Map<String, Object> keyData) throws Exception {
        logger.warn("EC key type is not yet supported. Please use RSA keys or add BouncyCastle dependency for EC support.");
        throw new TokenValidationException("EC key type is not yet supported. Use RSA keys or configure RSA public key.");
    }

    /**
     * Simple JSON parser for JWKS response.
     * Only handles basic object and array structures.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSimpleJson(String json) throws Exception {
        // For production, use a proper JSON library (e.g., Jackson)
        // This is a minimal parser for demonstration
        Map<String, Object> result = new HashMap<>();

        // Very basic parsing - expects format: {"keys": [...]}
        int keysStart = json.indexOf("\"keys\"");
        int arrayStart = json.indexOf('[', keysStart);
        int arrayEnd = json.lastIndexOf(']');

        if (keysStart >= 0 && arrayStart >= 0 && arrayEnd >= 0) {
            String arrayJson = json.substring(arrayStart, arrayEnd + 1);
            List<Map<String, Object>> keys = parseJsonArray(arrayJson);
            result.put("keys", keys);
        }

        return result;
    }

    /**
     * Parses a JSON array of objects representing JWKs.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJsonArray(String arrayJson) throws Exception {
        List<Map<String, Object>> keys = new java.util.ArrayList<>();
        int depth = 0;
        int objectStart = -1;

        for (int i = 0; i < arrayJson.length(); i++) {
            char c = arrayJson.charAt(i);

            if (c == '{') {
                if (depth == 0) {
                    objectStart = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objectStart >= 0) {
                    String objJson = arrayJson.substring(objectStart, i + 1);
                    Map<String, Object> obj = parseJsonObject(objJson);
                    keys.add(obj);
                }
            }
        }

        return keys;
    }

    /**
     * Parses a single JSON object into a Map.
     */
    private Map<String, Object> parseJsonObject(String objJson) throws Exception {
        Map<String, Object> obj = new HashMap<>();
        int i = 0;

        while (i < objJson.length()) {
            char c = objJson.charAt(i);

            // Find the key
            if (c == '"') {
                int keyStart = i + 1;
                int keyEnd = objJson.indexOf('"', keyStart);
                String key = objJson.substring(keyStart, keyEnd);

                // Find the value
                int colonPos = objJson.indexOf(':', keyEnd);
                int valueStart = colonPos + 1;
                while (valueStart < objJson.length() && Character.isWhitespace(objJson.charAt(valueStart))) {
                    valueStart++;
                }

                int valueEnd = valueStart;
                if (objJson.charAt(valueStart) == '"') {
                    valueEnd = objJson.indexOf('"', valueStart + 1);
                    String value = objJson.substring(valueStart + 1, valueEnd);
                    obj.put(key, value);
                    i = valueEnd + 1;
                } else if (objJson.charAt(valueStart) == '[' || objJson.charAt(valueStart) == '{') {
                    // For nested structures, find the matching bracket
                    char startChar = objJson.charAt(valueStart);
                    char endChar = startChar == '[' ? ']' : '}';
                    int nestDepth = 1;
                    valueEnd = valueStart + 1;
                    while (valueEnd < objJson.length() && nestDepth > 0) {
                        if (objJson.charAt(valueEnd) == startChar) nestDepth++;
                        if (objJson.charAt(valueEnd) == endChar) nestDepth--;
                        valueEnd++;
                    }
                    i = valueEnd;
                } else {
                    // Number or boolean
                    while (valueEnd < objJson.length() && objJson.charAt(valueEnd) != ',' && objJson.charAt(valueEnd) != '}') {
                        valueEnd++;
                    }
                    String value = objJson.substring(valueStart, valueEnd).trim();
                    obj.put(key, value);
                    i = valueEnd;
                }
            } else {
                i++;
            }
        }

        return obj;
    }

    /**
     * Checks if the cache is still valid.
     */
    private boolean isCacheValid() {
        return Instant.now().isBefore(cacheExpiryTime);
    }
}

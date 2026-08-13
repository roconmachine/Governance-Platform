package com.platform.security.exchange.client;

import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IClientCredential;
import com.microsoft.aad.msal4j.IConfidentialClientApplication;
import com.platform.security.exchange.config.ExchangeAuthProperties;
import com.platform.security.exchange.exception.ExchangeAuthException;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

/**
 * Builds the long-lived {@link IConfidentialClientApplication} instance
 * this module reuses for every token request. MSAL4J's own documentation
 * states this object is thread-safe and is meant to be created ONCE and
 * reused - it maintains its own internal token cache and connection
 * pooling internally, so constructing a new one per request would both hurt
 * performance and defeat MSAL4J's own caching. This factory exists so
 * {@code ExchangeAuthAutoConfiguration} creates exactly one, registered as a
 * singleton Spring bean.
 */
public class ConfidentialClientFactory {

    private final ExchangeAuthProperties properties;

    public ConfidentialClientFactory(ExchangeAuthProperties properties) {
        this.properties = properties;
    }

    public IConfidentialClientApplication create() {
        requireConfigured(properties.getTenantId(), "exchange.auth.tenant-id");
        requireConfigured(properties.getClientId(), "exchange.auth.client-id");

        IClientCredential credential = buildCredential();
        String authority = properties.getAuthorityHost() + properties.getTenantId() + "/";

        try {
            return ConfidentialClientApplication.builder(properties.getClientId(), credential)
                    .authority(authority)
                    .build();
        } catch (MalformedURLException e) {
            throw new ExchangeAuthException(
                    "Invalid authority URL built from exchange.auth.authority-host + tenant-id: " + authority, e);
        }
    }

    private IClientCredential buildCredential() {
        boolean hasSecret = hasText(properties.getClientSecret());
        boolean hasCertificate = hasText(properties.getCertificate().getPath());

        if (hasSecret && hasCertificate) {
            throw new ExchangeAuthException(
                    "Both exchange.auth.client-secret and exchange.auth.certificate.path are configured - " +
                            "set exactly one credential, not both, so it's unambiguous which one is actually in use.");
        }
        if (hasSecret) {
            return ClientCredentialFactory.createFromSecret(properties.getClientSecret());
        }
        if (hasCertificate) {
            return buildCertificateCredential();
        }
        // Fail closed, same philosophy as security-crypto's EnvKeyProvider:
        // refuse to start rather than silently running with no credential at all.
        throw new ExchangeAuthException(
                "Neither exchange.auth.client-secret nor exchange.auth.certificate.path is configured - " +
                        "this module cannot authenticate to Entra ID without one of them.");
    }

    private IClientCredential buildCertificateCredential() {
        ExchangeAuthProperties.Certificate cert = properties.getCertificate();
        try (InputStream in = Files.newInputStream(Path.of(cert.getPath()))) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            char[] password = requireNonBlankPassword(cert.getPassword());
            keyStore.load(in, password);

            String alias = resolveAlias(keyStore, cert.getAlias());
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password);
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);

            if (privateKey == null || certificate == null) {
                throw new ExchangeAuthException(
                        "Alias '" + alias + "' in keystore " + cert.getPath() +
                                " does not contain both a private key and a certificate");
            }
            return ClientCredentialFactory.createFromCertificate(privateKey, certificate);
        } catch (ExchangeAuthException e) {
            throw e;
        } catch (Exception e) {
            // Deliberately does not include the password in the message, obviously,
            // but also avoid echoing raw provider exception text that can vary by
            // JCE provider and sometimes includes more path/alias detail than needed.
            throw new ExchangeAuthException(
                    "Failed to load client certificate from " + cert.getPath() + " - check the path, " +
                            "password, and alias are correct, and that the file is a valid PKCS12 keystore", e);
        }
    }

    private String resolveAlias(KeyStore keyStore, String configuredAlias) throws Exception {
        if (hasText(configuredAlias)) {
            return configuredAlias;
        }
        Enumeration<String> aliases = keyStore.aliases();
        if (!aliases.hasMoreElements()) {
            throw new ExchangeAuthException("Keystore " + properties.getCertificate().getPath() + " contains no aliases");
        }
        return aliases.nextElement();
    }

    private char[] requireNonBlankPassword(String password) {
        if (!hasText(password)) {
            throw new ExchangeAuthException(
                    "exchange.auth.certificate.password is required when exchange.auth.certificate.path is set");
        }
        return password.toCharArray();
    }

    private void requireConfigured(String value, String propertyName) {
        if (!hasText(value)) {
            throw new ExchangeAuthException(propertyName + " is required but was not configured");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

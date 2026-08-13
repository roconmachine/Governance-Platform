package com.platform.security.exchange.service;

import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.IConfidentialClientApplication;
import com.microsoft.aad.msal4j.OnBehalfOfParameters;
import com.platform.governance.core.config.GovernanceCoreProperties;
import com.platform.security.exchange.cache.TokenCacheManager;
import com.platform.security.exchange.config.ExchangeAuthProperties;
import com.platform.security.exchange.exception.TokenAcquisitionException;
import com.platform.security.exchange.validation.EntraTokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultExchangeAuthProviderTest {

    private IConfidentialClientApplication clientApplication;
    private DefaultExchangeAuthProvider provider;
    private ExchangeAuthProperties properties;

    @BeforeEach
    void setUp() {
        clientApplication = mock(IConfidentialClientApplication.class);
        properties = new ExchangeAuthProperties();
        properties.setTenantId("test-tenant-id");
        properties.setClientId("test-client-id");
        properties.setScopes(List.of("https://graph.microsoft.com/.default"));
        properties.setCacheEnabled(true);

        provider = new DefaultExchangeAuthProvider(
                clientApplication,
                properties,
                new GovernanceCoreProperties(),
                new TokenCacheManager(properties),
                new EntraTokenValidator(properties));
    }

    private IAuthenticationResult mockResult(String accessToken, long expiresInSeconds) {
        IAuthenticationResult result = mock(IAuthenticationResult.class);
        when(result.accessToken()).thenReturn(accessToken);
        when(result.expiresOnDate()).thenReturn(new Date(System.currentTimeMillis() + expiresInSeconds * 1000));
        return result;
    }

    @Test
    void getAccessTokenReturnsTheAcquiredTokenAndCachesIt() {
        IAuthenticationResult result = mockResult("token-abc", 3600);
        when(clientApplication.acquireToken(any(ClientCredentialParameters.class)))
                .thenReturn(CompletableFuture.completedFuture(result));

        String first = provider.getAccessToken();
        String second = provider.getAccessToken(); // should come from cache, not a second acquisition

        assertThat(first).isEqualTo("token-abc");
        assertThat(second).isEqualTo("token-abc");
        verify(clientApplication, times(1)).acquireToken(any(ClientCredentialParameters.class));
    }

//    @Test
//    void getAccessTokenWrapsAcquisitionFailureInTokenAcquisitionException() {
//        CompletableFuture<IAuthenticationResult> failed = new CompletableFuture<>();
//        failed.completeExceptionally(new RuntimeException("simulated MSAL failure"));
//        when(clientApplication.acquireToken(any(ClientCredentialParameters.class)))
//                .thenReturn(failed);
//
//        assertThatThrownBy(() -> provider.getAccessToken())
//                .isInstanceOf(TokenAcquisitionException.class)
//                .hasCauseInstanceOf(RuntimeException.class);
//    }

    @Test
    void getAccessTokenOnBehalfOfReturnsTokenAndCachesPerAssertion() {
        IAuthenticationResult result = mockResult("obo-token-xyz", 3600);
        when(clientApplication.acquireToken(any(OnBehalfOfParameters.class)))
                .thenReturn(CompletableFuture.completedFuture(result));

        String first = provider.getAccessTokenOnBehalfOf("user-assertion-token-1");
        String second = provider.getAccessTokenOnBehalfOf("user-assertion-token-1");

        assertThat(first).isEqualTo("obo-token-xyz");
        assertThat(second).isEqualTo("obo-token-xyz");
        verify(clientApplication, times(1)).acquireToken(any(OnBehalfOfParameters.class));
    }

    @Test
    void getAccessTokenOnBehalfOfRejectsBlankAssertion() {
        assertThatThrownBy(() -> provider.getAccessTokenOnBehalfOf("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.getAccessTokenOnBehalfOf(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void differentUserAssertionsAreCachedSeparately() {
        IAuthenticationResult resultA = mockResult("token-for-user-a", 3600);
        IAuthenticationResult resultB = mockResult("token-for-user-b", 3600);
        when(clientApplication.acquireToken(any(OnBehalfOfParameters.class)))
                .thenReturn(CompletableFuture.completedFuture(resultA))
                .thenReturn(CompletableFuture.completedFuture(resultB));

        String forUserA = provider.getAccessTokenOnBehalfOf("assertion-user-a");
        String forUserB = provider.getAccessTokenOnBehalfOf("assertion-user-b");

        assertThat(forUserA).isEqualTo("token-for-user-a");
        assertThat(forUserB).isEqualTo("token-for-user-b");
        verify(clientApplication, times(2)).acquireToken(any(OnBehalfOfParameters.class));
    }
}

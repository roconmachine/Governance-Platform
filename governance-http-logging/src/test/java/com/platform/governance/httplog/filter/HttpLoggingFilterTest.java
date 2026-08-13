package com.platform.governance.httplog.filter;

import com.platform.governance.core.config.GovernanceCoreProperties;
import com.platform.governance.core.masking.SensitiveDataMasker;
import com.platform.governance.httplog.config.GovernanceHttpLoggingProperties;
import com.platform.governance.httplog.publisher.HttpAccessLogPublisher;
import com.platform.governance.httplog.model.HttpAccessLogEvent;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HttpLoggingFilterTest {

    // Runnable::run executes synchronously in these tests regardless of
    // properties.isAsync() - we're testing the filter's capture/masking
    // logic here, not the executor itself (governance-audit and
    // governance-exception-handling's tests already cover the
    // MdcTaskDecorator/CallerRunsPolicy mechanics this filter reuses).
    private static final java.util.concurrent.Executor SYNCHRONOUS_EXECUTOR = Runnable::run;

    @Test
    void excludedPathsAreNeverCaptured() throws Exception {
        GovernanceHttpLoggingProperties props = new GovernanceHttpLoggingProperties();
        List<HttpAccessLogEvent> captured = new ArrayList<>();
        HttpLoggingFilter filter = new HttpLoggingFilter(props, new GovernanceCoreProperties(),
                new SensitiveDataMasker(), captured::add, SYNCHRONOUS_EXECUTOR);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {};

        filter.doFilter(request, response, chain);

        assertThat(captured).isEmpty();
    }

    @Test
    void sensitiveBodyFieldsAreMaskedWhenBodyLoggingIsEnabled() throws Exception {
        GovernanceHttpLoggingProperties props = new GovernanceHttpLoggingProperties();
        props.setIncludeBody(true);
        List<HttpAccessLogEvent> captured = new ArrayList<>();
        HttpLoggingFilter filter = new HttpLoggingFilter(props, new GovernanceCoreProperties(),
                new SensitiveDataMasker(), captured::add, SYNCHRONOUS_EXECUTOR);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/transfer");
        String body = "{\"fromAccount\":\"ACC-1\",\"cardNumber\":\"4111111111111234\"}";
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            // simulate the controller actually reading the (wrapped) request body,
            // as a real @RestController would via @RequestBody
            req.getInputStream().readAllBytes();
        };

        filter.doFilter(request, response, chain);

        assertThat(captured).hasSize(1);
        String requestBody = captured.get(0).getRequestBody();
        assertThat(requestBody).contains("fromAccount");
        assertThat(requestBody).contains("cardNumber\":\"****\"");
        assertThat(requestBody).doesNotContain("4111111111111234");
    }

    @Test
    void queryStringAndRequestParametersAreCapturedByDefault() throws Exception {
        GovernanceHttpLoggingProperties props = new GovernanceHttpLoggingProperties();
        List<HttpAccessLogEvent> captured = new ArrayList<>();
        HttpLoggingFilter filter = new HttpLoggingFilter(props, new GovernanceCoreProperties(),
                new SensitiveDataMasker(), captured::add, SYNCHRONOUS_EXECUTOR);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/accounts");
        request.setQueryString("status=active&limit=10");
        request.addParameter("status", "active");
        request.addParameter("limit", "10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getQueryString()).isEqualTo("status=active&limit=10");
        assertThat(captured.get(0).getRequestParameters()).contains("status=active").contains("limit=10");
    }

    @Test
    void sensitiveQueryAndRequestParameterValuesAreMasked() throws Exception {
        GovernanceHttpLoggingProperties props = new GovernanceHttpLoggingProperties();
        List<HttpAccessLogEvent> captured = new ArrayList<>();
        HttpLoggingFilter filter = new HttpLoggingFilter(props, new GovernanceCoreProperties(),
                new SensitiveDataMasker(), captured::add, SYNCHRONOUS_EXECUTOR);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/lookup");
        request.setQueryString("cardNumber=4111111111111234&status=active");
        request.addParameter("cardNumber", "4111111111111234");
        request.addParameter("status", "active");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getQueryString()).contains("status=active").doesNotContain("4111111111111234");
        assertThat(captured.get(0).getRequestParameters()).doesNotContain("4111111111111234");
    }

    @Test
    void paramsAreNotCapturedWhenDisabled() throws Exception {
        GovernanceHttpLoggingProperties props = new GovernanceHttpLoggingProperties();
        props.setIncludeParams(false);
        List<HttpAccessLogEvent> captured = new ArrayList<>();
        HttpLoggingFilter filter = new HttpLoggingFilter(props, new GovernanceCoreProperties(),
                new SensitiveDataMasker(), captured::add, SYNCHRONOUS_EXECUTOR);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/accounts");
        request.setQueryString("status=active");
        request.addParameter("status", "active");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getQueryString()).isNull();
        assertThat(captured.get(0).getRequestParameters()).isNull();
    }
}

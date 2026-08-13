package com.roconmachine.governance.response.model;

import com.roconmachine.governance.response.config.ApplicationResponseProperties;
import com.roconmachine.governance.response.trace.TraceIdProvider;
import org.springframework.http.HttpStatus;

/**
 * Autowire this in a controller/service to build success responses without
 * hand-formatting the appCode - the counterpart to
 * {@code GlobalAppExceptionHandler}, which does the same for errors.
 */
public class AppResponseFactory {

    private final ApplicationResponseProperties properties;
    private final TraceIdProvider traceIdProvider;

    public AppResponseFactory(ApplicationResponseProperties properties, TraceIdProvider traceIdProvider) {
        this.properties = properties;
        this.traceIdProvider = traceIdProvider;
    }

    public <T> AppResponse<T> success(T data) {
        return success(HttpStatus.OK.value(), data, "Success");
    }

    public <T> AppResponse<T> success(T data, String message) {
        return success(HttpStatus.OK.value(), data, message);
    }

    public <T> AppResponse<T> success(int httpCode, T data, String message) {
        String appCode = AppCodeFormatter.format(properties.getServiceId(), AppCodeFormatter.INFO_TYPE,
                properties.getSuccessEventCode());
        return AppResponse.success(httpCode, appCode, message, data, traceIdProvider.currentTraceId());
    }
}

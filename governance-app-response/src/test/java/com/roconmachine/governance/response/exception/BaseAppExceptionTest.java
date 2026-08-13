package com.roconmachine.governance.response.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BaseAppExceptionTest {

    @Test
    void businessExceptionAlwaysHasBusinessType() {
        BusinessException ex = new BusinessException("0001", "insufficient funds", HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ex.getType()).isEqualTo(ExceptionType.BUSINESS);
        assertThat(ex.getEventCode()).isEqualTo("0001");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void systemExceptionAlwaysHasSystemTypeAndDefaultsTo500() {
        SystemException ex = new SystemException("1001", "downstream unavailable");
        assertThat(ex.getType()).isEqualTo(ExceptionType.SYSTEM);
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void rejectsEventCodeThatIsNotExactlyFourDigits() {
        assertThatThrownBy(() -> new BusinessException("1", "bad code", HttpStatus.BAD_REQUEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4 digits");

        assertThatThrownBy(() -> new BusinessException("ABCD", "bad code", HttpStatus.BAD_REQUEST))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new BusinessException(null, "bad code", HttpStatus.BAD_REQUEST))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private enum SampleErrorCode implements ErrorCode {
        INSUFFICIENT_FUNDS("0001", ExceptionType.BUSINESS, "Insufficient funds", HttpStatus.UNPROCESSABLE_ENTITY);

        private final String eventCode;
        private final ExceptionType type;
        private final String defaultMessage;
        private final HttpStatus httpStatus;

        SampleErrorCode(String eventCode, ExceptionType type, String defaultMessage, HttpStatus httpStatus) {
            this.eventCode = eventCode;
            this.type = type;
            this.defaultMessage = defaultMessage;
            this.httpStatus = httpStatus;
        }

        public String eventCode() { return eventCode; }
        public ExceptionType type() { return type; }
        public String defaultMessage() { return defaultMessage; }
        public HttpStatus httpStatus() { return httpStatus; }
    }

    @Test
    void businessExceptionFromErrorCodeUsesItsDefaults() {
        BusinessException ex = new BusinessException(SampleErrorCode.INSUFFICIENT_FUNDS);
        assertThat(ex.getEventCode()).isEqualTo("0001");
        assertThat(ex.getMessage()).isEqualTo("Insufficient funds");
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void businessExceptionFromErrorCodeCanOverrideMessage() {
        BusinessException ex = new BusinessException(SampleErrorCode.INSUFFICIENT_FUNDS, "Custom message for account 123");
        assertThat(ex.getMessage()).isEqualTo("Custom message for account 123");
        assertThat(ex.getEventCode()).isEqualTo("0001"); // code/status still come from the ErrorCode
    }
}

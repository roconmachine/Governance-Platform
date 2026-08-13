package com.platform.governance.response.config;

import com.platform.governance.response.exception.InvalidServiceIdException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationResponsePropertiesValidatorTest {

    private final ApplicationResponsePropertiesValidator validator = new ApplicationResponsePropertiesValidator();

    private ApplicationResponseProperties propertiesWithServiceId(String serviceId) {
        ApplicationResponseProperties properties = new ApplicationResponseProperties();
        properties.setServiceId(serviceId);
        return properties;
    }

    @Test
    void acceptsThreeUppercaseLetters() {
        assertThatCode(() -> validator.validate(propertiesWithServiceId("PAY"))).doesNotThrowAnyException();
    }

    @Test
    void acceptsThreeDigits() {
        assertThatCode(() -> validator.validate(propertiesWithServiceId("101"))).doesNotThrowAnyException();
    }

    @Test
    void acceptsMixedAlphanumeric() {
        assertThatCode(() -> validator.validate(propertiesWithServiceId("P1Y"))).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullServiceId() {
        assertThatThrownBy(() -> validator.validate(propertiesWithServiceId(null)))
                .isInstanceOf(InvalidServiceIdException.class)
                .hasMessageContaining("required");
    }

    @Test
    void rejectsBlankServiceId() {
        assertThatThrownBy(() -> validator.validate(propertiesWithServiceId("   ")))
                .isInstanceOf(InvalidServiceIdException.class);
    }

    @Test
    void rejectsTooShortServiceId() {
        assertThatThrownBy(() -> validator.validate(propertiesWithServiceId("PA")))
                .isInstanceOf(InvalidServiceIdException.class)
                .hasMessageContaining("3 alphanumeric characters");
    }

    @Test
    void rejectsTooLongServiceId() {
        assertThatThrownBy(() -> validator.validate(propertiesWithServiceId("PAYS")))
                .isInstanceOf(InvalidServiceIdException.class);
    }

    @Test
    void rejectsServiceIdWithSpecialCharacters() {
        assertThatThrownBy(() -> validator.validate(propertiesWithServiceId("PA-")))
                .isInstanceOf(InvalidServiceIdException.class);
    }
}

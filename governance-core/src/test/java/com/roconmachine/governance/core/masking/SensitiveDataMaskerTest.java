package com.roconmachine.governance.core.masking;

import com.roconmachine.governance.core.annotation.Sensitive;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {

    static class TransferRequest {
        String fromAccount = "ACC-001";
        @Sensitive(strategy = Sensitive.MaskStrategy.PARTIAL)
        String cardNumber = "4111111111111234";
        @Sensitive(strategy = Sensitive.MaskStrategy.FULL)
        String pin = "9821";
        @Sensitive(strategy = Sensitive.MaskStrategy.HASH)
        String nationalId = "1234567890";
    }

    private final SensitiveDataMasker masker = new SensitiveDataMasker();

    @Test
    void nonSensitiveFieldsRemainVisible() {
        String masked = masker.mask(new TransferRequest());
        assertThat(masked).contains("fromAccount=ACC-001");
    }

    @Test
    void partialStrategyKeepsLastFourCharacters() {
        String masked = masker.mask(new TransferRequest());
        assertThat(masked).contains("cardNumber=****1234");
        assertThat(masked).doesNotContain("4111111111111234");
    }

    @Test
    void fullStrategyHidesEntireValue() {
        String masked = masker.mask(new TransferRequest());
        assertThat(masked).contains("pin=****");
        assertThat(masked).doesNotContain("9821");
    }

    @Test
    void hashStrategyProducesDeterministicNonReversibleValue() {
        String masked = masker.mask(new TransferRequest());
        assertThat(masked).contains("nationalId=sha256:");
        assertThat(masked).doesNotContain("1234567890");
    }

    @Test
    void rawValueMaskingWorksWithoutAnAnnotatedField() {
        // used by governance-http-logging for header values keyed by configured header name
        assertThat(masker.maskValue("Bearer abc123xyz789", Sensitive.MaskStrategy.FULL)).isEqualTo("****");
        assertThat(masker.maskValue("4111111111111234", Sensitive.MaskStrategy.PARTIAL)).isEqualTo("****1234");
    }
}

package com.shoploc.store_service.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RibIbanHelperTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "FR7630006000011234567890189",          // Valid French IBAN
            "FR76 3000 6000 0112 3456 7890 189",    // Valid French IBAN with spaces
            "fr7630006000011234567890189",          // Valid lowercase
            "30006000011234567890189",              // Valid 23-char French RIB
            "30006 00001 12345678901 89",          // Valid 23-char French RIB with spaces
            "DE89370400440532013000"               // Valid German IBAN
    })
    @DisplayName("Devrait valider les RIB et IBAN valides")
    void shouldValidateCorrectRibAndIban(String value) {
        assertThat(RibIbanHelper.isValidRibOrIban(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "FR7630006000011234567890188",          // Invalid check digits on French IBAN
            "30006000011234567890188",              // Invalid RIB key (88 instead of 89)
            "FR0000000000000000000000000",          // Invalid IBAN
            "12345",                                // Too short
            "NOT_A_RIB",                            // Non-numeric/malformatted
            "FR76300060000112345678901",            // Incomplete French IBAN (less than 27 chars)
            "",                                     // Empty
            "   "                                   // Whitespace only
    })
    @DisplayName("Devrait rejeter les RIB et IBAN invalides")
    void shouldRejectInvalidRibAndIban(String value) {
        assertThat(RibIbanHelper.isValidRibOrIban(value)).isFalse();
    }

    @Test
    @DisplayName("Devrait rejeter un RIB/IBAN null")
    void shouldRejectNull() {
        assertThat(RibIbanHelper.isValidRibOrIban(null)).isFalse();
    }
}

package com.iotiq.partnererp.unit.common;

import com.iotiq.partnererp.common.util.AmountInWords;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AmountInWordsTest {

    private static String words(String amount) {
        return AmountInWords.rupees(new BigDecimal(amount));
    }

    @Test
    void zeroAndSmallAmounts() {
        assertThat(words("0")).isEqualTo("Rupees Zero Only");
        assertThat(words("1")).isEqualTo("Rupees One Only");
        assertThat(words("19.00")).isEqualTo("Rupees Nineteen Only");
        assertThat(words("100")).isEqualTo("Rupees One Hundred Only");
        assertThat(words("999")).isEqualTo("Rupees Nine Hundred Ninety Nine Only");
    }

    @Test
    void usesLakhAndCrore() {
        assertThat(words("1000")).isEqualTo("Rupees One Thousand Only");
        assertThat(words("100000")).isEqualTo("Rupees One Lakh Only");
        assertThat(words("123456")).isEqualTo("Rupees One Lakh Twenty Three Thousand Four Hundred Fifty Six Only");
        assertThat(words("10000000")).isEqualTo("Rupees One Crore Only");
        assertThat(words("123456789")).isEqualTo("Rupees Twelve Crore Thirty Four Lakh Fifty Six Thousand "
                + "Seven Hundred Eighty Nine Only");
        assertThat(words("1500000000")).isEqualTo("Rupees One Hundred Fifty Crore Only");
    }

    @Test
    void paiseAreSpelledOutAndRounded() {
        assertThat(words("123456.50")).isEqualTo(
                "Rupees One Lakh Twenty Three Thousand Four Hundred Fifty Six and Fifty Paise Only");
        assertThat(words("0.05")).isEqualTo("Rupees Zero and Five Paise Only");
        assertThat(words("10.995")).isEqualTo("Rupees Eleven Only");
        assertThat(words("-5.00")).isEqualTo("Minus Rupees Five Only");
    }
}

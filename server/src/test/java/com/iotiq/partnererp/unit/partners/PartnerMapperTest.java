package com.iotiq.partnererp.unit.partners;

import com.iotiq.partnererp.modules.partners.mapper.PartnerMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PartnerMapperTest {

    @Test
    void masksPanAndBankAccountToTheLastFour() {
        assertThat(PartnerMapper.mask("ABCDE1234F")).isEqualTo("••234F");
        assertThat(PartnerMapper.mask("123456789012")).isEqualTo("••9012");
        assertThat(PartnerMapper.mask(null)).isNull();
        assertThat(PartnerMapper.mask("")).isNull();
    }

    @Test
    void gstinMaskKeepsNoCharacterOfTheEmbeddedPan() {
        String pan = "ABCDE1234F";
        String gstin = "36" + pan + "1Z5";
        String masked = PartnerMapper.maskGstin(gstin);
        assertThat(masked).isEqualTo("••1Z5");
        // Positions 3–12 hold the PAN; the visible tail starts after them.
        assertThat(gstin.indexOf(masked.substring(2))).isEqualTo(2 + pan.length());
        assertThat(PartnerMapper.maskGstin(null)).isNull();
    }
}

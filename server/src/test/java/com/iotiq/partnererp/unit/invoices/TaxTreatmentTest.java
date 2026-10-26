package com.iotiq.partnererp.unit.invoices;

import com.iotiq.partnererp.common.enums.InvoiceStatus;
import com.iotiq.partnererp.common.enums.PaymentStatus;
import com.iotiq.partnererp.modules.invoices.service.InvoiceRules;
import com.iotiq.partnererp.modules.invoices.tax.GstState;
import com.iotiq.partnererp.modules.invoices.tax.Gstin;
import com.iotiq.partnererp.modules.invoices.tax.PlaceOfSupplyBasis;
import com.iotiq.partnererp.modules.invoices.tax.TaxTreatment;
import com.iotiq.partnererp.modules.invoices.tax.TaxTreatment.Inputs;
import com.iotiq.partnererp.modules.invoices.tax.TaxTreatment.Reason;
import com.iotiq.partnererp.modules.invoices.tax.TaxTreatment.Result;
import com.iotiq.partnererp.modules.invoices.tax.TaxType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.iotiq.partnererp.modules.invoices.tax.GstState.CHANDIGARH;
import static com.iotiq.partnererp.modules.invoices.tax.GstState.DELHI;
import static com.iotiq.partnererp.modules.invoices.tax.GstState.KARNATAKA;
import static com.iotiq.partnererp.modules.invoices.tax.GstState.TELANGANA;
import static com.iotiq.partnererp.modules.invoices.tax.PlaceOfSupplyBasis.DELIVERY_LOCATION;
import static com.iotiq.partnererp.modules.invoices.tax.PlaceOfSupplyBasis.INSTALLATION_SITE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tax treatment from explicit inputs (Phase 9 decision 1), GSTIN checks and the derived payment status (BR-V6).
 */
class TaxTreatmentTest {

    @Test
    void theSameStateAsTheSupplierIsIntraStateAndAnyOtherIsInterState() {
        Result intra = TaxTreatment.evaluate(new Inputs(TELANGANA, TELANGANA, null, TELANGANA, DELIVERY_LOCATION, null));
        assertThat(intra.taxType()).isEqualTo(TaxType.INTRA);
        assertThat(intra.utgst()).isFalse();
        assertThat(intra.confirmationRequired()).isFalse();
        assertThat(intra.missing()).isEmpty();

        Result inter = TaxTreatment.evaluate(new Inputs(TELANGANA, KARNATAKA, null, KARNATAKA, DELIVERY_LOCATION, null));
        assertThat(inter.taxType()).isEqualTo(TaxType.INTER);
        assertThat(inter.confirmationRequired()).isFalse();
    }

    @Test
    void theBillingStateNeverDecidesThePlaceOfSupply() {
        // Billed in Telangana, installed in Karnataka: IGST, and a confirmation is needed because the states differ.
        Result r = TaxTreatment.evaluate(new Inputs(TELANGANA, TELANGANA, null, KARNATAKA, INSTALLATION_SITE, null));
        assertThat(r.taxType()).isEqualTo(TaxType.INTER);
        assertThat(r.reasons()).extracting(Reason::code).containsExactly("PLACE_OF_SUPPLY_DIFFERS");

        // No place of supply: nothing is guessed, even though the billing state is known.
        Result none = TaxTreatment.evaluate(new Inputs(TELANGANA, TELANGANA, null, null, null, null));
        assertThat(none.taxType()).isNull();
        assertThat(none.missing()).containsExactly("placeOfSupplyCode", "placeOfSupplyBasis");
    }

    @Test
    void doubtfulInputsNeedAConfirmation() {
        assertThat(TaxTreatment.evaluate(new Inputs(TELANGANA, null, null, TELANGANA, DELIVERY_LOCATION, null))
                .reasons()).extracting(Reason::code).containsExactly("BILLING_STATE_MISSING");
        assertThat(TaxTreatment.evaluate(new Inputs(TELANGANA, TELANGANA, null, TELANGANA, PlaceOfSupplyBasis.OTHER,
                null)).reasons()).extracting(Reason::code).containsExactly("OTHER_BASIS");
        // A Karnataka GSTIN on a Telangana billing address.
        assertThat(TaxTreatment.evaluate(new Inputs(TELANGANA, TELANGANA, "29AAGCB7383J1Z4", TELANGANA,
                DELIVERY_LOCATION, null)).reasons()).extracting(Reason::code).containsExactly("GSTIN_STATE_DIFFERS");
        // The estimate said IGST; the place of supply gives CGST + SGST.
        Result changed = TaxTreatment.evaluate(new Inputs(TELANGANA, TELANGANA, null, TELANGANA, DELIVERY_LOCATION, true));
        assertThat(changed.reasons()).extracting(Reason::code).containsExactly("ESTIMATE_TAX_TYPE_DIFFERS");
        assertThat(TaxTreatment.evaluate(new Inputs(TELANGANA, TELANGANA, null, TELANGANA, DELIVERY_LOCATION, false))
                .confirmationRequired()).isFalse();
    }

    @Test
    void unionTerritoriesWithoutALegislatureLevyUtgst() {
        assertThat(TaxTreatment.evaluate(new Inputs(CHANDIGARH, CHANDIGARH, null, CHANDIGARH, DELIVERY_LOCATION, null))
                .utgst()).isTrue();
        assertThat(TaxTreatment.evaluate(new Inputs(DELHI, DELHI, null, DELHI, DELIVERY_LOCATION, null)).utgst()).isFalse();
        // Inter-state supplies to a union territory are IGST, never UTGST.
        assertThat(TaxTreatment.evaluate(new Inputs(TELANGANA, CHANDIGARH, null, CHANDIGARH, DELIVERY_LOCATION, null))
                .utgst()).isFalse();
    }

    @Test
    void theConfirmationKeyChangesWithEveryInputItCovers() {
        String key = TaxTreatment.evaluate(new Inputs(TELANGANA, null, null, KARNATAKA, DELIVERY_LOCATION, null)).key();
        assertThat(TaxTreatment.evaluate(new Inputs(TELANGANA, null, null, KARNATAKA, DELIVERY_LOCATION, null)).key())
                .isEqualTo(key);
        assertThat(TaxTreatment.evaluate(new Inputs(TELANGANA, null, null, KARNATAKA, INSTALLATION_SITE, null)).key())
                .isNotEqualTo(key);
        assertThat(TaxTreatment.evaluate(new Inputs(KARNATAKA, null, null, KARNATAKA, DELIVERY_LOCATION, null)).key())
                .isNotEqualTo(key);
    }

    @Test
    void gstinsAreCheckedForStructureStateAndCheckCharacter() {
        assertThat(Gstin.problem("36AABCI1234A1ZC")).isNull();
        assertThat(Gstin.problem("29AAGCB7383J1Z4")).isNull();
        assertThat(Gstin.problem("27AAPFU0939F1ZV")).isNull();
        assertThat(Gstin.problem("36AABCI1234A1ZD")).contains("check character");
        assertThat(Gstin.problem("98AABCI1234A1ZC")).contains("isn't a state code");
        assertThat(Gstin.problem("36AABCI1234")).contains("15-character");
        assertThat(Gstin.normalize(" 36aabci1234a1zc ")).isEqualTo("36AABCI1234A1ZC");
        assertThat(Gstin.normalize("  ")).isNull();
        assertThat(GstState.byCode("25")).isEmpty();
        assertThat(GstState.byCode("38")).contains(GstState.LADAKH);
    }

    @Test
    void paymentStatusIsDerivedFromPaymentsAndTheDueDate() {
        LocalDate today = LocalDate.of(2026, 10, 26);
        BigDecimal total = new BigDecimal("1180.00");
        assertThat(InvoiceRules.paymentStatus(InvoiceStatus.DRAFT, total, BigDecimal.ZERO, null, today)).isNull();
        assertThat(InvoiceRules.paymentStatus(InvoiceStatus.CANCELLED, total, BigDecimal.ZERO, today, today))
                .isEqualTo(PaymentStatus.CANCELLED);
        assertThat(status(total, "0", today)).isEqualTo(PaymentStatus.UNPAID);
        assertThat(status(total, "0", today.minusDays(1))).isEqualTo(PaymentStatus.OVERDUE);
        assertThat(status(total, "500", today)).isEqualTo(PaymentStatus.PARTLY_PAID);
        assertThat(status(total, "500", today.minusDays(1))).isEqualTo(PaymentStatus.PARTLY_PAID_OVERDUE);
        // ₹0.50 tolerance (BR-V6), even when overdue.
        assertThat(status(total, "1179.50", today.minusDays(9))).isEqualTo(PaymentStatus.PAID);
        assertThat(status(total, "1179.49", today)).isEqualTo(PaymentStatus.PARTLY_PAID);
        assertThat(PaymentStatus.PARTLY_PAID.getLabel()).isEqualTo("Partially paid");
    }

    private static PaymentStatus status(BigDecimal total, String paid, LocalDate due) {
        return InvoiceRules.paymentStatus(InvoiceStatus.ISSUED, total, new BigDecimal(paid), due, LocalDate.of(2026, 10, 26));
    }
}

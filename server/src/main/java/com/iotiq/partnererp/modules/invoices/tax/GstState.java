package com.iotiq.partnererp.modules.invoices.tax;

import java.util.Arrays;
import java.util.Optional;

/**
 * States and union territories with their GST state codes (the first two digits of a GSTIN).
 *
 * <p>{@code utgst} marks the union territories without a legislature, where the intra-state levy is UTGST instead of
 * SGST (UTGST Act, s.2(114) CGST Act). Delhi, Puducherry and Jammu and Kashmir have legislatures and levy SGST.
 * Retired codes (25 Daman and Diu, merged into 26 in 2020; 28 the former Andhra Pradesh) and the non-state codes
 * (96 foreign country, 97 other territory, 99 centre jurisdiction) are not offered: supplies outside India are not
 * supported (DECISIONS.md P9-G08).
 */
public enum GstState {

    JAMMU_AND_KASHMIR("01", "Jammu and Kashmir", false),
    HIMACHAL_PRADESH("02", "Himachal Pradesh", false),
    PUNJAB("03", "Punjab", false),
    CHANDIGARH("04", "Chandigarh", true),
    UTTARAKHAND("05", "Uttarakhand", false),
    HARYANA("06", "Haryana", false),
    DELHI("07", "Delhi", false),
    RAJASTHAN("08", "Rajasthan", false),
    UTTAR_PRADESH("09", "Uttar Pradesh", false),
    BIHAR("10", "Bihar", false),
    SIKKIM("11", "Sikkim", false),
    ARUNACHAL_PRADESH("12", "Arunachal Pradesh", false),
    NAGALAND("13", "Nagaland", false),
    MANIPUR("14", "Manipur", false),
    MIZORAM("15", "Mizoram", false),
    TRIPURA("16", "Tripura", false),
    MEGHALAYA("17", "Meghalaya", false),
    ASSAM("18", "Assam", false),
    WEST_BENGAL("19", "West Bengal", false),
    JHARKHAND("20", "Jharkhand", false),
    ODISHA("21", "Odisha", false),
    CHHATTISGARH("22", "Chhattisgarh", false),
    MADHYA_PRADESH("23", "Madhya Pradesh", false),
    GUJARAT("24", "Gujarat", false),
    DADRA_NAGAR_HAVELI_DAMAN_DIU("26", "Dadra and Nagar Haveli and Daman and Diu", true),
    MAHARASHTRA("27", "Maharashtra", false),
    KARNATAKA("29", "Karnataka", false),
    GOA("30", "Goa", false),
    LAKSHADWEEP("31", "Lakshadweep", true),
    KERALA("32", "Kerala", false),
    TAMIL_NADU("33", "Tamil Nadu", false),
    PUDUCHERRY("34", "Puducherry", false),
    ANDAMAN_AND_NICOBAR("35", "Andaman and Nicobar Islands", true),
    TELANGANA("36", "Telangana", false),
    ANDHRA_PRADESH("37", "Andhra Pradesh", false),
    LADAKH("38", "Ladakh", true);

    private final String code;
    private final String displayName;
    private final boolean utgst;

    GstState(String code, String displayName, boolean utgst) {
        this.code = code;
        this.displayName = displayName;
        this.utgst = utgst;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Intra-state supplies here levy UTGST instead of SGST. */
    public boolean isUtgst() {
        return utgst;
    }

    public static Optional<GstState> byCode(String code) {
        return code == null ? Optional.empty() : Arrays.stream(values()).filter(s -> s.code.equals(code)).findFirst();
    }

    /** "36 · Telangana" */
    public String label() {
        return code + " · " + displayName;
    }
}

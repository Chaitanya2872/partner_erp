package com.iotiq.partnererp.common.constants;

import java.time.ZoneId;

public final class AppConstants {

    public static final String API_V1 = "/api/v1";

    /** Business dates (SLA, due dates, FY boundaries) are evaluated in this zone (DECISIONS D-24). */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 200;

    private AppConstants() {
    }
}

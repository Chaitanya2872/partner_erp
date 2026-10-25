package com.iotiq.partnererp.unit.common;

import com.iotiq.partnererp.common.exception.BusinessException;
import com.iotiq.partnererp.common.exception.GlobalExceptionHandler;
import com.iotiq.partnererp.common.exception.ResourceNotFoundException;
import com.iotiq.partnererp.common.web.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void validationErrorsReturnFieldListInEnvelope() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"qty\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.status").value(400))
                .andExpect(jsonPath("$.error.fieldErrors", hasSize(2)))
                .andExpect(jsonPath("$.error.requestId", not(emptyOrNullString())));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void validRequestReturnsSuccessEnvelopeAndRequestIdHeader() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"A\",\"qty\":2}"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("A x2"));
    }

    @Test
    void wellFormedIncomingRequestIdIsEchoedAndUnsafeOneReplaced() throws Exception {
        mvc.perform(get("/probe/missing").header("X-Request-Id", "abc-123"))
                .andExpect(header().string("X-Request-Id", "abc-123"))
                .andExpect(jsonPath("$.error.requestId").value("abc-123"));
        mvc.perform(get("/probe/missing").header("X-Request-Id", "bad id\r\ninjected"))
                .andExpect(header().string("X-Request-Id", not("bad id\r\ninjected")));
    }

    @Test
    void notFoundBusinessConflictAndUnexpectedMapToStatuses() throws Exception {
        mvc.perform(get("/probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(get("/probe/business"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("PAYOUT_DETAILS_MISSING"))
                .andExpect(jsonPath("$.error.detail").value("PAN and bank details are needed before the first payout"));
        mvc.perform(get("/probe/stale"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONCURRENT_MODIFICATION"));
        mvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                // internal exception text must never leak
                .andExpect(jsonPath("$.error.detail", not("secret internals")));
    }

    @Test
    void typeMismatchIsValidationError() throws Exception {
        mvc.perform(get("/probe/number/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("n"));
    }

    record ProbeRequest(@NotBlank String name, @Positive int qty) {
    }

    @RestController
    static class ProbeController {

        @PostMapping("/probe")
        com.iotiq.partnererp.common.response.ApiResponse<String> create(@Valid @RequestBody ProbeRequest body) {
            return com.iotiq.partnererp.common.response.ApiResponse.ok(body.name() + " x" + body.qty());
        }

        @GetMapping("/probe/missing")
        String missing() {
            throw new ResourceNotFoundException("Partner", "42");
        }

        @GetMapping("/probe/business")
        String business() {
            throw new BusinessException("PAYOUT_DETAILS_MISSING", "PAN and bank details are needed before the first payout");
        }

        @GetMapping("/probe/stale")
        String stale() {
            throw new OptimisticLockingFailureException("stale");
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("secret internals");
        }

        @GetMapping("/probe/number/{n}")
        String number(@PathVariable int n) {
            return String.valueOf(n);
        }
    }
}

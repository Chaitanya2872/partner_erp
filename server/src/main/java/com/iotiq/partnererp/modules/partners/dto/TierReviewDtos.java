package com.iotiq.partnererp.modules.partners.dto;

import com.iotiq.partnererp.common.enums.PartnerStatus;
import com.iotiq.partnererp.modules.partners.entity.TierReviewAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Tier review requests and results (BUSINESS_RULES §3.1). Review is manual: preview, then apply.
 */
public final class TierReviewDtos {

    private TierReviewDtos() {
    }

    /** @param partnerIds null or empty reviews every partner that is not Inactive */
    public record PreviewRequest(List<UUID> partnerIds) {
    }

    /**
     * One partner's proposed change.
     *
     * @param targetLevel the tier the partner qualifies for (programme → 4, else by revenue)
     */
    public record Item(UUID partnerId, String partnerName, PartnerStatus status, TierReviewAction action,
                       String actionLabel, int fromLevel, String fromTierName, int toLevel, String toTierName,
                       int targetLevel, BigDecimal revenue12m, boolean programme, boolean graceBefore,
                       boolean graceAfter) {
    }

    /**
     * @param reviewed  partners evaluated
     * @param changes   only the partners whose tier or grace flag would change
     */
    public record Preview(LocalDate reviewDate, int reviewed, List<Item> changes) {
    }

    /** The changes the user confirmed; each is recomputed and must still match. */
    public record ApplyRequest(@NotEmpty(message = "Choose at least one change to apply.") List<@Valid @NotNull ApplyItem> items) {
    }

    public record ApplyItem(@NotNull(message = "Partner is required.") UUID partnerId,
                            @NotNull(message = "Action is required.") TierReviewAction action,
                            @NotNull(message = "Target tier is required.") Integer toLevel) {
    }

    public record ApplyResult(LocalDate reviewDate, List<Item> applied) {
    }

    /** A row of {@code partner_tier_reviews}. */
    public record HistoryEntry(UUID id, TierReviewAction action, String actionLabel, int fromLevel, int toLevel,
                               BigDecimal revenue12m, boolean programme, UUID actorId, String actorName,
                               Instant reviewedAt) {
    }
}

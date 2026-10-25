package com.iotiq.partnererp.modules.partners.service;

import com.iotiq.partnererp.common.enums.PartnerStatus;
import com.iotiq.partnererp.common.exception.BusinessException;
import com.iotiq.partnererp.common.exception.ResourceNotFoundException;
import com.iotiq.partnererp.modules.audit.service.AuditActions;
import com.iotiq.partnererp.modules.audit.service.AuditService;
import com.iotiq.partnererp.modules.auth.entity.ModuleCode;
import com.iotiq.partnererp.modules.auth.service.DataScopeService;
import com.iotiq.partnererp.modules.partners.dto.TierReviewDtos.ApplyItem;
import com.iotiq.partnererp.modules.partners.dto.TierReviewDtos.ApplyResult;
import com.iotiq.partnererp.modules.partners.dto.TierReviewDtos.Item;
import com.iotiq.partnererp.modules.partners.dto.TierReviewDtos.Preview;
import com.iotiq.partnererp.modules.partners.entity.Partner;
import com.iotiq.partnererp.modules.partners.entity.PartnerTierReview;
import com.iotiq.partnererp.modules.partners.repository.PartnerMetricsRepository;
import com.iotiq.partnererp.modules.partners.repository.PartnerRepository;
import com.iotiq.partnererp.modules.partners.repository.PartnerTierReviewRepository;
import com.iotiq.partnererp.modules.settings.repository.PartnerTierRepository;
import com.iotiq.partnererp.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manual tier review (BUSINESS_RULES §3.1, D-07): a preview of what {@code reviewCp} would do, then an apply step
 * that recomputes every confirmed change under row locks and refuses if any no longer matches the preview.
 */
@Service
public class TierReviewService {

    private final PartnerRepository partnerRepository;
    private final PartnerTierRepository tierRepository;
    private final PartnerTierReviewRepository reviewRepository;
    private final PartnerMetricsRepository metrics;
    private final PartnerService partnerService;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;
    private final Clock clock;

    public TierReviewService(PartnerRepository partnerRepository, PartnerTierRepository tierRepository,
                             PartnerTierReviewRepository reviewRepository, PartnerMetricsRepository metrics,
                             PartnerService partnerService, DataScopeService dataScopeService,
                             AuditService auditService, Clock clock) {
        this.partnerRepository = partnerRepository;
        this.tierRepository = tierRepository;
        this.reviewRepository = reviewRepository;
        this.metrics = metrics;
        this.partnerService = partnerService;
        this.dataScopeService = dataScopeService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * @param partnerIds null or empty: every partner that is not Inactive ("Run tier review"); otherwise exactly
     *                   these partners ("Tier review" on one partner), whatever their status
     */
    @Transactional(readOnly = true)
    public Preview preview(List<UUID> partnerIds, AuthenticatedUser user) {
        List<Partner> partners;
        if (!dataScopeService.resolve(user, ModuleCode.PARTNERS).unrestricted()) {
            partners = List.of();
        } else if (partnerIds == null || partnerIds.isEmpty()) {
            partners = partnerRepository.findByStatusNotOrderByNameAsc(PartnerStatus.INACTIVE);
        } else {
            Set<UUID> wanted = new LinkedHashSet<>(partnerIds);
            partners = partnerRepository.findByIdInOrderByNameAsc(wanted);
            if (partners.size() != wanted.size()) {
                Set<UUID> found = partners.stream().map(Partner::getId).collect(Collectors.toSet());
                UUID missing = wanted.stream().filter(id -> !found.contains(id)).findFirst().orElseThrow();
                throw new ResourceNotFoundException("Partner", missing);
            }
        }
        List<Item> changes = evaluate(partners).stream().filter(Evaluated::changes).map(Evaluated::item).toList();
        return new Preview(LocalDate.now(clock), partners.size(), changes);
    }

    @Transactional
    public ApplyResult apply(List<ApplyItem> items, AuthenticatedUser user) {
        if (!dataScopeService.resolve(user, ModuleCode.PARTNERS).unrestricted()) {
            throw new ResourceNotFoundException("Partner", items.get(0).partnerId());
        }
        Map<UUID, ApplyItem> requested = items.stream()
                .collect(Collectors.toMap(ApplyItem::partnerId, Function.identity(), (a, b) -> {
                    throw BusinessException.conflict("DUPLICATE_PARTNER", "Each partner can appear only once.");
                }));
        List<Partner> partners = partnerRepository.lockAllById(requested.keySet());
        if (partners.size() != requested.size()) {
            Set<UUID> found = partners.stream().map(Partner::getId).collect(Collectors.toSet());
            UUID missing = requested.keySet().stream().filter(id -> !found.contains(id)).findFirst().orElseThrow();
            throw new ResourceNotFoundException("Partner", missing);
        }

        List<Evaluated> evaluated = evaluate(partners);
        for (Evaluated e : evaluated) {
            ApplyItem wanted = requested.get(e.partner().getId());
            if (!e.changes() || e.outcome().action() != wanted.action() || e.outcome().toLevel() != wanted.toLevel()) {
                throw BusinessException.conflict("TIER_REVIEW_STALE",
                        "The tier review for " + e.partner().getName() + " has changed since the preview. Preview again.");
            }
        }

        LocalDate today = LocalDate.now(clock);
        List<Item> applied = new ArrayList<>();
        for (Evaluated e : evaluated) {
            Partner partner = e.partner();
            TierRules.Outcome outcome = e.outcome();
            Map<String, Object> before = AuditService.values("tierLevel", outcome.fromLevel(),
                    "downgradeGrace", partner.isDowngradeGrace(), "lastTierReviewOn", partner.getLastTierReviewOn());
            if (outcome.toLevel() != outcome.fromLevel()) {
                partner.setTier(tierRepository.findByTierLevel((short) outcome.toLevel()).orElseThrow());
            }
            partner.setDowngradeGrace(outcome.graceAfter());
            partner.setLastTierReviewOn(today);
            partnerRepository.save(partner);

            PartnerTierReview review = new PartnerTierReview();
            review.setPartner(partner);
            review.setFromLevel((short) outcome.fromLevel());
            review.setToLevel((short) outcome.toLevel());
            review.setAction(outcome.action());
            review.setRevenue12m(e.revenue());
            review.setProgramme(partner.isStrategicProgramme());
            review.setActorId(user.id());
            reviewRepository.save(review);

            auditService.record(AuditActions.PARTNER_TIER_REVIEWED, AuditActions.ENTITY_PARTNER, partner.getId(), before,
                    AuditService.values("tierLevel", outcome.toLevel(), "downgradeGrace", outcome.graceAfter(),
                            "lastTierReviewOn", today, "action", outcome.action(), "revenue12m", e.revenue()));
            applied.add(e.item());
        }
        partnerRepository.flush();
        return new ApplyResult(today, applied);
    }

    private List<Evaluated> evaluate(Collection<Partner> partners) {
        PartnerService.TierTable tiers = partnerService.tierTable();
        Map<UUID, BigDecimal> revenue = metrics.paidRevenueSince(partners.stream().map(Partner::getId).toList(),
                partnerService.revenueSince());
        return partners.stream().map(p -> {
            BigDecimal rev = PartnerService.revenueOf(revenue, p.getId());
            int target = TierRules.calculatedLevel(tiers.thresholds(), rev, p.isStrategicProgramme());
            TierRules.Outcome outcome = TierRules.review(p.getTier().getTierLevel(), p.isDowngradeGrace(), target);
            Item item = new Item(p.getId(), p.getName(), p.getStatus(), outcome.action(),
                    outcome.action() == null ? "No change" : outcome.action().getLabel(), outcome.fromLevel(),
                    tiers.ref(outcome.fromLevel()).name(), outcome.toLevel(), tiers.ref(outcome.toLevel()).name(),
                    target, rev, p.isStrategicProgramme(), p.isDowngradeGrace(), outcome.graceAfter());
            return new Evaluated(p, outcome, rev, item);
        }).toList();
    }

    private record Evaluated(Partner partner, TierRules.Outcome outcome, BigDecimal revenue, Item item) {

        boolean changes() {
            return outcome.changes();
        }
    }
}

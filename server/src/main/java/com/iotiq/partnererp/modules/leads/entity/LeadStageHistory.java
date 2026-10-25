package com.iotiq.partnererp.modules.leads.entity;

import com.iotiq.partnererp.common.enums.LeadStage;
import com.iotiq.partnererp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Lead history entry: creation, stage changes and acknowledgement.
 */
@Entity
@Table(name = "lead_stage_history")
public class LeadStageHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", nullable = false, length = 20)
    private LeadEventType event;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_stage", length = 20)
    private LeadStage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_stage", length = 20)
    private LeadStage toStage;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public Lead getLead() {
        return lead;
    }

    public void setLead(Lead lead) {
        this.lead = lead;
    }

    public LeadEventType getEvent() {
        return event;
    }

    public void setEvent(LeadEventType event) {
        this.event = event;
    }

    public LeadStage getFromStage() {
        return fromStage;
    }

    public void setFromStage(LeadStage fromStage) {
        this.fromStage = fromStage;
    }

    public LeadStage getToStage() {
        return toStage;
    }

    public void setToStage(LeadStage toStage) {
        this.toStage = toStage;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}

package com.iotiq.partnererp.modules.estimates.entity;

import com.iotiq.partnererp.common.persistence.DocumentLine;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "estimate_items")
public class EstimateItem extends DocumentLine {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimate_id", nullable = false)
    private Estimate estimate;

    public Estimate getEstimate() {
        return estimate;
    }

    void setEstimate(Estimate estimate) {
        this.estimate = estimate;
    }
}

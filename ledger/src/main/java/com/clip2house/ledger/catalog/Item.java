package com.clip2house.ledger.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "items")
public class Item {

    public enum ValidationStatus { VALID, COHORT_INVALIDATED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_key", nullable = false, unique = true)
    private String nameKey;

    @Column(nullable = false)
    private String description;

    @Column(name = "ref_value", nullable = false, updatable = false)
    private BigDecimal refValue;

    @Column(name = "corrected_value")
    private BigDecimal correctedValue;

    @Column(name = "gen_version", nullable = false, updatable = false)
    private String genVersion;

    @Column(name = "gen_model", nullable = false, updatable = false)
    private String genModel;

    @Column(name = "gen_prompt_id", nullable = false, updatable = false)
    private String genPromptId;

    @Column(name = "cited_anchor_id", nullable = false, updatable = false)
    private Long citedAnchorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false)
    private ValidationStatus validationStatus = ValidationStatus.VALID;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected Item() {}

    public Item(String name, String nameKey, String description, BigDecimal refValue,
                String genVersion, String genModel, String genPromptId, Long citedAnchorId) {
        this.name = name;
        this.nameKey = nameKey;
        this.description = description;
        this.refValue = refValue;
        this.genVersion = genVersion;
        this.genModel = genModel;
        this.genPromptId = genPromptId;
        this.citedAnchorId = citedAnchorId;
    }

    public BigDecimal effectiveValue() {
        return correctedValue != null ? correctedValue : refValue;
    }

    public void correct(BigDecimal correctedValue) {
        this.correctedValue = correctedValue;
    }

    public void invalidateCohort() {
        this.validationStatus = ValidationStatus.COHORT_INVALIDATED;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getNameKey() { return nameKey; }
    public String getDescription() { return description; }
    public BigDecimal getRefValue() { return refValue; }
    public BigDecimal getCorrectedValue() { return correctedValue; }
    public String getGenVersion() { return genVersion; }
    public ValidationStatus getValidationStatus() { return validationStatus; }
}

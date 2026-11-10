package com.clip2house.ledger.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "golden_anchors")
public class GoldenAnchor {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "ref_value", nullable = false)
    private BigDecimal refValue;

    protected GoldenAnchor() {}

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getRefValue() { return refValue; }
}

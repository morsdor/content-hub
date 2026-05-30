package com.contenthub.analytics.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "usage_meter",
        uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "metric", "period"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageMeter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    private UUID workspaceId;

    @Column(nullable = false)
    private String metric;

    @Column(nullable = false)
    private LocalDate period;

    @Column(nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;
}

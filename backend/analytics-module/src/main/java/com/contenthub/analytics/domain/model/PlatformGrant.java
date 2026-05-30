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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_grant",
        uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "platform", "external_account_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    private UUID workspaceId;

    @Column(nullable = false)
    private String platform;

    @Column(name = "external_account_id", nullable = false)
    private String externalAccountId;

    @Column(name = "access_token_enc", nullable = false, columnDefinition = "bytea")
    private byte[] accessTokenEnc;

    @Column(name = "refresh_token_enc", columnDefinition = "bytea")
    private byte[] refreshTokenEnc;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", nullable = false)
    @Builder.Default
    private String[] scopes = new String[0];

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}

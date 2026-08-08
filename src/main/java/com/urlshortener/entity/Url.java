package com.urlshortener.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * JPA Entity representing the 'urls' table in PostgreSQL.
 * 
 * Annotations Breakdown:
 * @Entity: Tells JPA that this class is mapped to a database table.
 * @Table: Specifies table details such as table name and index constraints.
 * @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor, @Builder: Lombok annotations to avoid boilerplate code.
 */
@Entity
@Table(
    name = "urls",
    indexes = {
        @Index(name = "idx_urls_short_code", columnList = "short_code", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Url {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    @Column(name = "short_code", nullable = false, unique = true, length = 10)
    private String shortCode;

    @Column(name = "click_count", nullable = false)
    @Builder.Default
    private Long clickCount = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = true)
    private LocalDateTime expiresAt;

    /**
     * JPA Lifecycle Event Hook triggered before entity creation.
     * Ensures createdAt is set automatically and clickCount is defaulted to 0.
     */
    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.clickCount == null) {
            this.clickCount = 0L;
        }
    }
}

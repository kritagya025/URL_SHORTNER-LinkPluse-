package com.urlshortener.repository;

import com.urlshortener.entity.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA Repository for Url entity.
 * Provides out-of-the-box CRUD operations + custom derived query methods.
 */
@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {

    /**
     * Find a Url entity by its unique shortCode.
     * @param shortCode Generated short alphanumeric code (e.g. "aB72x")
     * @return Optional containing Url entity if found, empty Optional otherwise.
     */
    Optional<Url> findByShortCode(String shortCode);

    /**
     * Check if a shortCode already exists in the database.
     * Used during short code generation to handle potential collisions.
     * @param shortCode Short code to verify
     * @return true if shortCode exists, false otherwise.
     */
    boolean existsByShortCode(String shortCode);
}

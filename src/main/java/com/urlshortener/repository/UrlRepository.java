package com.urlshortener.repository;

import com.urlshortener.entity.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Atomically increments the click counter for a single link.
     *
     * <p>Performed as one UPDATE so that concurrent redirects to the same short code cannot
     * lose clicks. Reading the entity, incrementing in memory and saving it back would let two
     * simultaneous requests both read the same value and write the same result, dropping one
     * of the two clicks.
     *
     * @param id Primary key of the link that was visited
     */
    @Modifying
    @Query("UPDATE Url u SET u.clickCount = u.clickCount + 1 WHERE u.id = :id")
    void incrementClickCount(@Param("id") Long id);
}

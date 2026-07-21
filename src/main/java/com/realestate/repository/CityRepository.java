package com.realestate.repository;

import com.realestate.entity.City;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface CityRepository extends JpaRepository<City, UUID> {
    // Near-static: cities are Flyway-seeded, no admin mutation. Simple cache
    // clears on restart (which every migration triggers), so no evict needed.
    @Cacheable("cities")
    List<City> findByActiveTrueOrderByNameAsc();
    Optional<City> findBySlug(String slug);
}

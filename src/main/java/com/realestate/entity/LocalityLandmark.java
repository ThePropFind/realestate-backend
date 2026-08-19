package com.realestate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * A curated nearby place shown in the "Location & nearby places" section of the
 * property detail page.
 *
 * Replaces the hardcoded client table that used to live in the mobile app, so web
 * and mobile read the same data. Distances are human-readable LABELS, not numbers —
 * they are realistic but unsurveyed, and typing them as strings keeps anything
 * downstream from presenting them as precise measurements.
 *
 * A row with a null locality is part of the city-wide fallback set, served when a
 * listing's locality has no curated rows of its own.
 */
@Entity
@Table(name = "locality_landmarks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LocalityLandmark {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locality_id")
    private Locality locality;

    /**
     * Scopes the fallback. A landmark row always belongs to a city even when it
     * belongs to no particular locality — without this the fallback is global and
     * a Chennai listing gets served Coimbatore landmarks.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LandmarkKind kind;

    @Column(name = "distance_label", nullable = false, length = 20)
    private String distanceLabel;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private short sortOrder = 0;

    /** Drives the icon on the client. Closed set — mirrored by a CHECK constraint. */
    public enum LandmarkKind { HOSPITAL, SCHOOL, MALL, TRANSPORT, FOOD, PARK, TECH }
}

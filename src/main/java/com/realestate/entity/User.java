package com.realestate.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Maps to the `users` table.
 *
 * We deliberately do NOT implement UserDetails here — Spring Security
 * talks to UserPrincipal (a thin wrapper) instead, keeping this
 * class a plain JPA object with no framework coupling.
 */
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(unique = true, length = 15)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.BUYER;

    @Column(name = "profile_photo_url")
    private String profilePhotoUrl;

    @Column(name = "is_verified")
    @Builder.Default
    private boolean verified = false;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    // Temporary OTP — for email verification & password reset
    @Column(name = "otp_code", length = 10)
    private String otpCode;

    @Column(name = "otp_expires_at")
    private LocalDateTime otpExpiresAt;

    // Failed OTP guesses since the current OTP was issued — caps brute-force.
    @Column(name = "otp_attempts", nullable = false)
    @Builder.Default
    private int otpAttempts = 0;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * Send the "someone enquired about your listing" email? (V20)
     *
     * The only opt-out the app has: verification and password-reset OTPs are
     * security mail and are sent regardless. Defaults true — an account created
     * before this column existed was already receiving them.
     */
    @Column(name = "notify_email_inquiries", nullable = false)
    @Builder.Default
    private boolean notifyEmailInquiries = true;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Role enum ────────────────────────────────
    public enum Role {
        BUYER, SELLER, AGENT, ADMIN
    }
}

package com.realestate.service;

import com.realestate.dto.report.ReportDtos.ReportListingRequest;
import com.realestate.dto.report.ReportDtos.ReportListingResponse;
import com.realestate.entity.Property;
import com.realestate.entity.PropertyReport;
import com.realestate.entity.User;
import com.realestate.exception.BadRequestException;
import com.realestate.exception.ResourceNotFoundException;
import com.realestate.repository.PropertyReportRepository;
import com.realestate.repository.PropertyRepository;
import com.realestate.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins listing reports (B6).
 *
 * The guest path is the one that matters: requiring an account to flag a scam
 * listing would mean the listings most worth flagging get flagged least. So a
 * null reporter must be a first-class case, not an accident that NPEs.
 */
@ExtendWith(MockitoExtension.class)
class PropertyReportServiceTest {

    @Mock private PropertyReportRepository reportRepo;
    @Mock private PropertyRepository       propertyRepo;
    @Mock private UserRepository           userRepo;

    @InjectMocks private PropertyReportService reportService;

    private static final String REPORTER_EMAIL = "buyer@propfind.in";

    private final UUID propertyId = UUID.randomUUID();
    private final UUID reporterId = UUID.randomUUID();

    private Property listing(Property.ListingStatus status) {
        return Property.builder().id(propertyId).status(status).build();
    }

    private ReportListingRequest request(PropertyReport.Reason reason, String details) {
        ReportListingRequest req = new ReportListingRequest();
        req.setReason(reason);
        req.setDetails(details);
        return req;
    }

    /** save() returns its argument with an id, the way JPA would. */
    private void stubSave() {
        when(reportRepo.save(any(PropertyReport.class))).thenAnswer(inv -> {
            PropertyReport r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
    }

    private PropertyReport captureSaved() {
        ArgumentCaptor<PropertyReport> saved = ArgumentCaptor.forClass(PropertyReport.class);
        verify(reportRepo).save(saved.capture());
        return saved.getValue();
    }

    // ── Guest ────────────────────────────────────────────────────

    @Test
    void guestReportIsAcceptedWithANullReporter() {
        when(propertyRepo.findById(propertyId))
            .thenReturn(Optional.of(listing(Property.ListingStatus.ACTIVE)));
        stubSave();

        ReportListingResponse res = reportService.report(
            propertyId, request(PropertyReport.Reason.FRAUD_OR_SCAM, "Asked for an advance over UPI"), null);

        assertThat(res.getId()).isNotNull();
        assertThat(res.getReason()).isEqualTo("FRAUD_OR_SCAM");
        assertThat(res.getStatus()).isEqualTo("OPEN");

        PropertyReport saved = captureSaved();
        assertThat(saved.getReporter()).isNull();
        assertThat(saved.getDetails()).isEqualTo("Asked for an advance over UPI");
        // A guest report must not cost a user lookup at all.
        verify(userRepo, never()).findByEmail(any());
    }

    // ── Authenticated ────────────────────────────────────────────

    @Test
    void authenticatedReportRecordsTheReporter() {
        User reporter = User.builder().id(reporterId).email(REPORTER_EMAIL).build();
        when(propertyRepo.findById(propertyId))
            .thenReturn(Optional.of(listing(Property.ListingStatus.ACTIVE)));
        when(userRepo.findByEmail(REPORTER_EMAIL)).thenReturn(Optional.of(reporter));
        stubSave();

        reportService.report(
            propertyId, request(PropertyReport.Reason.ALREADY_SOLD_OR_RENTED, null), REPORTER_EMAIL);

        PropertyReport saved = captureSaved();
        assertThat(saved.getReporter()).isNotNull();
        assertThat(saved.getReporter().getId()).isEqualTo(reporterId);
        assertThat(saved.getStatus()).isEqualTo(PropertyReport.Status.OPEN);
    }

    @Test
    void blankDetailsAreStoredAsNullRatherThanWhitespace() {
        when(propertyRepo.findById(propertyId))
            .thenReturn(Optional.of(listing(Property.ListingStatus.ACTIVE)));
        stubSave();

        reportService.report(propertyId, request(PropertyReport.Reason.OTHER, "   "), null);

        assertThat(captureSaved().getDetails()).isNull();
    }

    // ── Guards ───────────────────────────────────────────────────

    @Test
    void reportingAnUnknownListingIs404() {
        when(propertyRepo.findById(propertyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.report(
                propertyId, request(PropertyReport.Reason.OTHER, null), null))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(reportRepo, never()).save(any());
    }

    @Test
    void nonActiveListingIsNotReportable() {
        // Otherwise the endpoint answers differently for a real DRAFT id than for a
        // made-up one, which makes it a probe for listings that are not public yet.
        when(propertyRepo.findById(propertyId))
            .thenReturn(Optional.of(listing(Property.ListingStatus.PENDING_REVIEW)));

        assertThatThrownBy(() -> reportService.report(
                propertyId, request(PropertyReport.Reason.OTHER, null), null))
            .isInstanceOf(BadRequestException.class);
        verify(reportRepo, never()).save(any());
    }

    @Test
    void missingReasonIsRejected() {
        when(propertyRepo.findById(propertyId))
            .thenReturn(Optional.of(listing(Property.ListingStatus.ACTIVE)));

        assertThatThrownBy(() -> reportService.report(propertyId, request(null, "something"), null))
            .isInstanceOf(BadRequestException.class);
        verify(reportRepo, never()).save(any());
    }

    @Test
    void anUnknownAuthenticatedEmailFallsBackToAGuestReport() {
        // Defensive: a token for a since-deleted account must still file the report
        // rather than 500 on the missing user.
        when(propertyRepo.findById(propertyId))
            .thenReturn(Optional.of(listing(Property.ListingStatus.ACTIVE)));
        when(userRepo.findByEmail(REPORTER_EMAIL)).thenReturn(Optional.empty());
        stubSave();

        reportService.report(
            propertyId, request(PropertyReport.Reason.INCORRECT_INFO, null), REPORTER_EMAIL);

        assertThat(captureSaved().getReporter()).isNull();
    }
}

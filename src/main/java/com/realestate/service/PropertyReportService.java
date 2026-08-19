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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Files buyer reports against a listing (B6).
 *
 * Guests may report — see V15 for why an account wall would be self-defeating here.
 * Volume is bounded by RateLimitFilter's 5/hr per-IP inquiry bucket, which
 * /properties/{id}/reports joins.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyReportService {

    private final PropertyReportRepository reportRepo;
    private final PropertyRepository       propertyRepo;
    private final UserRepository           userRepo;

    /**
     * @param userEmail the authenticated reporter, or null for a guest report.
     */
    @Transactional
    public ReportListingResponse report(UUID propertyId, ReportListingRequest req, String userEmail) {
        Property property = propertyRepo.findById(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("Property", propertyId));

        // Only a listing a buyer can actually see is reportable. Accepting reports on
        // DRAFT/PENDING ids would turn the endpoint into a probe for listings that
        // are not public yet.
        if (property.getStatus() != Property.ListingStatus.ACTIVE) {
            throw new BadRequestException("This listing is not available");
        }

        if (req.getReason() == null) {
            throw new BadRequestException("Select a reason for reporting this listing");
        }

        String details = req.getDetails() != null ? req.getDetails().trim() : null;
        if (details != null && details.isEmpty()) details = null;

        User reporter = userEmail != null ? userRepo.findByEmail(userEmail).orElse(null) : null;

        PropertyReport report = reportRepo.save(PropertyReport.builder()
            .property(property)
            .reporter(reporter)
            .reason(req.getReason())
            .details(details)
            .status(PropertyReport.Status.OPEN)
            .build());

        log.info("Listing reported: property={} reason={} by={}",
                 propertyId, req.getReason(), reporter != null ? reporter.getId() : "guest");

        return ReportListingResponse.builder()
            .id(report.getId())
            .reason(report.getReason().name())
            .status(report.getStatus().name())
            .createdAt(report.getCreatedAt())
            .build();
    }
}

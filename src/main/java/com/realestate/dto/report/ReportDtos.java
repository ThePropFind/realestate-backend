package com.realestate.dto.report;

import com.realestate.entity.PropertyReport;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

public class ReportDtos {

    @Data
    public static class ReportListingRequest {
        @NotNull(message = "Select a reason for reporting this listing")
        private PropertyReport.Reason reason;

        @Size(max = 1000, message = "Keep the details under 1000 characters")
        private String details;
    }

    /**
     * Acknowledgement only.
     *
     * SECURITY: carries no reporter identity and no moderation state beyond the
     * freshly-created OPEN. The endpoint is public, so anything richer would let an
     * anonymous caller read back who else flagged a listing — and a seller could
     * learn they had been reported, and by whom.
     */
    @Data @Builder
    public static class ReportListingResponse {
        private UUID   id;
        private String reason;
        private String status;
        private LocalDateTime createdAt;
    }
}

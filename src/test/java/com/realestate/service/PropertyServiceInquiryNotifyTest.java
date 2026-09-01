package com.realestate.service;

import com.realestate.entity.Property;
import com.realestate.entity.User;
import com.realestate.repository.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The one notification preference the Settings screen offers (V20).
 *
 * The count must still go up either way: an inquiry happened whether or not the
 * owner wants an email about it, and the dashboard's inquiryCount is a record of
 * demand, not a mail log.
 */
@ExtendWith(MockitoExtension.class)
class PropertyServiceInquiryNotifyTest {

    @Mock private PropertyRepository propertyRepository;
    @Mock private EmailService       emailService;

    @InjectMocks private PropertyService propertyService;

    private Property activeListingOwnedBy(User owner) {
        return Property.builder()
            .id(UUID.randomUUID())
            .title("2BHK near park")
            .status(Property.ListingStatus.ACTIVE)
            .owner(owner)
            .build();
    }

    private User owner(boolean notify) {
        return User.builder()
            .id(UUID.randomUUID())
            .name("Owner")
            .email("owner@propfind.in")
            .notifyEmailInquiries(notify)
            .build();
    }

    @Test
    void ownerOptedIn_getsTheEmail() {
        Property property = activeListingOwnedBy(owner(true));
        when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));

        propertyService.handleInquiry(property.getId(), "Is it still available?", "Buyer", "9876543210");

        verify(propertyRepository).incrementInquiryCount(property.getId());
        verify(emailService).sendInquiryNotification(
            anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void ownerOptedOut_getsNoEmailButTheInquiryStillCounts() {
        Property property = activeListingOwnedBy(owner(false));
        when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));

        propertyService.handleInquiry(property.getId(), "Is it still available?", "Buyer", "9876543210");

        verify(propertyRepository).incrementInquiryCount(property.getId());
        verify(emailService, never()).sendInquiryNotification(
            any(), any(), any(), any(), any());
    }
}

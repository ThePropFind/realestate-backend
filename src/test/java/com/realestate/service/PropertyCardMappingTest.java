package com.realestate.service;

import com.realestate.dto.property.PropertyDtos.PropertyCardResponse;
import com.realestate.entity.City;
import com.realestate.entity.Locality;
import com.realestate.entity.Property;
import com.realestate.entity.PropertyImage;
import com.realestate.entity.User;
import com.realestate.repository.PropertyDocumentRepository;
import com.realestate.repository.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the list-card projection, in particular {@code imageCount}.
 *
 * The mobile home screen renders an "N Photos" pill on the featured card, so the
 * count has to survive the entity → card mapping. It is derived from the same
 * already-loaded {@code images} collection that produces {@code primaryImageUrl},
 * so counting costs no extra query — a regression here would most likely be the
 * field silently dropping to 0, which the pill would render as a missing badge
 * rather than an error.
 */
@ExtendWith(MockitoExtension.class)
class PropertyCardMappingTest {

    @Mock private PropertyRepository         propertyRepository;
    @Mock private PropertyDocumentRepository documentRepository;
    @Mock private NearbyPlaceService         nearbyPlaceService;

    @InjectMocks private PropertyService propertyService;

    private Property listingWith(List<PropertyImage> images) {
        User owner = User.builder()
            .id(UUID.randomUUID())
            .name("Anitha R")
            .email("owner@propfind.in")
            .role(User.Role.SELLER)
            .build();

        City city = City.builder().id(UUID.randomUUID())
            .name("Coimbatore").slug("coimbatore").state("Tamil Nadu").build();
        Locality locality = Locality.builder()
            .id(UUID.randomUUID()).name("Saibaba Colony").slug("saibaba-colony").city(city).build();

        return Property.builder()
            .id(UUID.randomUUID())
            .owner(owner)
            .locality(locality)
            .title("Luxury Villa")
            .listingType(Property.ListingType.SALE)
            .propertyType(Property.PropertyType.VILLA)
            .status(Property.ListingStatus.ACTIVE)
            .price(new BigDecimal("12500000"))
            .priceUnit(Property.PriceUnit.TOTAL)
            .furnishing(Property.FurnishingStatus.FULLY_FURNISHED)
            .areaSqft(new BigDecimal("2400"))
            .images(images)
            .amenities(Set.of())
            .build();
    }

    private static PropertyImage image(String url, boolean primary) {
        return PropertyImage.builder().id(UUID.randomUUID()).url(url).isPrimary(primary).build();
    }

    @Test
    void card_countsEveryImageNotJustThePrimary() {
        Property p = listingWith(List.of(
            image("https://cdn/one.jpg", false),
            image("https://cdn/two.jpg", true),
            image("https://cdn/three.jpg", false)
        ));

        PropertyCardResponse card = propertyService.toCardResponse(p);

        assertThat(card.getImageCount()).isEqualTo(3);
        assertThat(card.getPrimaryImageUrl()).isEqualTo("https://cdn/two.jpg");
    }

    @Test
    void card_withNoImages_reportsZeroAndNullPrimary() {
        PropertyCardResponse card = propertyService.toCardResponse(listingWith(List.of()));

        assertThat(card.getImageCount()).isZero();
        assertThat(card.getPrimaryImageUrl()).isNull();
    }

    @Test
    void card_withNoPrimaryFlag_fallsBackToFirstImageAndStillCountsAll() {
        Property p = listingWith(List.of(
            image("https://cdn/one.jpg", false),
            image("https://cdn/two.jpg", false)
        ));

        PropertyCardResponse card = propertyService.toCardResponse(p);

        assertThat(card.getImageCount()).isEqualTo(2);
        assertThat(card.getPrimaryImageUrl()).isEqualTo("https://cdn/one.jpg");
    }
}

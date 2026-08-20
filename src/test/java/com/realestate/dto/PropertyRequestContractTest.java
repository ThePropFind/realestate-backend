package com.realestate.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realestate.dto.property.PropertyDtos.PropertyRequest;
import com.realestate.entity.Property;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract guards for the two PropertyRequest changes:
 *  - full address is now mandatory (item 2): blank addressLine must fail validation;
 *  - preferredTenant (item 5) must bind every allowed value and reject unknowns.
 */
class PropertyRequestContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static Validator validator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        return factory.getValidator();
    }

    /** A request that satisfies every constraint except the one under test. */
    private PropertyRequest validBase() {
        PropertyRequest req = new PropertyRequest();
        req.setTitle("2BHK near park");
        req.setListingType(Property.ListingType.RENT);
        req.setPropertyType(Property.PropertyType.APARTMENT);
        req.setLocalityId(UUID.randomUUID());
        req.setPrice(new BigDecimal("15000"));
        req.setAreaSqft(new BigDecimal("900"));
        req.setAddressLine("12 Main Road, RS Puram, Coimbatore");
        return req;
    }

    @Test
    void blankAddress_failsValidation() {
        PropertyRequest req = validBase();
        req.setAddressLine("   ");

        Set<ConstraintViolation<PropertyRequest>> violations = validator().validate(req);

        assertThat(violations)
            .anyMatch(v -> v.getPropertyPath().toString().equals("addressLine"));
    }

    @Test
    void fullAddress_passesValidation() {
        Set<ConstraintViolation<PropertyRequest>> violations = validator().validate(validBase());

        assertThat(violations)
            .noneMatch(v -> v.getPropertyPath().toString().equals("addressLine"));
    }

    @Test
    void pincode_sixDigits_passesValidation() {
        PropertyRequest req = validBase();
        req.setPincode("641012");

        assertThat(validator().validate(req))
            .noneMatch(v -> v.getPropertyPath().toString().equals("pincode"));
    }

    @Test
    void pincode_omitted_passesValidation() {
        assertThat(validator().validate(validBase()))
            .noneMatch(v -> v.getPropertyPath().toString().equals("pincode"));
    }

    /** Blank is a cleared form field, not a typo — the service normalises it to null. */
    @Test
    void pincode_blank_passesValidation() {
        PropertyRequest req = validBase();
        req.setPincode("");

        assertThat(validator().validate(req))
            .noneMatch(v -> v.getPropertyPath().toString().equals("pincode"));
    }

    @Test
    void pincode_leadingZero_isRejected() {
        PropertyRequest req = validBase();
        req.setPincode("041012");

        assertThat(validator().validate(req))
            .anyMatch(v -> v.getPropertyPath().toString().equals("pincode"));
    }

    @Test
    void pincode_wrongLength_isRejected() {
        for (String bad : new String[] { "12345", "6410123", "64101a" }) {
            PropertyRequest req = validBase();
            req.setPincode(bad);

            assertThat(validator().validate(req))
                .as("pincode %s", bad)
                .anyMatch(v -> v.getPropertyPath().toString().equals("pincode"));
        }
    }

    @Test
    void preferredTenant_allValues_areBindable() throws Exception {
        for (Property.PreferredTenant t : Property.PreferredTenant.values()) {
            PropertyRequest req = mapper.readValue(
                "{\"preferredTenant\":\"" + t.name() + "\"}", PropertyRequest.class);
            assertThat(req.getPreferredTenant()).isEqualTo(t);
        }
    }

    @Test
    void preferredTenant_unknown_isRejected() {
        assertThatThrownBy(() ->
            mapper.readValue("{\"preferredTenant\":\"PETS\"}", PropertyRequest.class))
            .isInstanceOf(com.fasterxml.jackson.databind.exc.InvalidFormatException.class);
    }
}

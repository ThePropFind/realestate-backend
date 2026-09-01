package com.realestate.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realestate.dto.property.PropertyDtos.PropertyDetailResponse;
import com.realestate.dto.property.PropertyDtos.PropertyRequest;
import com.realestate.entity.Property;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * plotUse must survive the round trip — regression #91.
 *
 * propertyType has a single PLOT value for residential, commercial and
 * industrial plots, so the wizard's distinction lived only in client state and
 * was never sent. Editing a listing therefore reopened every plot as
 * "Residential Plot". V21 persists it; these pin both ends of the wire.
 */
class PropertyRequestPlotUseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void requestBindsEveryPlotUse() throws Exception {
        for (Property.PlotUse use : Property.PlotUse.values()) {
            PropertyRequest req = mapper.readValue(
                "{\"plotUse\":\"" + use.name() + "\"}", PropertyRequest.class);
            assertThat(req.getPlotUse()).isEqualTo(use);
        }
    }

    /**
     * Null is a real, permanent value here: every non-plot listing, and every
     * plot posted before V21, has no plot use. Clients must render it as
     * "unspecified" rather than defaulting to RESIDENTIAL, which is precisely
     * the substitution #91 was about.
     */
    @Test
    void absentPlotUseStaysNull() throws Exception {
        PropertyRequest req = mapper.readValue("{\"title\":\"Plot near Pollachi\"}", PropertyRequest.class);
        assertThat(req.getPlotUse()).isNull();
    }

    @Test
    void unknownPlotUseIsRejected() {
        assertThatThrownBy(() ->
            mapper.readValue("{\"plotUse\":\"FARMHOUSE\"}", PropertyRequest.class))
            .isInstanceOf(Exception.class);
    }

    /** The detail response carries it back out as the enum's name, like every other enum here. */
    @Test
    void detailResponseSerialisesPlotUseAsAString() throws Exception {
        PropertyDetailResponse res = PropertyDetailResponse.builder()
            .plotUse(Property.PlotUse.COMMERCIAL.name())
            .build();

        assertThat(mapper.writeValueAsString(res)).contains("\"plotUse\":\"COMMERCIAL\"");
    }
}

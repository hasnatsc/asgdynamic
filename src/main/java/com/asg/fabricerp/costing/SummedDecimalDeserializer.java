package com.asg.fabricerp.costing;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * A consumption figure the upstream may send as a sum rather than a number.
 *
 * <p>On a Seersucker costing (two warp beams, up and low) {@code warpYarnConsumption} arrives as
 * {@code "0.0428 + 0.1069"} - one term per beam. The total is what the field means: on 04102503409
 * 0.0428 + 0.1069 plus weft 0.0824 is the 0.2321 the costing reports as {@code yarnWillConsumed}.
 * Anything that still is not a number binds to null rather than failing the whole costing.
 */
class SummedDecimalDeserializer extends JsonDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        return sum(p.getValueAsString());
    }

    static BigDecimal sum(String raw) {
        if (raw == null || raw.isBlank()) return null;
        BigDecimal total = BigDecimal.ZERO;
        for (String term : raw.split("\\+")) {
            if (term.isBlank()) continue;
            try {
                total = total.add(new BigDecimal(term.trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return total;
    }
}

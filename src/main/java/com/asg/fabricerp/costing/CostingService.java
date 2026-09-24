package com.asg.fabricerp.costing;

import com.asg.fabricerp.global.documents.FabricSpec;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Server-side wrapper for the external fabric costing system.
 *
 * <p>Upstream contract (verified 2026-09-24):
 * <pre>
 *   GET {baseUrl}/api/fabricsCost?user=...&amp;password=...&amp;code=...
 *   200 -&gt; {"status":"success","data":{ ...159 fields... }}
 *   400 -&gt; {"status":"error","data":"Something is wrong"}
 * </pre>
 *
 * <p>The upstream returns the SAME 400 body for unknown code, bad credentials and missing
 * params, so the cause cannot be distinguished. That ambiguity is surfaced honestly rather
 * than guessed at. Distinct 401/404 codes have been requested from the costing team.
 */
@Service
public class CostingService {

    private final WebClient client;
    private final CostingProperties props;
    private final ObjectMapper mapper;

    public CostingService(WebClient.Builder builder, CostingProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.client = builder.baseUrl(props.baseUrl()).build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Envelope(String status, FabricCost data) {}

    @Cacheable(cacheNames = "fabricCost", unless = "#result == null || !#result.isFinallyApproved()")
    public FabricCost fetch(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("costing code is required");
        }
        try {
            Envelope env = client.get()
                .uri(uri -> uri.path("/api/fabricsCost")
                    .queryParam("user", props.user())
                    .queryParam("password", props.password())
                    .queryParam("code", code)
                    .build())
                .retrieve()
                .bodyToMono(Envelope.class)
                .timeout(Duration.ofSeconds(15))
                .block();

            if (env == null || env.data() == null || !"success".equalsIgnoreCase(env.status())) {
                throw new CostingUnavailableException("Costing lookup failed for code " + code);
            }
            return env.data();

        } catch (WebClientResponseException.BadRequest e) {
            throw new CostingUnavailableException(
                ("Costing service rejected the request for code %s. The upstream returns an "
               + "identical error for unknown-code and authentication failure, so the cause "
               + "cannot be determined from the response.").formatted(code), e);
        } catch (CostingUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new CostingUnavailableException("Costing service unreachable for code " + code, e);
        }
    }

    /**
     * Stamps authoritative fabric figures onto a line.
     *
     * <p>Replaces asgdynamic's {@code onclick_so_dtlSet_fabricsCost},
     * {@code gsmCalculated} and {@code leadTime} handlers, which computed these in the
     * browser and posted the result.
     *
     * @return the break-even price per yard, for the caller to use as the line rate basis
     */
    public BigDecimal applyTo(FabricSpec spec) {
        FabricCost cost = fetch(spec.getCostingCode());
        spec.setGsm(cost.gsm());
        if (isBlank(spec.getConstruction()))          spec.setConstruction(cost.construction());
        if (isBlank(spec.getDeclaredConstruction()))  spec.setDeclaredConstruction(cost.declaredConstruction());
        if (isBlank(spec.getComposition()))           spec.setComposition(cost.composition());
        if (spec.getCuttableWidth() == null)          spec.setCuttableWidth(cost.cuttableWidth());
        return cost.breakEvenPriceYds();
    }

    /** Second-pass parse of the JSON-in-string composition payload. */
    public List<FabricCost.Composition> parseComposition(FabricCost cost) {
        String raw = cost.fabricCompositionRaw();
        if (raw == null || raw.isBlank()) return List.of();
        try {
            List<Map<String, Object>> rows = mapper.readValue(raw, new TypeReference<>() {});
            return rows.stream()
                .map(r -> new FabricCost.Composition(
                    str(r.get("count")), str(r.get("fiber")),
                    dec(r.get("blend")), dec(r.get("resultant"))))
                .toList();
        } catch (Exception e) {
            throw new CostingUnavailableException(
                "Malformed composition payload for " + cost.code(), e);
        }
    }

    /** Second-pass parse of the parallel colour/qty arrays. */
    @SuppressWarnings("unchecked")
    public List<FabricCost.ColourQty> parseColourQty(FabricCost cost) {
        String raw = cost.dyeingColorQtyRaw();
        if (raw == null || raw.isBlank()) return List.of();
        try {
            Map<String, Object> m = mapper.readValue(raw, new TypeReference<>() {});
            List<String> colours = (List<String>) m.getOrDefault("color", List.of());
            List<BigDecimal> qty = ((List<Object>) m.getOrDefault("qty", List.of()))
                .stream().map(CostingService::dec).toList();
            return FabricCost.zipColourQty(colours, qty);
        } catch (Exception e) {
            throw new CostingUnavailableException(
                "Malformed colour/qty payload for " + cost.code(), e);
        }
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private static BigDecimal dec(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

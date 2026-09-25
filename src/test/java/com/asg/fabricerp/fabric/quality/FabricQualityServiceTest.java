package com.asg.fabricerp.fabric.quality;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.fabric.quality.ConstructionYarn.Direction;
import com.asg.fabricerp.fabric.quality.FabricQualityService.ConstructionRequest;
import com.asg.fabricerp.fabric.quality.FabricQualityService.FibreRequest;
import com.asg.fabricerp.fabric.quality.FabricQualityService.YarnRequest;
import com.asg.fabricerp.fabric.setup.AttributeType;
import com.asg.fabricerp.fabric.setup.FabricAttribute;
import com.asg.fabricerp.fabric.setup.FabricAttributeRepository;
import com.asg.fabricerp.global.numbering.BusinessNumberService;
import com.asg.fabricerp.global.numbering.BusinessSeries;
import com.asg.fabricerp.inventory.item.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.asg.fabricerp.inventory.item.FiberType.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The rules a fabric quality is held to, and the notation derived from it. */
class FabricQualityServiceTest {

    private static final Long ORG = 1L;

    private ConstructionRepository constructions;
    private FabricAttributeRepository attributes;
    private YarnCountRepository counts;
    private FabricQualityService service;

    @BeforeEach
    void setUp() {
        constructions = mock(ConstructionRepository.class);
        attributes = mock(FabricAttributeRepository.class);
        counts = mock(YarnCountRepository.class);
        BusinessNumberService numbering = mock(BusinessNumberService.class);
        when(numbering.next(BusinessSeries.CONSTRUCTION)).thenReturn("CON-2026-000001");
        service = new FabricQualityService(constructions, attributes, counts, mock(YarnTypeRepository.class),
            mock(YarnBlendRepository.class), numbering, context());

        when(counts.findScoped(40L, ORG)).thenReturn(Optional.of(count(40L, "40")));
        when(counts.findScoped(20L, ORG)).thenReturn(Optional.of(count(20L, "20")));
        when(constructions.saveAndFlush(any())).thenAnswer(i -> {
            Construction c = i.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 9L);
            when(constructions.findScoped(9L, ORG)).thenReturn(Optional.of(c));
            return c;
        });
    }

    @Test
    void aQualityIsNumberedAndReadsAsTheTradeNotation() {
        Map<String, Object> saved = service.save(request(
            List.of(warp(40L), warp(20L), weft(40L)),
            List.of(fibre(VISCOSE, "30"), fibre(COTTON, "70"))));

        assertThat(saved.get("code")).isEqualTo("CON-2026-000001");
        assertThat(saved.get("notation")).isEqualTo("40+20x40/120x80, 58\"");
        assertThat(saved.get("composition")).isEqualTo("70% Cotton 30% Viscose");
    }

    @Test
    void aQualityNeedsBothDirectionsAndAtMostThreeYarnsEach() {
        assertThatThrownBy(() -> service.save(request(List.of(warp(40L)), List.of(fibre(COTTON, "100")))))
            .hasMessageContaining("at least one warp and one weft");
        assertThatThrownBy(() -> service.save(request(
                List.of(warp(40L), warp(40L), warp(40L), warp(40L), weft(40L)), List.of(fibre(COTTON, "100")))))
            .hasMessageContaining("At most 3 warp yarns");
    }

    @Test
    void theCompositionMustTotalExactlyOneHundred_withEachFibreOnce() {
        assertThatThrownBy(() -> FabricQualityService.fibres(List.of(fibre(COTTON, "70"), fibre(LINEN, "20"))))
            .hasMessageContaining("totals 90%");
        assertThatThrownBy(() -> FabricQualityService.fibres(List.of(fibre(COTTON, "50"), fibre(COTTON, "50"))))
            .hasMessageContaining("listed twice");
        assertThatThrownBy(() -> FabricQualityService.fibres(List.of()))
            .hasMessageContaining("at least one fibre");
        assertThat(FabricQualityService.fibres(List.of(fibre(COTTON, "97.5"), fibre(ELASTANE, "2.5")))).hasSize(2);
    }

    @Test
    void theCuttableWidthIsNeverWiderThanTheFinished() {
        ConstructionRequest r = request(List.of(warp(40L), weft(40L)), List.of(fibre(COTTON, "100")));
        ConstructionRequest wide = new ConstructionRequest(null, null, null, null, null, null,
            r.epi(), r.ppi(), null, null, new BigDecimal("58"), new BigDecimal("60"), null, null, true, r.yarns(), r.fibres());
        assertThatThrownBy(() -> service.save(wide)).hasMessageContaining("cuttable width");
    }

    @Test
    void aValueFromOneFabricListCannotBeFiledAsAnother() {
        FabricAttribute style = new FabricAttribute(AttributeType.WEAVE_STYLE, "HBT", "Herringbone");
        when(attributes.findScoped(5L, ORG)).thenReturn(Optional.of(style));
        ConstructionRequest r = request(List.of(warp(40L), weft(40L)), List.of(fibre(COTTON, "100")));
        ConstructionRequest misfiled = new ConstructionRequest(null, null, null, 5L, null, null,
            r.epi(), r.ppi(), null, null, null, null, null, null, true, r.yarns(), r.fibres());

        assertThatThrownBy(() -> service.save(misfiled)).hasMessageContaining("'Herringbone' is a Weave Style, not a Weave Type");
    }

    // ---------------------------------------------------------------------------- fixtures

    private static ConstructionRequest request(List<YarnRequest> yarns, List<FibreRequest> fibres) {
        return new ConstructionRequest(null, null, null, null, null, null,
            new BigDecimal("120"), new BigDecimal("80"), null, null, new BigDecimal("58.00"), null, null, null, true,
            yarns, fibres);
    }

    private static YarnRequest warp(Long countId) { return new YarnRequest(Direction.WARP, countId, null, null, BigDecimal.ONE, null); }
    private static YarnRequest weft(Long countId) { return new YarnRequest(Direction.WEFT, countId, null, null, BigDecimal.ONE, null); }
    private static FibreRequest fibre(FiberType type, String pct) { return new FibreRequest(type, new BigDecimal(pct)); }

    private static YarnCount count(Long id, String name) {
        YarnCount c = new YarnCount();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private static OrgContext context() {
        return new OrgContext() {
            @Override public Long organizationId()     { return ORG; }
            @Override public Long businessUnitId()     { return 10L; }
            @Override public String businessUnitCode() { return "AF"; }
            @Override public Long warehouseId()        { return 1L; }
            @Override public String username()         { return "tester"; }
            @Override public RowScope rowScope()       { return RowScope.unrestrictedScope(); }
        };
    }
}

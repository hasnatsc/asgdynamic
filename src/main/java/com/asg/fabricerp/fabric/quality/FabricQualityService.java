package com.asg.fabricerp.fabric.quality;

import com.asg.fabricerp.common.LookupPage;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.fabric.quality.ConstructionYarn.Direction;
import com.asg.fabricerp.fabric.setup.AttributeType;
import com.asg.fabricerp.fabric.setup.FabricAttribute;
import com.asg.fabricerp.fabric.setup.FabricAttributeRepository;
import com.asg.fabricerp.global.numbering.BusinessNumberService;
import com.asg.fabricerp.global.numbering.BusinessSeries;
import com.asg.fabricerp.inventory.item.FiberType;
import com.asg.fabricerp.inventory.item.YarnBlendRepository;
import com.asg.fabricerp.inventory.item.YarnCountRepository;
import com.asg.fabricerp.inventory.item.YarnTypeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Constructions: the fabric quality master a booking line will reference instead of re-keying
 * the legacy spec columns on every order.
 *
 * <h2>The rules a quality is held to</h2>
 * <ul>
 *   <li>At least one warp and one weft yarn, at most three each - the legacy grid's shape;
 *       every ratio positive.</li>
 *   <li>A composition whose fibres total exactly 100, each fibre once - so "70% Viscose 30%
 *       Linen" is data, not a string a screen has to split.</li>
 *   <li>EPI and PPI; widths optional, and the cuttable width never wider than the finished.</li>
 *   <li>Weave type, style and finish come from their own Fabric setup lists, and a value from
 *       one list cannot be filed as another.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class FabricQualityService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record YarnRequest(Direction direction, Long yarnCountId, Long yarnTypeId, Long yarnBlendId,
                              BigDecimal ratio, String remarks) { }

    public record FibreRequest(FiberType fiberType, BigDecimal percentage) { }

    public record ConstructionRequest(Long id, Long version, String name,
                                      Long weaveTypeId, Long weaveStyleId, Long finishTypeId,
                                      BigDecimal epi, BigDecimal ppi, BigDecimal reedCount,
                                      BigDecimal greigeWidth, BigDecimal finishWidth, BigDecimal cuttableWidth,
                                      BigDecimal gsm, String remarks, Boolean active,
                                      List<YarnRequest> yarns, List<FibreRequest> fibres) { }

    private final ConstructionRepository constructions;
    private final FabricAttributeRepository attributes;
    private final YarnCountRepository yarnCounts;
    private final YarnTypeRepository yarnTypes;
    private final YarnBlendRepository yarnBlends;
    private final BusinessNumberService numbering;
    private final OrgContext context;

    public FabricQualityService(ConstructionRepository constructions, FabricAttributeRepository attributes,
                                YarnCountRepository yarnCounts, YarnTypeRepository yarnTypes,
                                YarnBlendRepository yarnBlends, BusinessNumberService numbering, OrgContext context) {
        this.constructions = constructions;
        this.attributes = attributes;
        this.yarnCounts = yarnCounts;
        this.yarnTypes = yarnTypes;
        this.yarnBlends = yarnBlends;
        this.numbering = numbering;
        this.context = context;
    }

    private Long org() { return context.requireOrganizationId(); }

    // ================================================================================ reads

    public Page<Map<String, Object>> search(String q, boolean includeInactive, Pageable pageable) {
        return constructions.search(org(), LookupPage.like(q), includeInactive, pageable).map(FabricQualityService::summary);
    }

    public Map<String, Object> detail(Long id) {
        Construction c = get(id);
        Map<String, Object> row = summary(c);
        row.put("version", c.getVersion());
        row.put("weaveTypeId", idOf(c.getWeaveType()));
        row.put("weaveStyleId", idOf(c.getWeaveStyle()));
        row.put("finishTypeId", idOf(c.getFinishType()));
        row.put("weaveStyleName", nameOf(c.getWeaveStyle()));
        row.put("finishTypeName", nameOf(c.getFinishType()));
        row.put("epi", c.getEpi());
        row.put("ppi", c.getPpi());
        row.put("reedCount", c.getReedCount());
        row.put("greigeWidth", c.getGreigeWidth());
        row.put("finishWidth", c.getFinishWidth());
        row.put("cuttableWidth", c.getCuttableWidth());
        row.put("gsm", c.getGsm());
        row.put("remarks", c.getRemarks());
        row.put("updatedBy", c.getUpdatedBy() != null ? c.getUpdatedBy() : c.getCreatedBy());
        row.put("updatedAt", c.getUpdatedAt() != null ? c.getUpdatedAt() : c.getCreatedAt());
        row.put("yarns", c.getYarns().stream().map(y -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("direction", y.getDirection().name());
            m.put("sequence", y.getSequence());
            m.put("yarnCountId", y.getYarnCount().getId());
            m.put("yarnCountName", y.getYarnCount().getName());
            m.put("yarnTypeId", y.getYarnType() == null ? null : y.getYarnType().getId());
            m.put("yarnTypeName", y.getYarnType() == null ? null : y.getYarnType().getName());
            m.put("yarnBlendId", y.getYarnBlend() == null ? null : y.getYarnBlend().getId());
            m.put("yarnBlendName", y.getYarnBlend() == null ? null : y.getYarnBlend().getName());
            m.put("ratio", y.getRatio());
            m.put("remarks", y.getRemarks());
            return m;
        }).toList());
        row.put("fibres", c.getFibres().stream().map(f -> Map.<String, Object>of(
            "fiberType", f.getFiberType().name(), "fiberLabel", f.getFiberType().label(),
            "percentage", f.getPercentage())).toList());
        return row;
    }

    public Construction get(Long id) {
        return constructions.findScoped(id, org())
            .orElseThrow(() -> new IllegalArgumentException("Construction not found: " + id));
    }

    /** One page of active constructions for a picker: code or trade name. */
    public LookupPage<LookupPage.Option> lookup(String q, Integer page, Integer size) {
        return LookupPage.of(constructions.lookup(org(), LookupPage.like(q), LookupPage.pageable(page, size)),
            FabricQualityService::option);
    }

    public LookupPage<LookupPage.Option> option(Long id) {
        return LookupPage.single(option(get(id)));
    }

    /** "Denim 3/1 RHT" or the notation as the label; the notation, composition and weave beneath. */
    static LookupPage.Option option(Construction c) {
        String notation = c.notation();
        List<String> sub = new ArrayList<>();
        if (c.getName() != null && !notation.isEmpty()) sub.add(notation);
        if (!c.composition().isEmpty()) sub.add(c.composition());
        if (c.getWeaveType() != null) sub.add(c.getWeaveType().getName());
        return new LookupPage.Option(c.getId(), c.getCode(), c.getName() != null ? c.getName() : notation,
            String.join(" · ", sub));
    }

    // ================================================================================ writes

    @Transactional
    public Map<String, Object> save(ConstructionRequest r) {
        Construction c;
        if (r.id() == null) {
            c = new Construction(numbering.next(BusinessSeries.CONSTRUCTION));
            c.setOrganizationId(org());
        } else {
            c = get(r.id());
            if (r.version() != null && !r.version().equals(c.getVersion())) {
                throw new IllegalStateException("Someone else changed construction %s since you opened it. Reload and try again."
                    .formatted(c.getCode()));
            }
        }
        c.setName(clean(r.name(), 200));
        c.setWeaveType(attribute(r.weaveTypeId(), AttributeType.WEAVE_TYPE));
        c.setWeaveStyle(attribute(r.weaveStyleId(), AttributeType.WEAVE_STYLE));
        c.setFinishType(attribute(r.finishTypeId(), AttributeType.FINISH_TYPE));
        c.setEpi(positive(required(r.epi(), "EPI"), "EPI"));
        c.setPpi(positive(required(r.ppi(), "PPI"), "PPI"));
        c.setReedCount(positive(r.reedCount(), "Reed count"));
        c.setGreigeWidth(positive(r.greigeWidth(), "Greige width"));
        c.setFinishWidth(positive(r.finishWidth(), "Finished width"));
        c.setCuttableWidth(positive(r.cuttableWidth(), "Cuttable width"));
        if (c.getCuttableWidth() != null && c.getFinishWidth() != null
                && c.getCuttableWidth().compareTo(c.getFinishWidth()) > 0) {
            throw new IllegalArgumentException("The cuttable width cannot be wider than the finished width.");
        }
        c.setGsm(positive(r.gsm(), "GSM"));
        c.setRemarks(clean(r.remarks(), 1000));
        c.setActive(r.active() == null || r.active());
        c.replaceYarns(yarns(r.yarns()));
        c.replaceFibres(fibres(r.fibres()));
        return detail(constructions.saveAndFlush(c).getId());
    }

    /** Soft delete: the code stays spent, and a booking that later names it still resolves. */
    @Transactional
    public void delete(Long id) {
        Construction c = get(id);
        c.markDeleted();
        c.setActive(false);
    }

    private List<ConstructionYarn> yarns(List<YarnRequest> requested) {
        List<ConstructionYarn> rows = new ArrayList<>();
        Map<Direction, Integer> seen = new EnumMap<>(Direction.class);
        for (YarnRequest y : requested == null ? List.<YarnRequest>of() : requested) {
            if (y == null || y.direction() == null) throw new IllegalArgumentException("Say whether each yarn is warp or weft.");
            int sequence = seen.merge(y.direction(), 1, Integer::sum);
            String where = "%s yarn %d".formatted(y.direction() == Direction.WARP ? "Warp" : "Weft", sequence);
            if (sequence > ConstructionYarn.MAX_PER_DIRECTION) {
                throw new IllegalArgumentException("At most %d %s yarns.".formatted(
                    ConstructionYarn.MAX_PER_DIRECTION, y.direction().name().toLowerCase(Locale.ROOT)));
            }
            if (y.yarnCountId() == null) throw new IllegalArgumentException(where + ": choose its count.");
            rows.add(new ConstructionYarn(y.direction(), sequence,
                yarnCounts.findScoped(y.yarnCountId(), org())
                    .orElseThrow(() -> new IllegalArgumentException(where + ": unknown yarn count.")),
                y.yarnTypeId() == null ? null : yarnTypes.findScoped(y.yarnTypeId(), org())
                    .orElseThrow(() -> new IllegalArgumentException(where + ": unknown yarn type.")),
                y.yarnBlendId() == null ? null : yarnBlends.findScoped(y.yarnBlendId(), org())
                    .orElseThrow(() -> new IllegalArgumentException(where + ": unknown yarn blend.")),
                positive(y.ratio(), where + " ratio"), clean(y.remarks(), 300)));
        }
        if (!seen.containsKey(Direction.WARP) || !seen.containsKey(Direction.WEFT)) {
            throw new IllegalArgumentException("Give at least one warp and one weft yarn.");
        }
        return rows;
    }

    static List<ConstructionFibre> fibres(List<FibreRequest> requested) {
        List<ConstructionFibre> rows = new ArrayList<>();
        Set<FiberType> seen = EnumSet.noneOf(FiberType.class);
        BigDecimal total = BigDecimal.ZERO;
        for (FibreRequest f : requested == null ? List.<FibreRequest>of() : requested) {
            if (f == null || f.fiberType() == null) throw new IllegalArgumentException("Choose the fibre on every composition row.");
            if (!seen.add(f.fiberType())) {
                throw new IllegalArgumentException(f.fiberType().label() + " is listed twice in the composition.");
            }
            BigDecimal pct = required(f.percentage(), f.fiberType().label() + " %");
            if (pct.signum() <= 0 || pct.compareTo(HUNDRED) > 0) {
                throw new IllegalArgumentException(f.fiberType().label() + " must be more than 0% and at most 100%.");
            }
            total = total.add(pct);
            rows.add(new ConstructionFibre(f.fiberType(), pct));
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("Give the composition - at least one fibre.");
        if (total.compareTo(HUNDRED) != 0) {
            throw new IllegalArgumentException("The composition totals %s%%; it must total 100%%."
                .formatted(total.stripTrailingZeros().toPlainString()));
        }
        return rows;
    }

    // ================================================================================ helpers

    private static Map<String, Object> summary(Construction c) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", c.getId());
        row.put("code", c.getCode());
        row.put("name", c.getName());
        row.put("notation", c.notation());
        row.put("composition", c.composition());
        row.put("weaveTypeName", nameOf(c.getWeaveType()));
        row.put("active", c.getActive());
        return row;
    }

    private FabricAttribute attribute(Long id, AttributeType type) {
        if (id == null) return null;
        FabricAttribute a = attributes.findScoped(id, org())
            .orElseThrow(() -> new IllegalArgumentException(type.label() + " not found: " + id));
        if (a.getAttributeType() != type) {
            throw new IllegalArgumentException("'%s' is a %s, not a %s.".formatted(a.getName(),
                a.getAttributeType().label(), type.label()));
        }
        return a;
    }

    private static Long idOf(FabricAttribute a)     { return a == null ? null : a.getId(); }
    private static String nameOf(FabricAttribute a) { return a == null ? null : a.getName(); }

    private static <T> T required(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required.");
        return value;
    }

    private static BigDecimal positive(BigDecimal value, String field) {
        if (value != null && value.signum() <= 0) throw new IllegalArgumentException(field + " must be more than zero.");
        return value;
    }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException("Keep it to %d characters: %s…".formatted(max, trimmed.substring(0, 20)));
        }
        return trimmed;
    }
}

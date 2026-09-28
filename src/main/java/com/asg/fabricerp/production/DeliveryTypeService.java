package com.asg.fabricerp.production;

import com.asg.fabricerp.common.OrgContext;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Delivery types (Master data -> Delivery types): the code, name and sort order a production
 * order's pre-delivery schedule picks from. One a schedule has used is retired, not deleted, so the
 * order still reads.
 */
@Service
public class DeliveryTypeService {

    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_-]{1,20}$");

    public record Request(String code, String name, Integer sortOrder, Boolean active) { }

    private final DeliveryTypeRepository types;
    private final NamedParameterJdbcTemplate jdbc;
    private final OrgContext context;

    public DeliveryTypeService(DeliveryTypeRepository types, NamedParameterJdbcTemplate jdbc, OrgContext context) {
        this.types = types;
        this.jdbc = jdbc;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public List<DeliveryType> list() {
        return types.list(context.requireOrganizationId());
    }

    @Transactional
    public DeliveryType save(Long id, Request r) {
        Long org = context.requireOrganizationId();
        DeliveryType t = id == null ? new DeliveryType() : types.findScoped(id, org)
            .orElseThrow(() -> new IllegalArgumentException("Delivery type not found: " + id));
        String code = r.code() == null ? "" : r.code().strip().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("A code is up to 20 letters, digits, - or _");
        }
        if (types.codeTaken(org, code, id)) throw new IllegalArgumentException("Code " + code + " is already used");
        if (r.name() == null || r.name().isBlank()) throw new IllegalArgumentException("Give the delivery type's name");
        if (r.name().strip().length() > 100) throw new IllegalArgumentException("The name is longer than 100 characters");
        if (id == null) t.setOrganizationId(org);
        t.setCode(code);
        t.setName(r.name().strip());
        t.setSortOrder(r.sortOrder());
        if (r.active() != null) t.setActive(r.active());
        return types.save(t);
    }

    /** Deleted when no schedule has used it; otherwise retired. */
    @Transactional
    public String delete(Long id) {
        DeliveryType t = types.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Delivery type not found: " + id));
        Integer used = jdbc.queryForObject("SELECT count(*) FROM fab_bpo_pre_deliveries WHERE delivery_type_id = :id",
            new MapSqlParameterSource("id", id), Integer.class);
        if (used != null && used > 0) {
            t.setActive(false);
            types.save(t);
            return "retired";
        }
        t.markDeleted();
        types.save(t);
        return "deleted";
    }
}

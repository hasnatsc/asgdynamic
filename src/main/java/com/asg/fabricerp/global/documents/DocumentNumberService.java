package com.asg.fabricerp.global.documents;

import com.asg.fabricerp.common.OrgContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues document numbers in the legacy shape {@code {TYPE}{UNIT}{000000}} —
 * BPOAF000001, LCAF000002, MRRAF000014 — so numbers already in circulation keep resolving.
 *
 * <p>Single atomic upsert. The obvious alternative (SELECT ... FOR UPDATE, then INSERT or
 * UPDATE) races on the first use of a new key: two transactions both find no row and both
 * insert. {@code ON CONFLICT} closes that window.
 *
 * <p>{@code REQUIRES_NEW} so a rolled-back document does not roll the counter back and
 * hand the same number out twice. Gaps are acceptable; duplicates are not.
 */
@Service
public class DocumentNumberService {

    private static final String NEXT_VALUE_SQL = """
            INSERT INTO gbl_document_sequence (organization_id, seq_key, last_value)
            VALUES (:orgId, :key, 1)
            ON CONFLICT (organization_id, seq_key)
            DO UPDATE SET last_value = gbl_document_sequence.last_value + 1
            RETURNING last_value
            """;

    @PersistenceContext
    private EntityManager em;

    private final OrgContext context;

    public DocumentNumberService(OrgContext context) {
        this.context = context;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(DocumentType type) {
        return next(type, context.requireBusinessUnitCode(), context.requireOrganizationId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(DocumentType type, String businessUnitCode, Long organizationId) {
        String key = type.prefix() + businessUnitCode;

        Number nextValue = (Number) em.createNativeQuery(NEXT_VALUE_SQL)
            .setParameter("orgId", organizationId)
            .setParameter("key", key)
            .getSingleResult();

        return "%s%06d".formatted(key, nextValue.longValue());
    }

    /**
     * A master-data code on the same counter table: {@code prefix}, the caller's unit code, then
     * {@code width} digits - ITMAF000001, YTAF0001. The legacy masters were numbered this way
     * too (brand BRAF00001, category CAF110000). SpindleERP computes these as {@code MAX(code)+1}
     * inside a read-only transaction, which hands two concurrent callers the same code.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextCode(String prefix, int width) {
        String key = prefix + context.requireBusinessUnitCode();

        Number nextValue = (Number) em.createNativeQuery(NEXT_VALUE_SQL)
            .setParameter("orgId", context.requireOrganizationId())
            .setParameter("key", key)
            .getSingleResult();

        return key + String.format("%0" + width + "d", nextValue.longValue());
    }
}

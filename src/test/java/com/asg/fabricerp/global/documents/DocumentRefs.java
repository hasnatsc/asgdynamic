package com.asg.fabricerp.global.documents;

import com.asg.fabricerp.common.AuditableEntity;
import com.asg.fabricerp.common.BusinessUnit;
import com.asg.fabricerp.common.MarketingTeam;
import com.asg.fabricerp.common.Warehouse;
import com.asg.fabricerp.party.Party;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Id-only stand-ins for the associations {@link BusinessDocument} now holds - the same shape a
 * request body produces ({@code {"party": {"id": 42}}}). Every factory returns null for a null id.
 */
public final class DocumentRefs {

    private DocumentRefs() { }

    public static BusinessUnit unit(Long id) {
        return withId(id == null ? null : new BusinessUnit("U" + id, "Unit " + id), id);
    }

    public static Warehouse warehouse(Long id) {
        return withId(id == null ? null : new Warehouse("W" + id, "Warehouse " + id), id);
    }

    public static MarketingTeam team(Long id) {
        return withId(id == null ? null : new MarketingTeam("T" + id, "Team " + id), id);
    }

    public static Party party(Long id) {
        return withId(id == null ? null : new Party("P" + id, "Party " + id, Party.PartyType.ORGANISATION), id);
    }

    public static BusinessDocument document(Long id) {
        return withId(id == null ? null : new BusinessDocument(), id);
    }

    public static BusinessDocumentColorLine colorLine(Long id) {
        return withId(id == null ? null : new BusinessDocumentColorLine(), id);
    }

    public static Long id(AuditableEntity entity) {
        return AuditableEntity.idOf(entity);
    }

    /**
     * A {@link DocumentReferences} that stamps {@code unitId} as the operating unit, hands back
     * team stand-ins, and leaves submitted references as they are - the tests that use it are
     * about drawing and revising, not reference resolution.
     */
    public static DocumentReferences references(Long unitId) {
        DocumentReferences references = mock(DocumentReferences.class);
        when(references.currentBusinessUnit()).thenAnswer(i -> unit(unitId));
        when(references.marketingTeam(anyLong())).thenAnswer(i -> team(i.getArgument(0)));
        return references;
    }

    private static <T extends AuditableEntity> T withId(T entity, Long id) {
        if (entity != null) entity.setId(id);
        return entity;
    }
}

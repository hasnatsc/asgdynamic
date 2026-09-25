package com.asg.fabricerp.approval;

import com.asg.fabricerp.common.BaseOrgEntity;
import com.asg.fabricerp.global.documents.DocumentType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Who approves one document type in one business unit - asfl-erp's {@code core.approval_matrix}.
 *
 * <p>With a marketing team it is that team's own matrix (asfl-erp V27, team-wise); without one it
 * is the unit-wide matrix, governing every team that has none of its own and every unteamed
 * document. The engine prefers the narrower.
 *
 * <p>Levels are walked by position among those whose amount band covers the document, so a
 * booking of 5,000 may need one signature where one of 500,000 needs three.
 */
@Entity
@Table(name = "apr_matrices")
public class ApprovalMatrix extends BaseOrgEntity {

    @Column(name = "business_unit_id", nullable = false)
    private Long businessUnitId;

    /** Null for the unit-wide matrix. */
    @Column(name = "marketing_team_id")
    private Long marketingTeamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private DocumentType documentType;

    @Column(nullable = false, length = 150)
    private String name;

    @OneToMany(mappedBy = "matrix", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<ApprovalLevel> levels = new ArrayList<>();

    protected ApprovalMatrix() { }

    public ApprovalMatrix(Long organizationId, Long businessUnitId, Long marketingTeamId,
                          DocumentType documentType, String name) {
        setOrganizationId(organizationId);
        this.businessUnitId = businessUnitId;
        this.marketingTeamId = marketingTeamId;
        this.documentType = documentType;
        this.name = name;
    }

    public boolean isTeamWise() {
        return marketingTeamId != null;
    }

    /**
     * Makes the levels exactly {@code incoming}, numbered 1..n in the order given.
     *
     * <p>Existing rows are rewritten in place, new ones appended and the surplus removed from the
     * end - never cleared and re-added. Hibernate flushes inserts before orphan deletes, so a fresh
     * level 1 would meet the old level 1 on {@code uk_apr_level_sequence} before the old one left.
     */
    public void replaceLevels(List<ApprovalLevel> incoming) {
        levels.sort(Comparator.comparingInt(ApprovalLevel::getSequence));
        for (int i = 0; i < incoming.size(); i++) {
            if (i < levels.size()) {
                levels.get(i).copyFrom(incoming.get(i));
            } else {
                ApprovalLevel level = incoming.get(i);
                level.attach(this, i + 1);
                levels.add(level);
            }
        }
        while (levels.size() > incoming.size()) {
            levels.remove(levels.size() - 1);
        }
    }

    /** The levels an amount needs, in order. A null amount is covered by every level. */
    public List<ApprovalLevel> levelsFor(BigDecimal amount) {
        return levels.stream()
            .filter(l -> l.appliesTo(amount))
            .sorted(Comparator.comparingInt(ApprovalLevel::getSequence))
            .toList();
    }

    /** The {@code level}-th (1-based) of the levels the amount needs. */
    public Optional<ApprovalLevel> levelFor(BigDecimal amount, int level) {
        List<ApprovalLevel> applicable = levelsFor(amount);
        return level >= 1 && level <= applicable.size() ? Optional.of(applicable.get(level - 1)) : Optional.empty();
    }

    public Long getBusinessUnitId()      { return businessUnitId; }
    public Long getMarketingTeamId()     { return marketingTeamId; }
    public void setMarketingTeamId(Long v) { this.marketingTeamId = v; }
    public DocumentType getDocumentType() { return documentType; }
    public void setDocumentType(DocumentType v) { this.documentType = v; }
    public String getName()              { return name; }
    public void setName(String v)        { this.name = v; }
    public List<ApprovalLevel> getLevels() { return List.copyOf(levels); }
}

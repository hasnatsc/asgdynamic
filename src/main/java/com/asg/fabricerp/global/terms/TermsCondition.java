package com.asg.fabricerp.global.terms;

import com.asg.fabricerp.common.BaseOrgEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One standard clause. Those flagged {@link #getIsDefault() default} are copied onto every new
 * document of their {@link ConditionType}, in {@code sortOrder}, where the user may edit, drop
 * or add to them. Non-default clauses are a library to pick from.
 */
@Entity
@Table(
    name = "gbl_terms_conditions",
    indexes = @Index(name = "ix_gtc_org_type", columnList = "organization_id,condition_type,active"))
public class TermsCondition extends BaseOrgEntity {

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 30)
    private ConditionType conditionType;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String caption;

    @NotBlank
    @Size(max = 2000)
    @Column(name = "body_text", nullable = false, length = 2000)
    private String bodyText;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = Boolean.FALSE;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    public ConditionType getConditionType()       { return conditionType; }
    public void setConditionType(ConditionType v) { this.conditionType = v; }
    public String getCaption()                    { return caption; }
    public void setCaption(String v)              { this.caption = v == null ? null : v.trim(); }
    public String getBodyText()                   { return bodyText; }
    public void setBodyText(String v)             { this.bodyText = v == null ? null : v.trim(); }
    public Boolean getIsDefault()                 { return isDefault; }
    public void setIsDefault(Boolean v)           { this.isDefault = Boolean.TRUE.equals(v); }
    public Integer getSortOrder()                 { return sortOrder; }
    public void setSortOrder(Integer v)           { this.sortOrder = v == null ? 0 : v; }
}

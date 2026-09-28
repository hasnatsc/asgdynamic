package com.asg.fabricerp.production;

import com.asg.fabricerp.common.BaseOrgEntity;
import jakarta.persistence.*;

/** What a planned delivery is - PP submission, partial delivery, full delivery. */
@Entity
@Table(name = "fab_delivery_types")
public class DeliveryType extends BaseOrgEntity {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    public String getCode()              { return code; }
    public void setCode(String v)        { this.code = v; }
    public String getName()              { return name; }
    public void setName(String v)        { this.name = v; }
    public Integer getSortOrder()        { return sortOrder; }
    public void setSortOrder(Integer v)  { this.sortOrder = v == null ? 0 : v; }
}

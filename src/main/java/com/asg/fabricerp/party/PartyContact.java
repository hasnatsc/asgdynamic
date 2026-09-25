package com.asg.fabricerp.party;

import com.asg.fabricerp.common.BaseOrgLineEntity;
import jakarta.persistence.*;

/**
 * A person at a party. The legacy row held exactly one contact - one merchandiser per customer,
 * for a mill whose customers each have a merchandiser, a QA lead and an accounts contact.
 */
@Entity
@Table(
    name = "pty_party_contacts",
    indexes = @Index(name = "ix_pty_contact_party", columnList = "party_id"))
public class PartyContact extends BaseOrgLineEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false, updatable = false)
    private Party party;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "designation_code", length = 40)
    private String designationCode;

    @Column(length = 40)
    private String phone;

    @Column(length = 40)
    private String mobile;

    @Column(length = 200)
    private String email;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    protected PartyContact() { }

    PartyContact(Party party, String name, String designationCode) {
        this.party = party;
        this.name = name;
        this.designationCode = designationCode;
        setOrganizationId(party.getOrganizationId());
    }

    public PartyContact reachableAt(String phone, String mobile, String email) {
        this.phone = phone;
        this.mobile = mobile;
        this.email = email;
        return this;
    }

    void makePrimary() { this.primary = true; }

    public Party getParty()              { return party; }
    public String getName()              { return name; }
    public String getDesignationCode()   { return designationCode; }
    public String getPhone()             { return phone; }
    public String getMobile()            { return mobile; }
    public String getEmail()             { return email; }
    public boolean isPrimary()           { return primary; }
}

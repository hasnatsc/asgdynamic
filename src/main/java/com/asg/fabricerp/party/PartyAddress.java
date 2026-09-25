package com.asg.fabricerp.party;

import com.asg.fabricerp.common.BaseOrgLineEntity;
import jakarta.persistence.*;

/**
 * One address, typed by what it is for. The legacy row carried {@code address},
 * {@code shipping_address}, {@code billing_address} and {@code factory_address} as four columns,
 * so a customer with two delivery points had to pick one. Rows, so a party has as many as it has.
 *
 * <p>{@code geoCode} names a geography value by code, not by foreign key.
 */
@Entity
@Table(
    name = "pty_party_addresses",
    indexes = @Index(name = "ix_pty_address_party", columnList = "party_id"))
public class PartyAddress extends BaseOrgLineEntity {

    public enum AddressType { REGISTERED, BILLING, SHIPPING, FACTORY, WAREHOUSE }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false, updatable = false)
    private Party party;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 20)
    private AddressType type;

    @Column(nullable = false, length = 300)
    private String line1;

    @Column(length = 300)
    private String line2;

    @Column(name = "geo_code", length = 40)
    private String geoCode;

    @Column(length = 20)
    private String postcode;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    protected PartyAddress() { }

    PartyAddress(Party party, AddressType type, String line1, String geoCode) {
        this.party = party;
        this.type = type;
        this.line1 = line1;
        this.geoCode = geoCode;
        setOrganizationId(party.getOrganizationId());
    }

    public PartyAddress withLine2(String v)    { this.line2 = v; return this; }
    public PartyAddress withPostcode(String v) { this.postcode = v; return this; }
    void makePrimary()                         { this.primary = true; }
    void setPrimary(boolean v)                 { this.primary = v; }

    void update(AddressType newType, String newLine1, String newLine2, String newGeoCode, String newPostcode) {
        this.type = newType;
        this.line1 = newLine1;
        this.line2 = newLine2;
        this.geoCode = newGeoCode;
        this.postcode = newPostcode;
    }

    public Party getParty()        { return party; }
    public AddressType getType()   { return type; }
    public String getLine1()       { return line1; }
    public String getLine2()       { return line2; }
    public String getGeoCode()     { return geoCode; }
    public String getPostcode()    { return postcode; }
    public boolean isPrimary()     { return primary; }
}

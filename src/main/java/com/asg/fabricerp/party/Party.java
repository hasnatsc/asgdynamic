package com.asg.fabricerp.party;

import com.asg.fabricerp.common.BaseOrgEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A counterparty: one row per real company or person, whatever they are to us. Ported from
 * asfl-erp's {@code party.internal.Party}, onto this project's org-scoped {@link BaseOrgEntity}.
 *
 * <h2>The problem it fixes</h2>
 * The legacy system kept Marketing Customer, Commercial Customer, Supplier, Local Agent and Bank
 * as separate records, so a garment factory the mill both sells fabric to and buys waste from was
 * two unlinked rows: its address maintained twice, its exposure impossible to sum. Here there is
 * one {@code Party} and a set of {@link PartyRole} rows.
 *
 * <h2>What a party is not</h2>
 * <b>A party is who someone is, not what was agreed with them.</b> Terms, limits, prices and
 * letters of credit belong to the module that negotiates them - the legacy {@code stakeholder}
 * table carried an LC's number, value and tenure in the same row as the company's address.
 *
 * <h2>Columns</h2>
 * Identity (code, name, legal name, type); statutory identifiers (TIN, BIN, VAT registration)
 * as typed columns because compliance reporting filters on them; everything sparser - IRC, ERC,
 * bond licence, BTMA membership - in {@code attributes}.
 */
@Entity
@Table(
    name = "pty_parties",
    uniqueConstraints = @UniqueConstraint(name = "uk_pty_party_org_code", columnNames = {"organization_id", "code"}))
public class Party extends BaseOrgEntity {

    public enum PartyType { ORGANISATION, INDIVIDUAL }

    @Column(nullable = false, length = 40)
    private String code;

    /** The trading name, as it should appear on a document. */
    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, length = 20)
    private PartyType partyType = PartyType.ORGANISATION;

    /** The registered name, where it differs from the trading name. */
    @Column(name = "legal_name", length = 300)
    private String legalName;

    @Column(name = "country_code", length = 40)
    private String countryCode;

    @Column(length = 40)
    private String tin;

    @Column(length = 40)
    private String bin;

    @Column(name = "vat_reg_no", length = 40)
    private String vatRegNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", nullable = false)
    private Map<String, Object> attributes = new LinkedHashMap<>();

    @JsonIgnore
    @OneToMany(mappedBy = "party", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PartyRole> roles = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "party", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PartyAddress> addresses = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "party", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PartyContact> contacts = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "party", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PartyBankAccount> bankAccounts = new ArrayList<>();

    protected Party() { }

    public Party(String code, String name, PartyType partyType) {
        this.code = code;
        this.name = name;
        this.partyType = partyType;
    }

    // ---- Roles -----------------------------------------------------------------------------------

    /**
     * Grants a role, or restores one previously revoked. Re-granting restores rather than
     * duplicating: the same company coming back as a supplier after two years is the same
     * relationship resuming, and a second row would make "since when" unanswerable.
     *
     * @param qualifier required for {@link PartyRoleType#CUSTOMER}, forbidden otherwise
     * @param roleCode  the identifier the party trades under in this capacity; may be null
     */
    public PartyRole grantRole(PartyRoleType type, String qualifier, String roleCode, LocalDate on) {
        Optional<PartyRole> existing = roleRow(type, qualifier);
        if (existing.isPresent()) {
            existing.get().restore();
            return existing.get();
        }
        PartyRole role = new PartyRole(this, type, qualifier, roleCode, on);
        roles.add(role);
        return role;
    }

    /** For the unqualified roles - all of them but {@code CUSTOMER}. */
    public PartyRole grantRole(PartyRoleType type, LocalDate on) {
        return grantRole(type, null, null, on);
    }

    public void revokeRole(PartyRoleType type, String qualifier, LocalDate on) {
        roleRow(type, qualifier).ifPresent(role -> role.revoke(on));
    }

    public boolean holds(PartyRoleType type) {
        return roles.stream().anyMatch(r -> r.getRoleType() == type && r.isCurrent());
    }

    /** @throws PartyRoleNotHeldException when the party may not be named in this capacity */
    public void requireRole(PartyRoleType type) {
        if (!holds(type)) {
            throw new PartyRoleNotHeldException(code, type, activeRoles());
        }
    }

    public Set<PartyRoleType> activeRoles() {
        Set<PartyRoleType> held = EnumSet.noneOf(PartyRoleType.class);
        roles.stream().filter(PartyRole::isCurrent).forEach(r -> held.add(r.getRoleType()));
        return held;
    }

    public List<PartyRole> getRoles() {
        return List.copyOf(roles);
    }

    private Optional<PartyRole> roleRow(PartyRoleType type, String qualifier) {
        return roles.stream().filter(r -> r.matches(type, qualifier)).findFirst();
    }

    // ---- Addresses, contacts, bank accounts -----------------------------------------------------

    /** The first address of a type becomes that type's primary. */
    public PartyAddress addAddress(PartyAddress.AddressType type, String line1, String geoCode) {
        PartyAddress address = new PartyAddress(this, type, line1, geoCode);
        if (addresses.stream().noneMatch(a -> a.getType() == type)) {
            address.makePrimary();
        }
        addresses.add(address);
        return address;
    }

    public Optional<PartyAddress> primaryAddress(PartyAddress.AddressType type) {
        return addresses.stream().filter(a -> a.getType() == type && a.isPrimary()).findFirst();
    }

    public PartyContact addContact(String contactName, String designationCode) {
        PartyContact contact = new PartyContact(this, contactName, designationCode);
        if (contacts.isEmpty()) {
            contact.makePrimary();
        }
        contacts.add(contact);
        return contact;
    }

    public Optional<PartyContact> primaryContact() {
        return contacts.stream().filter(PartyContact::isPrimary).findFirst();
    }

    /**
     * An account this party holds at {@code bank} - itself a party holding {@link PartyRoleType#BANK}.
     * That is what removes the legacy schema's fifteen bank columns from every party row.
     *
     * @throws PartyRoleNotHeldException if {@code bank} does not hold {@code BANK}
     */
    public PartyBankAccount addBankAccount(Party bank, String accountName, String accountNumber) {
        bank.requireRole(PartyRoleType.BANK);
        if (bank == this) {
            throw new IllegalArgumentException("A party cannot hold an account at itself.");
        }
        PartyBankAccount account = new PartyBankAccount(this, bank, accountName, accountNumber);
        if (bankAccounts.isEmpty()) {
            account.makePrimary();
        }
        bankAccounts.add(account);
        return account;
    }

    public Optional<PartyBankAccount> primaryBankAccount() {
        return bankAccounts.stream().filter(PartyBankAccount::isPrimary).findFirst();
    }

    public List<PartyAddress> getAddresses()          { return List.copyOf(addresses); }
    public List<PartyContact> getContacts()           { return List.copyOf(contacts); }
    public List<PartyBankAccount> getBankAccounts()   { return List.copyOf(bankAccounts); }

    // ---- Identity --------------------------------------------------------------------------------

    public void identify(String legalName, String countryCode) {
        this.legalName = legalName;
        this.countryCode = countryCode;
    }

    public void registerFor(String tin, String bin, String vatRegNo) {
        this.tin = tin;
        this.bin = bin;
        this.vatRegNo = vatRegNo;
    }

    public void renameTo(String newName)               { this.name = newName; }
    public void setAttribute(String key, Object value) { attributes.put(key, value); }

    public String getCode()                    { return code; }
    public String getName()                    { return name; }
    public PartyType getPartyType()            { return partyType; }
    public String getLegalName()               { return legalName; }
    public String getCountryCode()             { return countryCode; }
    public String getTin()                     { return tin; }
    public String getBin()                     { return bin; }
    public String getVatRegNo()                { return vatRegNo; }
    public Map<String, Object> getAttributes() { return Map.copyOf(attributes); }
}

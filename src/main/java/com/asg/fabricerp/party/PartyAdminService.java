package com.asg.fabricerp.party;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.numbering.BusinessNumberService;
import com.asg.fabricerp.global.numbering.BusinessSeries;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Party maintenance - the screen asfl-erp's module never had. Everything the directory grid and
 * the editor need, and the rules that keep a party's record honest:
 *
 * <ul>
 *   <li><b>Roles are revoked, not erased.</b> Unticking Supplier marks that role row revoked
 *       today; ticking it again later restores the same row, so "supplier since when" survives.</li>
 *   <li><b>Child rows are updated in place.</b> Addresses, contacts and bank accounts the editor
 *       still lists keep their ids; only the rows it dropped are removed.</li>
 *   <li><b>Exactly one primary</b> per address type, among contacts, and among bank accounts -
 *       the first listed when the editor marked none.</li>
 *   <li><b>A party still in use is not deleted.</b> Documents naming it, or other parties'
 *       accounts held at it as their bank, block deletion; deactivating is the alternative.</li>
 *   <li><b>Two people editing the same party</b> do not silently overwrite each other: the
 *       editor sends the version it loaded, and a stale one is refused.</li>
 * </ul>
 */
@Service
public class PartyAdminService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public record RoleRequest(PartyRoleType roleType, String qualifier, String roleCode) { }

    public record AddressRequest(Long id, PartyAddress.AddressType type, String line1, String line2,
                                 String geoCode, String postcode, boolean primary) { }

    public record ContactRequest(Long id, String name, String designationCode, String phone, String mobile,
                                 String email, boolean primary) { }

    public record BankAccountRequest(Long id, Long bankId, String accountName, String accountNumber,
                                     String branchName, String routingNumber, String swiftCode,
                                     String currencyCode, boolean primary) { }

    public record PartyRequest(Long id, Long version, String code, String name, Party.PartyType partyType,
                               String legalName, String countryCode, String tin, String bin, String vatRegNo,
                               Boolean active, List<RoleRequest> roles, List<AddressRequest> addresses,
                               List<ContactRequest> contacts, List<BankAccountRequest> bankAccounts,
                               Map<String, String> attributes) { }

    /** The directory's filter chips. */
    public record Counts(long total, long inactive, Map<PartyRoleType, Long> byRole) { }

    private final PartyRepository parties;
    private final BusinessNumberService numbering;
    private final OrgContext context;

    public PartyAdminService(PartyRepository parties, BusinessNumberService numbering, OrgContext context) {
        this.parties = parties;
        this.numbering = numbering;
        this.context = context;
    }

    // ---- Directory ---------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> search(PartyRoleType role, Boolean active, Party.PartyType type,
                                            String query, Pageable pageable) {
        String term = clean(query);
        Page<Party> page = parties.adminSearch(context.requireOrganizationId(), role, active, type,
            term == null ? null : "%" + term.toLowerCase(Locale.ROOT) + "%", pageable);
        return enrich(page);
    }

    @Transactional(readOnly = true)
    public Counts counts() {
        Long orgId = context.requireOrganizationId();
        Map<PartyRoleType, Long> byRole = new EnumMap<>(PartyRoleType.class);
        for (Object[] row : parties.countByRole(orgId)) {
            byRole.put((PartyRoleType) row[0], ((Number) row[1]).longValue());
        }
        Object[] totals = parties.countTotals(orgId).getFirst();
        return new Counts(((Number) totals[0]).longValue(), ((Number) totals[1]).longValue(), byRole);
    }

    /**
     * A page of parties as grid rows. Roles, primary contact and primary address come from three
     * batched queries over the page's ids - never one query per row.
     */
    private Page<Map<String, Object>> enrich(Page<Party> page) {
        List<Long> ids = page.getContent().stream().map(Party::getId).toList();
        if (ids.isEmpty()) return page.map(PartyAdminService::gridRow);

        Map<Long, List<PartyRole>> roles = parties.currentRolesOf(ids).stream()
            .collect(Collectors.groupingBy(r -> r.getParty().getId()));
        Map<Long, PartyContact> contacts = parties.primaryContactsOf(ids).stream()
            .collect(Collectors.toMap(c -> c.getParty().getId(), Function.identity(), (a, b) -> a));
        Map<Long, PartyAddress> addresses = parties.primaryAddressesOf(ids).stream()
            .collect(Collectors.toMap(a -> a.getParty().getId(), Function.identity(), PartyAdminService::preferredAddress));

        return page.map(p -> {
            Map<String, Object> row = gridRow(p);
            row.put("roles", roles.getOrDefault(p.getId(), List.of()).stream().map(PartyAdminService::roleLabel).toList());
            PartyContact contact = contacts.get(p.getId());
            row.put("contactName", contact == null ? null : contact.getName());
            row.put("contactPhone", contact == null ? null : firstNonBlank(contact.getMobile(), contact.getPhone()));
            row.put("contactEmail", contact == null ? null : contact.getEmail());
            PartyAddress address = addresses.get(p.getId());
            row.put("location", address == null ? null
                : address.getGeoCode() == null ? address.getLine1() : address.getLine1() + ", " + address.getGeoCode());
            return row;
        });
    }

    /** The address a list shows: registered, then billing, then whichever came first. */
    private static PartyAddress preferredAddress(PartyAddress a, PartyAddress b) {
        return rank(b.getType()) < rank(a.getType()) ? b : a;
    }

    private static int rank(PartyAddress.AddressType type) {
        return switch (type) {
            case REGISTERED -> 0;
            case BILLING -> 1;
            default -> 2;
        };
    }

    private static Map<String, Object> gridRow(Party p) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", p.getId());
        row.put("code", p.getCode());
        row.put("name", p.getName());
        row.put("legalName", p.getLegalName());
        row.put("partyType", p.getPartyType());
        row.put("tin", p.getTin());
        row.put("bin", p.getBin());
        row.put("active", p.getActive());
        return row;
    }

    // ---- Detail ------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        Party p = get(id);
        Map<String, Object> row = gridRow(p);
        row.put("version", p.getVersion());
        row.put("countryCode", p.getCountryCode());
        row.put("vatRegNo", p.getVatRegNo());
        row.put("attributes", p.getAttributes());
        row.put("createdBy", p.getCreatedBy());
        row.put("createdAt", p.getCreatedAt());
        row.put("updatedBy", p.getUpdatedBy());
        row.put("updatedAt", p.getUpdatedAt());

        row.put("roles", p.getRoles().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("roleType", r.getRoleType());
            m.put("qualifier", r.getQualifier());
            m.put("roleCode", r.getRoleCode());
            m.put("grantedOn", r.getGrantedOn());
            m.put("revokedOn", r.getRevokedOn());
            m.put("current", r.isCurrent());
            return m;
        }).toList());
        row.put("addresses", p.getAddresses().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("type", a.getType());
            m.put("line1", a.getLine1());
            m.put("line2", a.getLine2());
            m.put("geoCode", a.getGeoCode());
            m.put("postcode", a.getPostcode());
            m.put("primary", a.isPrimary());
            return m;
        }).toList());
        row.put("contacts", p.getContacts().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("designationCode", c.getDesignationCode());
            m.put("phone", c.getPhone());
            m.put("mobile", c.getMobile());
            m.put("email", c.getEmail());
            m.put("primary", c.isPrimary());
            return m;
        }).toList());
        row.put("bankAccounts", p.getBankAccounts().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("bankId", a.getBank().getId());
            m.put("bankCode", a.getBank().getCode());
            m.put("bankName", a.getBank().getName());
            m.put("accountName", a.getAccountName());
            m.put("accountNumber", a.getAccountNumber());
            m.put("branchName", a.getBranchName());
            m.put("routingNumber", a.getRoutingNumber());
            m.put("swiftCode", a.getSwiftCode());
            m.put("currencyCode", a.getCurrencyCode());
            m.put("primary", a.isPrimary());
            return m;
        }).toList());
        row.put("usage", Map.of(
            "documents", parties.documentsNaming(id),
            "accountsHeldHere", parties.accountsHeldAt(id)));
        return row;
    }

    @Transactional(readOnly = true)
    public Party get(Long id) {
        return parties.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Party not found: " + id));
    }

    // ---- Save --------------------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> save(PartyRequest r) {
        Long orgId = context.requireOrganizationId();
        String name = required(r.name(), "Trading name", 200);
        Party.PartyType type = r.partyType() == null ? Party.PartyType.ORGANISATION : r.partyType();

        Party party;
        String typedCode = null;
        if (r.id() == null) {
            String code = clean(r.code());
            if (code == null) {
                code = numbering.next(BusinessSeries.PARTY);
            } else {
                code = max(code.toUpperCase(Locale.ROOT), "Code", 40);
                if (parties.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(orgId, code)) {
                    throw new IllegalArgumentException("Party code '%s' is already in use.".formatted(code));
                }
                typedCode = code;
            }
            party = new Party(code, name, type);
            party.setOrganizationId(orgId);
        } else {
            party = get(r.id());
            if (r.version() != null && !r.version().equals(party.getVersion())) {
                throw new ObjectOptimisticLockingFailureException(Party.class, r.id());
            }
            party.renameTo(name);
            party.setPartyType(type);
        }

        party.identify(max(clean(r.legalName()), "Legal name", 300), max(clean(r.countryCode()), "Country", 40));
        party.registerFor(max(clean(r.tin()), "TIN", 40), max(clean(r.bin()), "BIN", 40),
            max(clean(r.vatRegNo()), "VAT registration", 40));
        party.setActive(r.active() == null || r.active());
        party.replaceAttributes(attributes(r.attributes()));

        syncRoles(party, r.roles() == null ? List.of() : r.roles());
        syncAddresses(party, r.addresses() == null ? List.of() : r.addresses());
        syncContacts(party, r.contacts() == null ? List.of() : r.contacts());
        syncBankAccounts(orgId, party, r.bankAccounts() == null ? List.of() : r.bankAccounts());

        // Last, after syncRoles has drawn any role codes: the reservation is an uncommitted ledger row
        // until this save commits, and a code drawn after it that happened to equal it would wait on
        // this very transaction.
        if (typedCode != null) {
            numbering.reserve(BusinessSeries.PARTY, typedCode);
        }

        Party saved = parties.save(party);
        parties.flush();   // so the returned version and child ids are the persisted ones
        return detail(saved.getId());
    }

    /** Grants what the editor ticked, revokes what it unticked - history kept either way. */
    private void syncRoles(Party party, List<RoleRequest> requested) {
        LocalDate today = LocalDate.now();
        Set<String> wanted = new HashSet<>();
        for (RoleRequest role : requested) {
            if (role.roleType() == null) continue;
            String qualifier = role.roleType().isQualified() ? clean(role.qualifier()) : null;
            if (!wanted.add(role.roleType() + "/" + qualifier)) {
                throw new IllegalArgumentException("%s is listed twice.".formatted(label(role.roleType(), qualifier)));
            }
            PartyRole granted = party.grantRole(role.roleType(), qualifier, null, today);
            String typed = max(clean(role.roleCode()), "Role code", 40);
            Optional<BusinessSeries> series = BusinessSeries.forRole(role.roleType());
            if (typed != null || series.isEmpty()) {
                granted.setRoleCode(typed);
            } else if (granted.getRoleCode() == null) {
                granted.setRoleCode(siblingCode(party, granted).orElseGet(() -> numbering.next(series.get())));
            }
            // else: a numbered role keeps the code it was issued; leaving the field blank does not erase it.
        }
        if (wanted.isEmpty()) {
            throw new IllegalArgumentException("Give the party at least one role - a party with none cannot be named on any document.");
        }
        for (PartyRole existing : party.getRoles()) {
            if (existing.isCurrent() && !wanted.contains(existing.getRoleType() + "/" + existing.getQualifier())) {
                if (existing.getRoleType() == PartyRoleType.BANK && party.getId() != null
                        && parties.accountsHeldAt(party.getId()) > 0) {
                    throw new IllegalStateException(
                        "Other parties hold accounts at this bank, so it must keep the Bank role.");
                }
                party.revokeRole(existing.getRoleType(), existing.getQualifier(), today);
            }
        }
    }

    /**
     * A company that is both a marketing and a commercial customer is one customer with one code:
     * the qualifier says which side deals with it, not who it is.
     */
    private static Optional<String> siblingCode(Party party, PartyRole role) {
        return party.getRoles().stream()
            .filter(other -> other != role && other.getRoleType() == role.getRoleType() && other.getRoleCode() != null)
            .map(PartyRole::getRoleCode)
            .findFirst();
    }

    private void syncAddresses(Party party, List<AddressRequest> requested) {
        Map<Long, PartyAddress> existing = byId(party.getAddresses(), PartyAddress::getId);
        List<PartyAddress> kept = new ArrayList<>();
        List<Boolean> primaries = new ArrayList<>();
        for (AddressRequest a : requested) {
            PartyAddress.AddressType type = Objects.requireNonNullElse(a.type(), PartyAddress.AddressType.REGISTERED);
            String line1 = required(a.line1(), "Address line 1", 300);
            PartyAddress row = a.id() == null ? party.addAddress(type, line1, null) : existingRow(existing, a.id(), "Address");
            row.update(type, line1, max(clean(a.line2()), "Address line 2", 300),
                max(clean(a.geoCode()), "Area / city", 40), max(clean(a.postcode()), "Postcode", 20));
            kept.add(row);
            primaries.add(a.primary());
        }
        existing.values().stream().filter(row -> !kept.contains(row)).forEach(party::removeAddress);
        // One primary per address type.
        Map<PartyAddress.AddressType, List<Integer>> byType = new EnumMap<>(PartyAddress.AddressType.class);
        for (int i = 0; i < kept.size(); i++) byType.computeIfAbsent(kept.get(i).getType(), t -> new ArrayList<>()).add(i);
        byType.values().forEach(indexes -> {
            int chosen = indexes.stream().filter(primaries::get).findFirst().orElse(indexes.getFirst());
            indexes.forEach(i -> kept.get(i).setPrimary(i == chosen));
        });
    }

    private void syncContacts(Party party, List<ContactRequest> requested) {
        Map<Long, PartyContact> existing = byId(party.getContacts(), PartyContact::getId);
        List<PartyContact> kept = new ArrayList<>();
        List<Boolean> primaries = new ArrayList<>();
        for (ContactRequest c : requested) {
            String contactName = required(c.name(), "Contact name", 200);
            String email = max(clean(c.email()), "Email", 200);
            if (email != null && !EMAIL.matcher(email).matches()) {
                throw new IllegalArgumentException("'%s' is not an email address.".formatted(email));
            }
            PartyContact row = c.id() == null ? party.addContact(contactName, null) : existingRow(existing, c.id(), "Contact");
            row.update(contactName, max(clean(c.designationCode()), "Designation", 40));
            row.reachableAt(max(clean(c.phone()), "Phone", 40), max(clean(c.mobile()), "Mobile", 40), email);
            kept.add(row);
            primaries.add(c.primary());
        }
        existing.values().stream().filter(row -> !kept.contains(row)).forEach(party::removeContact);
        onePrimary(kept, primaries, PartyContact::setPrimary);
    }

    private void syncBankAccounts(Long orgId, Party party, List<BankAccountRequest> requested) {
        Map<Long, PartyBankAccount> existing = byId(party.getBankAccounts(), PartyBankAccount::getId);
        List<PartyBankAccount> kept = new ArrayList<>();
        List<Boolean> primaries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (BankAccountRequest a : requested) {
            if (a.bankId() == null) throw new IllegalArgumentException("Choose the bank for every account.");
            Party bank = a.bankId().equals(party.getId()) ? party : parties.findScoped(a.bankId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("Bank not found: " + a.bankId()));
            String accountName = required(a.accountName(), "Account name", 200);
            String accountNumber = required(a.accountNumber(), "Account number", 60);
            if (!seen.add(bank.getId() + "/" + accountNumber)) {
                throw new IllegalArgumentException("Account %s at %s is listed twice.".formatted(accountNumber, bank.getName()));
            }
            PartyBankAccount row = a.id() == null
                ? party.addBankAccount(bank, accountName, accountNumber)
                : existingRow(existing, a.id(), "Bank account");
            row.update(bank, accountName, accountNumber);
            row.at(max(clean(a.branchName()), "Branch", 200), max(clean(a.routingNumber()), "Routing number", 40),
                max(clean(a.swiftCode()), "SWIFT", 20));
            String currency = clean(a.currencyCode());
            if (currency != null && !currency.matches("[A-Za-z]{3}")) {
                throw new IllegalArgumentException("Currency must be a three-letter code, e.g. BDT or USD.");
            }
            row.denominatedIn(currency == null ? null : currency.toUpperCase(Locale.ROOT));
            kept.add(row);
            primaries.add(a.primary());
        }
        existing.values().stream().filter(row -> !kept.contains(row)).forEach(party::removeBankAccount);
        onePrimary(kept, primaries, PartyBankAccount::setPrimary);
    }

    // ---- Delete ------------------------------------------------------------------------------------

    @Transactional
    public void delete(Long id) {
        Party party = get(id);
        long documents = parties.documentsNaming(id);
        if (documents > 0) {
            throw new IllegalStateException(
                "%d document(s) name this party, so it cannot be deleted. Deactivate it instead.".formatted(documents));
        }
        if (parties.accountsHeldAt(id) > 0) {
            throw new IllegalStateException("Other parties hold accounts at this bank. Deactivate it instead.");
        }
        party.markDeleted();
        party.setActive(false);
        parties.save(party);
    }

    // ---- Helpers -----------------------------------------------------------------------------------

    static String roleLabel(PartyRole r) {
        return label(r.getRoleType(), r.getQualifier());
    }

    static String label(PartyRoleType type, String qualifier) {
        return qualifier == null ? type.name() : type.name() + ":" + qualifier;
    }

    private static <T> Map<Long, T> byId(List<T> rows, Function<T, Long> id) {
        Map<Long, T> map = new LinkedHashMap<>();
        rows.forEach(row -> map.put(id.apply(row), row));
        return map;
    }

    private static <T> T existingRow(Map<Long, T> existing, Long id, String what) {
        T row = existing.get(id);
        if (row == null) throw new IllegalArgumentException("%s %d does not belong to this party.".formatted(what, id));
        return row;
    }

    private static <T> void onePrimary(List<T> rows, List<Boolean> requested, java.util.function.BiConsumer<T, Boolean> set) {
        if (rows.isEmpty()) return;
        int chosen = requested.indexOf(Boolean.TRUE);
        if (chosen < 0) chosen = 0;
        for (int i = 0; i < rows.size(); i++) set.accept(rows.get(i), i == chosen);
    }

    private static Map<String, Object> attributes(Map<String, String> submitted) {
        Map<String, Object> cleaned = new LinkedHashMap<>();
        if (submitted == null) return cleaned;
        submitted.forEach((key, value) -> {
            String k = clean(key);
            String v = clean(value);
            if (k != null && v != null) cleaned.put(max(k, "Detail name", 60), max(v, "Detail value", 300));
        });
        return cleaned;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String required(String value, String field, int maxLength) {
        String cleaned = clean(value);
        if (cleaned == null) throw new IllegalArgumentException(field + " is required.");
        return max(cleaned, field, maxLength);
    }

    private static String max(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException("%s is longer than %d characters.".formatted(field, maxLength));
        }
        return value;
    }
}

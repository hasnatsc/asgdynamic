package com.asg.fabricerp.party;

import com.asg.fabricerp.global.documents.DocumentType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.asg.fabricerp.party.PartyRoleType.*;
import static org.assertj.core.api.Assertions.*;

/** Ported from asfl-erp's {@code PartyTest}: one record, many roles, and roles that mean something. */
class PartyTest {

    private static final LocalDate DAY = LocalDate.of(2026, 1, 15);

    private static Party party(String code) {
        return new Party(code, code + " Ltd", Party.PartyType.ORGANISATION);
    }

    @Test
    void oneRecordHoldsBothSidesOfTheRelationship() {
        Party factory = party("GF1");
        factory.grantRole(CUSTOMER, MARKETING, "C-001", DAY);
        factory.grantRole(SUPPLIER, DAY);

        assertThat(factory.activeRoles()).containsExactlyInAnyOrder(CUSTOMER, SUPPLIER);
    }

    @Test
    void aCustomerMustBeQualified_andNothingElseMayBe() {
        Party p = party("X");
        assertThatThrownBy(() -> p.grantRole(CUSTOMER, null, null, DAY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> p.grantRole(SUPPLIER, MARKETING, null, DAY)).isInstanceOf(IllegalArgumentException.class);

        p.grantRole(CUSTOMER, MARKETING, null, DAY);
        p.grantRole(CUSTOMER, COMMERCIAL, null, DAY);
        assertThat(p.getRoles()).hasSize(2);
    }

    @Test
    void revokingKeepsTheHistory_andRegrantingRestoresTheSameRow() {
        Party p = party("S1");
        PartyRole role = p.grantRole(SUPPLIER, DAY);
        p.revokeRole(SUPPLIER, null, DAY.plusDays(30));

        assertThat(p.holds(SUPPLIER)).isFalse();
        assertThat(role.getRevokedOn()).isEqualTo(DAY.plusDays(30));

        PartyRole again = p.grantRole(SUPPLIER, DAY.plusDays(90));
        assertThat(again).isSameAs(role);
        assertThat(p.holds(SUPPLIER)).isTrue();
        assertThat(role.getGrantedOn()).isEqualTo(DAY);   // "since when" survives
    }

    @Test
    void theWrongRoleIsRefusedWithWhatThePartyActuallyIs() {
        Party supplier = party("S2");
        supplier.grantRole(SUPPLIER, DAY);

        assertThatThrownBy(() -> supplier.requireRole(CUSTOMER))
            .isInstanceOf(PartyRoleNotHeldException.class)
            .hasMessage("Party S2 does not hold role CUSTOMER; it holds SUPPLIER");
    }

    @Test
    void documentTypesDemandTheRightRole() {
        assertThat(DocumentType.BOOKING.requiredPartyRole()).isEqualTo(CUSTOMER);
        assertThat(DocumentType.WEAVING_WORK_ORDER.requiredPartyRole()).isEqualTo(CUSTOMER);
        assertThat(DocumentType.PURCHASE_ORDER.requiredPartyRole()).isEqualTo(SUPPLIER);
        assertThat(DocumentType.EXPORT_LETTER_OF_CREDIT.requiredPartyRole()).isNull();
    }

    @Test
    void aBankAccountIsHeldAtABankParty_andOnlyABank() {
        Party customer = party("C1");
        Party bank = party("BRAC");
        bank.grantRole(BANK, DAY);
        Party notABank = party("S3");
        notABank.grantRole(SUPPLIER, DAY);

        PartyBankAccount account = customer.addBankAccount(bank, "C1 Ltd", "1501-00123").at("Gulshan", "060261726", "BRAKBDDH");
        assertThat(account.isPrimary()).isTrue();
        assertThat(account.getBank()).isSameAs(bank);

        assertThatThrownBy(() -> customer.addBankAccount(notABank, "C1 Ltd", "9"))
            .isInstanceOf(PartyRoleNotHeldException.class);
    }

    @Test
    void severalAddressesPerType_theFirstIsPrimary() {
        Party p = party("GF2");
        PartyAddress first = p.addAddress(PartyAddress.AddressType.SHIPPING, "Plot 1, Gazipur", "GAZ");
        PartyAddress second = p.addAddress(PartyAddress.AddressType.SHIPPING, "Plot 9, Savar", "SAV");

        assertThat(first.isPrimary()).isTrue();
        assertThat(second.isPrimary()).isFalse();
        assertThat(p.primaryAddress(PartyAddress.AddressType.SHIPPING)).containsSame(first);
    }
}

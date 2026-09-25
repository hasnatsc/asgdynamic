package com.asg.fabricerp.accounts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** The ledger and every report over it. Sums run in PostgreSQL, in BDT (functional amount). */
public interface GlEntryRepository extends JpaRepository<GlEntry, Long> {

    @Query("select e from GlEntry e where e.id = :id and e.organizationId = :orgId")
    Optional<GlEntry> findScoped(@Param("id") Long id, @Param("orgId") Long orgId);

    @Query(value = """
           select e from GlEntry e
           where e.organizationId = :orgId
             and e.postingDate >= :from and e.postingDate <= :to
             and (:event = '' or e.eventType = :event)
             and (:voucher is null or e.voucherType = :voucher)
             and (:pattern = '' or lower(e.entryNo) like :pattern or lower(coalesce(e.narration, '')) like :pattern)
           """,
           countQuery = """
           select count(e) from GlEntry e
           where e.organizationId = :orgId
             and e.postingDate >= :from and e.postingDate <= :to
             and (:event = '' or e.eventType = :event)
             and (:voucher is null or e.voucherType = :voucher)
             and (:pattern = '' or lower(e.entryNo) like :pattern or lower(coalesce(e.narration, '')) like :pattern)
           """)
    Page<GlEntry> search(@Param("orgId") Long orgId, @Param("from") LocalDate from, @Param("to") LocalDate to,
                         @Param("event") String event, @Param("voucher") VoucherType voucher,
                         @Param("pattern") String pattern, Pageable pageable);

    /** Per account: BDT debits and credits posted between two dates - the trial balance. */
    @Query("""
           select l.accountCode,
                  sum(case when l.side = com.asg.fabricerp.accounts.AccountFlags.Side.DEBIT then l.functionalAmount else 0 end),
                  sum(case when l.side = com.asg.fabricerp.accounts.AccountFlags.Side.CREDIT then l.functionalAmount else 0 end)
           from GlEntryLine l join l.entry e
           where e.organizationId = :orgId and e.postingDate >= :from and e.postingDate <= :to
           group by l.accountCode
           """)
    List<Object[]> movementByAccount(@Param("orgId") Long orgId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Lines on one account between two dates, oldest first - the account ledger. */
    @Query("""
           select l from GlEntryLine l join fetch l.entry e
           where e.organizationId = :orgId and l.accountCode = :account
             and e.postingDate >= :from and e.postingDate <= :to
           order by e.postingDate, e.id, l.sequence
           """)
    List<GlEntryLine> accountLines(@Param("orgId") Long orgId, @Param("account") String accountCode,
                                   @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Signed BDT balance (debit positive) of an account before a date: the ledger's opening line. */
    @Query("""
           select coalesce(sum(case when l.side = com.asg.fabricerp.accounts.AccountFlags.Side.DEBIT
                                    then l.functionalAmount else -l.functionalAmount end), 0)
           from GlEntryLine l join l.entry e
           where e.organizationId = :orgId and l.accountCode = :account and e.postingDate < :before
           """)
    BigDecimal openingBalance(@Param("orgId") Long orgId, @Param("account") String accountCode,
                              @Param("before") LocalDate before);

    /** A party's lines on control accounts, oldest first - the customer / supplier ledger. */
    @Query("""
           select l from GlEntryLine l join fetch l.entry e
           where e.organizationId = :orgId and l.partyId = :partyId
             and e.postingDate >= :from and e.postingDate <= :to
           order by e.postingDate, e.id, l.sequence
           """)
    List<GlEntryLine> partyLines(@Param("orgId") Long orgId, @Param("partyId") Long partyId,
                                 @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
           select coalesce(sum(case when l.side = com.asg.fabricerp.accounts.AccountFlags.Side.DEBIT
                                    then l.functionalAmount else -l.functionalAmount end), 0)
           from GlEntryLine l join l.entry e
           where e.organizationId = :orgId and l.partyId = :partyId and e.postingDate < :before
           """)
    BigDecimal partyOpeningBalance(@Param("orgId") Long orgId, @Param("partyId") Long partyId,
                                   @Param("before") LocalDate before);

    /** What a customer owes: signed balance on asset control accounts, up to a date. */
    @Query("""
           select coalesce(sum(case when l.side = com.asg.fabricerp.accounts.AccountFlags.Side.DEBIT
                                    then l.functionalAmount else -l.functionalAmount end), 0)
           from GlEntryLine l join l.entry e, Account a
           where e.organizationId = :orgId and l.partyId = :partyId and e.postingDate <= :asOf
             and a.organizationId = e.organizationId and a.code = l.accountCode and a.deleted = false
             and a.usage = com.asg.fabricerp.accounts.AccountFlags.AccountUsage.CONTROL
             and a.accountType = com.asg.fabricerp.accounts.AccountFlags.AccountType.ASSET
           """)
    BigDecimal receivableBalance(@Param("orgId") Long orgId, @Param("partyId") Long partyId,
                                 @Param("asOf") LocalDate asOf);

    @Query("select count(l) from GlEntryLine l join l.entry e where e.organizationId = :orgId and l.accountCode = :account")
    long countLinesOn(@Param("orgId") Long orgId, @Param("account") String accountCode);
}

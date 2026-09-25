package com.asg.fabricerp.accounts;

import com.asg.fabricerp.accounts.AccountFlags.AccountType;
import com.asg.fabricerp.accounts.AccountFlags.Side;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.party.Party;
import com.asg.fabricerp.party.PartyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports over the ledger: trial balance, account ledger and party (customer / supplier) ledger.
 * All figures are BDT (the functional amount), summed in PostgreSQL.
 */
@Service
@Transactional(readOnly = true)
public class LedgerReportService {

    private final GlEntryRepository entries;
    private final AccountRepository accounts;
    private final PartyRepository parties;
    private final OrgContext context;

    public LedgerReportService(GlEntryRepository entries, AccountRepository accounts, PartyRepository parties,
                               OrgContext context) {
        this.entries = entries;
        this.accounts = accounts;
        this.parties = parties;
        this.context = context;
    }

    /** One chart row of the trial balance. Summary rows carry their children's totals. */
    public record TrialBalanceRow(String code, String name, AccountType type, String usage, int depth,
                                  String parentCode, BigDecimal opening, BigDecimal debit, BigDecimal credit,
                                  BigDecimal closing) { }

    public record TrialBalance(LocalDate from, LocalDate to, List<TrialBalanceRow> rows,
                               BigDecimal totalDebit, BigDecimal totalCredit, boolean balanced) { }

    /**
     * Opening balance before {@code from}, movement from {@code from} to {@code to}, and closing - per
     * account, with summary accounts rolled up. Balances are signed debit-positive; the screen shows
     * them on the account's natural side.
     */
    public TrialBalance trialBalance(LocalDate from, LocalDate to) {
        Long orgId = context.requireOrganizationId();
        Map<String, BigDecimal[]> movement = new HashMap<>();
        for (Object[] row : entries.movementByAccount(orgId, from, to)) {
            movement.put((String) row[0], new BigDecimal[] {num(row[1]), num(row[2])});
        }
        Map<String, BigDecimal> opening = new HashMap<>();
        for (Object[] row : entries.movementByAccount(orgId, LocalDate.of(1900, 1, 1), from.minusDays(1))) {
            opening.put((String) row[0], num(row[1]).subtract(num(row[2])));
        }

        List<Account> chart = accounts.chart(orgId);
        Map<String, Account> byCode = new LinkedHashMap<>();
        chart.forEach(a -> byCode.put(a.getCode(), a));

        // Leaf figures first, then add each leaf into every ancestor.
        Map<String, BigDecimal[]> figures = new HashMap<>();   // opening, debit, credit
        for (Account account : chart) {
            BigDecimal[] mv = movement.getOrDefault(account.getCode(), new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal op = opening.getOrDefault(account.getCode(), BigDecimal.ZERO);
            for (Account at = account; at != null; at = at.getParent() == null ? null : byCode.get(at.getParent().getCode())) {
                BigDecimal[] f = figures.computeIfAbsent(at.getCode(), k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                f[0] = f[0].add(op);
                f[1] = f[1].add(mv[0]);
                f[2] = f[2].add(mv[1]);
            }
        }

        List<TrialBalanceRow> rows = new ArrayList<>();
        for (Account root : chart) {
            if (root.getParent() == null) addTree(root, 0, chart, figures, rows);
        }

        // Totals over leaves only, so summary rows are not counted twice.
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (Account account : chart) {
            if (account.isSummary()) continue;
            BigDecimal[] f = figures.get(account.getCode());
            BigDecimal closing = f[0].add(f[1]).subtract(f[2]);
            if (closing.signum() > 0) totalDebit = totalDebit.add(closing); else totalCredit = totalCredit.add(closing.negate());
        }
        return new TrialBalance(from, to, rows, totalDebit, totalCredit, totalDebit.compareTo(totalCredit) == 0);
    }

    private void addTree(Account node, int depth, List<Account> chart, Map<String, BigDecimal[]> figures,
                         List<TrialBalanceRow> rows) {
        BigDecimal[] f = figures.get(node.getCode());
        rows.add(new TrialBalanceRow(node.getCode(), node.getName(), node.getAccountType(), node.getUsage().name(), depth,
            node.getParent() == null ? null : node.getParent().getCode(),
            f[0], f[1], f[2], f[0].add(f[1]).subtract(f[2])));
        for (Account child : chart) {
            if (child.getParent() != null && child.getParent().getCode().equals(node.getCode())) {
                addTree(child, depth + 1, chart, figures, rows);
            }
        }
    }

    /** A ledger line with its running balance (debit-positive, BDT). */
    public record LedgerLine(Long entryId, String entryNo, LocalDate date, String eventType, String narration,
                             String accountCode, Long partyId, BigDecimal debit, BigDecimal credit, BigDecimal balance) { }

    public record Ledger(String title, String subtitle, LocalDate from, LocalDate to, BigDecimal opening,
                         List<LedgerLine> lines, BigDecimal totalDebit, BigDecimal totalCredit, BigDecimal closing) { }

    public Ledger accountLedger(String accountCode, LocalDate from, LocalDate to) {
        Long orgId = context.requireOrganizationId();
        Account account = accounts.findByCode(orgId, accountCode)
            .orElseThrow(() -> new IllegalArgumentException("No account " + accountCode + "."));
        if (account.isSummary()) {
            throw new IllegalArgumentException(account.getCode() + " is a summary account; open one of its sub-accounts "
                + "or use the trial balance for its total.");
        }
        return ledger(account.getCode() + " · " + account.getName(), account.getAccountType().label(), from, to,
            entries.openingBalance(orgId, accountCode, from), entries.accountLines(orgId, accountCode, from, to));
    }

    public Ledger partyLedger(Long partyId, LocalDate from, LocalDate to) {
        Long orgId = context.requireOrganizationId();
        Party party = parties.findScoped(partyId, orgId)
            .orElseThrow(() -> new IllegalArgumentException("No party " + partyId + "."));
        return ledger(party.getCode() + " · " + party.getName(), "Customer / supplier ledger", from, to,
            entries.partyOpeningBalance(orgId, partyId, from), entries.partyLines(orgId, partyId, from, to));
    }

    private static Ledger ledger(String title, String subtitle, LocalDate from, LocalDate to, BigDecimal opening,
                                 List<GlEntryLine> lines) {
        BigDecimal running = opening;
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        List<LedgerLine> out = new ArrayList<>();
        for (GlEntryLine line : lines) {
            BigDecimal debit = line.getSide() == Side.DEBIT ? line.getFunctionalAmount() : BigDecimal.ZERO;
            BigDecimal credit = line.getSide() == Side.CREDIT ? line.getFunctionalAmount() : BigDecimal.ZERO;
            running = running.add(debit).subtract(credit);
            debits = debits.add(debit);
            credits = credits.add(credit);
            GlEntry e = line.getEntry();
            out.add(new LedgerLine(e.getId(), e.getEntryNo(), e.getPostingDate(), e.getEventType(),
                line.getNarration() == null ? e.getNarration() : line.getNarration(), line.getAccountCode(),
                line.getPartyId(), debit, credit, running));
        }
        return new Ledger(title, subtitle, from, to, opening, out, debits, credits, running);
    }

    private static BigDecimal num(Object value) {
        return value == null ? BigDecimal.ZERO : value instanceof BigDecimal b ? b : new BigDecimal(value.toString());
    }
}

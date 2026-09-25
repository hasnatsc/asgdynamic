package com.asg.fabricerp.accounts;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.party.PartyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Customer credit control, computed from the ledger rather than from a maintained balance.
 *
 * <p>A stored customer balance is the classic figure that drifts from the ledger it claims to
 * summarise. Control-account lines always name their party, so the receivable is a sum over the
 * ledger by construction - and summing keeps it that way.
 */
@Service
@Transactional(readOnly = true)
public class CreditService {

    private final CreditLimitRepository limits;
    private final GlEntryRepository entries;
    private final PartyRepository parties;
    private final OrgContext context;

    public CreditService(CreditLimitRepository limits, GlEntryRepository entries, PartyRepository parties, OrgContext context) {
        this.limits = limits;
        this.entries = entries;
        this.parties = parties;
        this.context = context;
    }

    public record Exposure(Long partyId, String currencyCode, BigDecimal receivable, BigDecimal secured,
                           BigDecimal limit, BigDecimal headroom, boolean onHold, String holdReason,
                           boolean hasLimit, Verdict verdict) { }

    public enum Verdict { WITHIN_LIMIT, OVER_LIMIT, ON_HOLD, NO_LIMIT }

    public BigDecimal receivable(Long partyId, LocalDate asOf) {
        return entries.receivableBalance(context.requireOrganizationId(), partyId, asOf);
    }

    /** What the customer owes, their limit, and whether {@code additional} more may be committed. */
    public Exposure exposureOf(Long partyId, BigDecimal additional, LocalDate on) {
        Optional<CreditLimit> limit = limitOn(partyId, on);
        BigDecimal receivable = receivable(partyId, on);
        BigDecimal exposure = receivable.add(additional == null ? BigDecimal.ZERO : additional);
        if (limit.isEmpty()) {
            return new Exposure(partyId, "BDT", receivable, BigDecimal.ZERO, null, null, false, null, false, Verdict.NO_LIMIT);
        }
        CreditLimit l = limit.get();
        BigDecimal headroom = l.headroomAgainst(exposure);
        Verdict verdict = l.isOnHold() ? Verdict.ON_HOLD : headroom.signum() < 0 ? Verdict.OVER_LIMIT : Verdict.WITHIN_LIMIT;
        return new Exposure(partyId, l.getCurrencyCode(), receivable, l.getSecuredAmount(), l.getLimitAmount(),
            headroom, l.isOnHold(), l.getHoldReason(), true, verdict);
    }

    public Optional<CreditLimit> limitOn(Long partyId, LocalDate on) {
        return limits.forParty(context.requireOrganizationId(), partyId).stream()
            .filter(l -> l.coversDate(on)).findFirst();
    }

    public List<CreditLimit> currentLimits() {
        return limits.current(context.requireOrganizationId());
    }

    public record LimitRequest(Long partyId, BigDecimal limitAmount, String currencyCode, LocalDate effectiveFrom,
                               BigDecimal securedAmount, LocalDate reviewOn, String remarks) { }

    /** Grants a limit; a customer's current limit is superseded, never edited. */
    @Transactional
    public CreditLimit grant(LimitRequest request) {
        Long orgId = context.requireOrganizationId();
        if (request.partyId() == null) throw new IllegalArgumentException("Choose the customer.");
        parties.findScoped(request.partyId(), orgId)
            .orElseThrow(() -> new IllegalArgumentException("No party " + request.partyId() + "."));
        LocalDate from = request.effectiveFrom() == null ? LocalDate.now() : request.effectiveFrom();
        for (CreditLimit current : limits.forParty(orgId, request.partyId())) {
            if (current.getEffectiveTo() == null) {
                current.supersededFrom(from);
                limits.saveAndFlush(current);   // free the one-current-limit index before the insert
            }
        }
        CreditLimit limit = new CreditLimit(request.partyId(), request.currencyCode(), request.limitAmount(), from)
            .reviewedOn(request.reviewOn()).noting(request.remarks());
        if (request.securedAmount() != null) limit.securedBy(request.securedAmount());
        limit.setOrganizationId(orgId);
        return limits.save(limit);
    }

    @Transactional
    public CreditLimit hold(Long limitId, String reason) {
        CreditLimit limit = scoped(limitId);
        limit.placeOnHold(reason);
        return limit;
    }

    @Transactional
    public CreditLimit release(Long limitId) {
        CreditLimit limit = scoped(limitId);
        limit.releaseHold();
        return limit;
    }

    private CreditLimit scoped(Long id) {
        return limits.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("No credit limit " + id + "."));
    }
}

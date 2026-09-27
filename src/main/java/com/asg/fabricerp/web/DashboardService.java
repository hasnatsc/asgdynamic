package com.asg.fabricerp.web;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.inventory.item.InventoryItemRepository;
import com.asg.fabricerp.party.PartyRepository;
import com.asg.fabricerp.security.FabricUserRepository;
import org.springframework.stereotype.Service;

/**
 * Lightweight service that gathers headline numbers for the home-page KPI row.
 * Each count is a single {@code SELECT COUNT(*)} — no joins, no heavy lifting — over the
 * organization being worked in, so switching organization in the header changes them too.
 * When a repository does not exist yet (e.g. booking counts), the count stays zero.
 */
@Service
public class DashboardService {

    private final FabricUserRepository users;
    private final PartyRepository parties;
    private final InventoryItemRepository items;
    private final OrgContext context;

    public DashboardService(FabricUserRepository users, PartyRepository parties,
                            InventoryItemRepository items, OrgContext context) {
        this.users = users;
        this.parties = parties;
        this.items = items;
        this.context = context;
    }

    public DashboardStats stats() {
        Long orgId = context.requireOrganizationId();
        return new DashboardStats(
            0, 0,       // bookings, draftBookings — wire up when BookingRepository exposes counts
            0, 0,       // productionOrders, activeProductionOrders — wire up later
            items.countByOrganizationIdAndDeletedFalse(orgId),
            parties.countByOrganizationIdAndDeletedFalse(orgId),
            users.countByOrganizationIdAndDeletedFalse(orgId),
            users.countByOrganizationIdAndAccountLockedTrueAndDeletedFalse(orgId)
        );
    }

    public record DashboardStats(
        long bookings, long draftBookings,
        long productionOrders, long activeProductionOrders,
        long inventoryItems,
        long parties,
        long users, long lockedUsers
    ) {}
}

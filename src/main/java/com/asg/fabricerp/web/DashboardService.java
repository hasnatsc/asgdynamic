package com.asg.fabricerp.web;

import com.asg.fabricerp.inventory.item.InventoryItemRepository;
import com.asg.fabricerp.party.PartyRepository;
import com.asg.fabricerp.security.FabricUserRepository;
import org.springframework.stereotype.Service;

/**
 * Lightweight service that gathers headline numbers for the home-page KPI row.
 * Each count is a single {@code SELECT COUNT(*)} — no joins, no heavy lifting.
 * When a repository does not exist yet (e.g. booking counts), the count stays zero.
 */
@Service
public class DashboardService {

    private final FabricUserRepository users;
    private final PartyRepository parties;
    private final InventoryItemRepository items;

    public DashboardService(FabricUserRepository users, PartyRepository parties,
                            InventoryItemRepository items) {
        this.users = users;
        this.parties = parties;
        this.items = items;
    }

    public DashboardStats stats() {
        return new DashboardStats(
            0, 0,       // bookings, draftBookings — wire up when BookingRepository exposes counts
            0, 0,       // productionOrders, activeProductionOrders — wire up later
            items.count(),
            parties.count(),
            users.count(),
            users.countByAccountLockedTrue()
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

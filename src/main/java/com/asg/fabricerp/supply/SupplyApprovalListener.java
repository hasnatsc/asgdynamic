package com.asg.fabricerp.supply;

import com.asg.fabricerp.approval.ApprovalListener;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.DocumentType;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * What a purchase requisition or order does the moment its last approval level signs: it moves the
 * progress of what it was raised against. Store documents - requisitions, transfer requests,
 * adjustments and the rest - are approved and then posted, and act when posted
 * ({@link SupplyPostingService#post}).
 */
@Component
public class SupplyApprovalListener implements ApprovalListener {

    private static final Set<DocumentType> HANDLED = EnumSet.of(DocumentType.PURCHASE_REQUISITION, DocumentType.PURCHASE_ORDER);

    private final SupplyProgress progress;

    public SupplyApprovalListener(SupplyProgress progress) {
        this.progress = progress;
    }

    @Override
    public boolean handles(DocumentType type) {
        return HANDLED.contains(type);
    }

    @Override
    public void onApproved(BusinessDocument document) {
        progress.refreshUpwards(document);
    }
}

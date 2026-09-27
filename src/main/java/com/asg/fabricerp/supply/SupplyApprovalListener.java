package com.asg.fabricerp.supply;

import com.asg.fabricerp.approval.ApprovalListener;
import com.asg.fabricerp.global.documents.BusinessDocument;
import com.asg.fabricerp.global.documents.DocumentType;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * What the purchase and store documents do the moment their last approval level signs: a stock
 * adjustment writes its moves (refused, and not signed, if the store no longer holds what it takes
 * away, or its month is closed); an approved purchase requisition, order or transfer request moves
 * the progress of what it was raised against.
 */
@Component
public class SupplyApprovalListener implements ApprovalListener {

    private static final Set<DocumentType> HANDLED = EnumSet.of(DocumentType.STORE_REQUISITION,
        DocumentType.PURCHASE_REQUISITION, DocumentType.PURCHASE_ORDER, DocumentType.STOCK_TRANSFER,
        DocumentType.STOCK_ADJUSTMENT);

    private final SupplyStockWriter writer;
    private final SupplyProgress progress;

    public SupplyApprovalListener(SupplyStockWriter writer, SupplyProgress progress) {
        this.writer = writer;
        this.progress = progress;
    }

    @Override
    public boolean handles(DocumentType type) {
        return HANDLED.contains(type);
    }

    @Override
    public void onApproved(BusinessDocument document) {
        if (document.getDocumentType() == DocumentType.STOCK_ADJUSTMENT) writer.write(SupplyStep.SA, document);
        progress.refreshUpwards(document);
    }
}

package com.asg.fabricerp.search;

import com.asg.fabricerp.accounts.VoucherType;
import com.asg.fabricerp.global.documents.DocumentType;
import com.asg.fabricerp.security.Screen;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * What central search finds. Each kind is read from one table ({@link SearchSql}), opened on one
 * screen and seen only by whoever holds VIEW on that screen - for documents, the screen of their
 * type, so a store keeper's search never lists a commercial invoice.
 */
public enum SearchKind {

    /** Every {@link DocumentType} that has a screen: booking to delivery, purchase, store, commercial. */
    DOCUMENT("Documents"),
    /** General-ledger entries, by voucher number. */
    VOUCHER("Vouchers"),
    PARTY("Parties"),
    ITEM("Items");

    private final String label;

    SearchKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** The authority that lets a user see a record of this kind; null for a document type with no screen yet. */
    public static String viewAuthority(SearchKind kind, String docType) {
        return switch (kind) {
            case DOCUMENT -> {
                Screen screen = documentScreen(docType);
                yield screen == null ? null : "SCREEN_" + screen.name() + "_VIEW";
            }
            case VOUCHER -> "SCREEN_ACC_JOURNAL_VIEW";
            case PARTY -> "SCREEN_PARTY_VIEW";
            case ITEM -> "SCREEN_ITEM_VIEW";
        };
    }

    /** The kinds a user holding these authorities can find anything of - the search page's filter chips. */
    public static List<SearchKind> visibleTo(Set<String> authorities) {
        return Arrays.stream(values()).filter(kind -> kind == DOCUMENT
            ? Arrays.stream(DocumentType.values()).map(t -> viewAuthority(DOCUMENT, t.name()))
                .anyMatch(a -> a != null && authorities.contains(a))
            : authorities.contains(viewAuthority(kind, null))).toList();
    }

    /** Where a record opens: the same {@code ?open=} / {@code ?id=} / {@code ?view=} links the screens already honour. */
    public static String link(SearchKind kind, String docType, Long id) {
        return switch (kind) {
            case DOCUMENT -> {
                Screen screen = documentScreen(docType);
                yield screen == null ? null : screen.path() + "?open=" + id;
            }
            case VOUCHER -> Screen.ACC_JOURNAL.path() + "?open=" + id;
            case PARTY -> Screen.PARTY.path() + "?id=" + id;
            case ITEM -> Screen.ITEM.path() + "?view=" + id;
        };
    }

    /** "Production Order", "Payment voucher", "Customer"... - what the result list calls a record. */
    public static String typeLabel(SearchKind kind, String docType) {
        if (docType == null) return kind == PARTY ? "Party" : kind == ITEM ? "Item" : kind.label;
        try {
            return switch (kind) {
                case DOCUMENT -> DocumentType.valueOf(docType).label();
                case VOUCHER -> VoucherType.valueOf(docType).label();
                case PARTY -> "INDIVIDUAL".equals(docType) ? "Party (individual)" : "Party";
                case ITEM -> com.asg.fabricerp.inventory.item.ItemType.valueOf(docType).label() + " item";
            };
        } catch (IllegalArgumentException unknown) {
            return docType;
        }
    }

    /** The screen a document type lives on, through its role root - the mapping the approval links use. */
    static Screen documentScreen(String docType) {
        if (docType == null) return null;
        try {
            return Screen.valueOf(DocumentType.valueOf(docType).roleRoot());
        } catch (RuntimeException noScreen) {
            return null;
        }
    }
}

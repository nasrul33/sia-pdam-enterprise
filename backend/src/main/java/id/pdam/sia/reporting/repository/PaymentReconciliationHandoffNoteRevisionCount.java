package id.pdam.sia.reporting.repository;

import java.util.UUID;

public interface PaymentReconciliationHandoffNoteRevisionCount {
    UUID getNoteId();

    long getRevisionCount();
}

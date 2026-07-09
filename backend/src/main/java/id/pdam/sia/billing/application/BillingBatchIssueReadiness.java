package id.pdam.sia.billing.application;

import id.pdam.sia.billing.domain.BillingBatch;

import java.math.BigDecimal;

public record BillingBatchIssueReadiness(
        BillingBatch batch,
        long totalInvoices,
        long draftInvoices,
        long issuedOrPaidInvoices,
        long blockedInvoices,
        long missingJournalTraceInvoices,
        BigDecimal draftAmount,
        BigDecimal outstandingAmount,
        boolean readyToIssue
) {
}

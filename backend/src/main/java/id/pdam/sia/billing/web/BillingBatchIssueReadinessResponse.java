package id.pdam.sia.billing.web;

import id.pdam.sia.billing.application.BillingBatchIssueReadiness;

import java.math.BigDecimal;

public record BillingBatchIssueReadinessResponse(
        BillingBatchResponse batch,
        long totalInvoices,
        long draftInvoices,
        long issuedOrPaidInvoices,
        long blockedInvoices,
        long missingJournalTraceInvoices,
        BigDecimal draftAmount,
        BigDecimal outstandingAmount,
        boolean readyToIssue
) {
    public static BillingBatchIssueReadinessResponse from(BillingBatchIssueReadiness readiness) {
        return new BillingBatchIssueReadinessResponse(
                BillingBatchResponse.from(readiness.batch()),
                readiness.totalInvoices(),
                readiness.draftInvoices(),
                readiness.issuedOrPaidInvoices(),
                readiness.blockedInvoices(),
                readiness.missingJournalTraceInvoices(),
                readiness.draftAmount(),
                readiness.outstandingAmount(),
                readiness.readyToIssue()
        );
    }
}

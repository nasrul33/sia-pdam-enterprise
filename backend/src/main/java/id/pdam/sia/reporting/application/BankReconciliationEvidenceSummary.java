package id.pdam.sia.reporting.application;

import id.pdam.sia.payment.application.PaymentReconciliationMatchStatus;
import id.pdam.sia.payment.domain.PaymentReconciliationItem;
import id.pdam.sia.payment.domain.PaymentReconciliationResolutionStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record BankReconciliationEvidenceSummary(
        int totalRows,
        int exactMatches,
        int probableMatches,
        int amountVariances,
        int reversedPayments,
        int multipleCandidates,
        int unmatchedRows,
        BigDecimal totalVariance,
        int acceptedItems,
        int resolvedItems,
        int ignoredItems,
        int adjustedItems
) {
    public static BankReconciliationEvidenceSummary from(List<PaymentReconciliationItem> items) {
        BigDecimal totalVariance = items.stream()
                .map(PaymentReconciliationItem::getAmountVariance)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return new BankReconciliationEvidenceSummary(
                items.size(),
                countMatch(items, PaymentReconciliationMatchStatus.EXACT_MATCH),
                countMatch(items, PaymentReconciliationMatchStatus.PROBABLE_MATCH),
                countMatch(items, PaymentReconciliationMatchStatus.AMOUNT_VARIANCE),
                countMatch(items, PaymentReconciliationMatchStatus.REVERSED_PAYMENT),
                countMatch(items, PaymentReconciliationMatchStatus.MULTIPLE_CANDIDATES),
                countMatch(items, PaymentReconciliationMatchStatus.UNMATCHED),
                totalVariance,
                countResolution(items, PaymentReconciliationResolutionStatus.ACCEPTED),
                countResolution(items, PaymentReconciliationResolutionStatus.RESOLVED),
                countResolution(items, PaymentReconciliationResolutionStatus.IGNORED),
                (int) items.stream().filter(item -> item.getAdjustmentJournalEntryId() != null).count()
        );
    }

    private static int countMatch(List<PaymentReconciliationItem> items, PaymentReconciliationMatchStatus status) {
        return (int) items.stream().filter(item -> item.getMatchStatus() == status).count();
    }

    private static int countResolution(List<PaymentReconciliationItem> items, PaymentReconciliationResolutionStatus status) {
        return (int) items.stream().filter(item -> item.getResolutionStatus() == status).count();
    }
}

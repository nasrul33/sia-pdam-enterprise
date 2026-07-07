package id.pdam.sia.payment.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record PaymentReconciliationMatchSummary(
        int totalRows,
        int exactMatches,
        int probableMatches,
        int amountVariances,
        int reversedPayments,
        int multipleCandidates,
        int unmatchedRows,
        BigDecimal totalVariance
) {
    public static PaymentReconciliationMatchSummary from(List<PaymentReconciliationMatchResult> matches) {
        BigDecimal totalVariance = matches.stream()
                .map(PaymentReconciliationMatchResult::amountVariance)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return new PaymentReconciliationMatchSummary(
                matches.size(),
                count(matches, PaymentReconciliationMatchStatus.EXACT_MATCH),
                count(matches, PaymentReconciliationMatchStatus.PROBABLE_MATCH),
                count(matches, PaymentReconciliationMatchStatus.AMOUNT_VARIANCE),
                count(matches, PaymentReconciliationMatchStatus.REVERSED_PAYMENT),
                count(matches, PaymentReconciliationMatchStatus.MULTIPLE_CANDIDATES),
                count(matches, PaymentReconciliationMatchStatus.UNMATCHED),
                totalVariance
        );
    }

    private static int count(List<PaymentReconciliationMatchResult> matches, PaymentReconciliationMatchStatus status) {
        return (int) matches.stream().filter(match -> match.status() == status).count();
    }
}

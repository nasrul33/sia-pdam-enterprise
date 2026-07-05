package id.pdam.sia.billing.application;

import id.pdam.sia.billing.domain.BillingBatch;
import id.pdam.sia.billing.domain.Invoice;
import id.pdam.sia.shared.money.Money;

import java.util.List;

public record BillingBatchGenerationResult(
        BillingBatch batch,
        List<Invoice> generatedInvoices,
        Money totalAmount
) {
}

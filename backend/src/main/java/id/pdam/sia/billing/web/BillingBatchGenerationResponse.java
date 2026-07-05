package id.pdam.sia.billing.web;

import id.pdam.sia.billing.application.BillingBatchGenerationResult;

import java.math.BigDecimal;
import java.util.List;

public record BillingBatchGenerationResponse(
        BillingBatchResponse batch,
        List<InvoiceResponse> generatedInvoices,
        BigDecimal totalAmount
) {
    public static BillingBatchGenerationResponse from(BillingBatchGenerationResult result) {
        return new BillingBatchGenerationResponse(
                BillingBatchResponse.from(result.batch()),
                result.generatedInvoices().stream().map(InvoiceResponse::from).toList(),
                result.totalAmount().amount()
        );
    }
}

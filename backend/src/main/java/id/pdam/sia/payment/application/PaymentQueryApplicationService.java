package id.pdam.sia.payment.application;

import id.pdam.sia.payment.domain.Payment;
import id.pdam.sia.payment.domain.PaymentAllocation;
import id.pdam.sia.payment.domain.PaymentReceipt;
import id.pdam.sia.payment.domain.PaymentStatus;
import id.pdam.sia.payment.repository.PaymentAllocationRepository;
import id.pdam.sia.payment.repository.PaymentReceiptRepository;
import id.pdam.sia.payment.repository.PaymentRepository;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PaymentQueryApplicationService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort PAYMENT_LIST_SORT = Sort.by(Sort.Direction.DESC, "paidAt")
            .and(Sort.by(Sort.Direction.DESC, "createdAt"));

    private final PaymentRepository paymentRepository;
    private final PaymentReceiptRepository paymentReceiptRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;

    public PaymentQueryApplicationService(
            PaymentRepository paymentRepository,
            PaymentReceiptRepository paymentReceiptRepository,
            PaymentAllocationRepository paymentAllocationRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentReceiptRepository = paymentReceiptRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
    }

    @Transactional(readOnly = true)
    public Page<Payment> listPayments(PaymentStatus status, String channel, String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), PAYMENT_LIST_SORT);
        String normalizedChannel = normalizeChannel(channel);
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();

        if (normalizedSearch != null) {
            return paymentRepository.search(status, normalizedChannel, normalizedSearch, pageable);
        }
        if (status != null && normalizedChannel != null) {
            return paymentRepository.findByStatusAndChannel(status, normalizedChannel, pageable);
        }
        if (status != null) {
            return paymentRepository.findByStatus(status, pageable);
        }
        if (normalizedChannel != null) {
            return paymentRepository.findByChannel(normalizedChannel, pageable);
        }
        return paymentRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public PaymentSettlementResult getPayment(UUID paymentId) {
        if (paymentId == null) {
            throw new BusinessException("PAYMENT_ID_REQUIRED", "Payment id is required.");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "Payment was not found."));
        PaymentReceipt receipt = paymentReceiptRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException("PAYMENT_RECEIPT_NOT_FOUND", "Payment receipt was not found."));
        List<PaymentAllocation> allocations = paymentAllocationRepository.findByPaymentId(paymentId);

        return new PaymentSettlementResult(payment, receipt, allocations);
    }

    private static String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        return channel.trim().toUpperCase();
    }
}

package id.pdam.sia.billing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceComponentSnapshotTest {
    @Test
    void invoiceSnapshotsComponentsAndIncludesPenaltyInOutstanding() {
        Invoice invoice = new Invoice(
                UUID.randomUUID(), UUID.randomUUID(), "INV-202607-001", "2026-07",
                new BigDecimal("20000.00"), new BigDecimal("5000.00"), new BigDecimal("2000.00"),
                new BigDecimal("2500.00"), new BigDecimal("3000.00"), new BigDecimal("1500.00"),
                LocalDate.of(2026, 8, 20)
        );

        assertThat(invoice.getUsageCharge()).isEqualByComparingTo("20000.00");
        assertThat(invoice.getFixedCharge()).isEqualByComparingTo("5000.00");
        assertThat(invoice.getLevyCharge()).isEqualByComparingTo("2000.00");
        assertThat(invoice.getAdminCharge()).isEqualByComparingTo("2500.00");
        assertThat(invoice.getWasteCharge()).isEqualByComparingTo("3000.00");
        assertThat(invoice.getSubtotal()).isEqualByComparingTo("32500.00");
        assertThat(invoice.getPenaltyAmount()).isEqualByComparingTo("1500.00");
        assertThat(invoice.getOutstandingAmount()).isEqualByComparingTo("34000.00");
    }
}

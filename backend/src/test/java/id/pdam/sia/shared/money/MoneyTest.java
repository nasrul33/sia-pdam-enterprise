package id.pdam.sia.shared.money;

import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void addsAndSubtractsMoney() {
        Money first = Money.of("1000.10");
        Money second = Money.of("250.20");

        assertThat(first.add(second)).isEqualTo(Money.of("1250.30"));
        assertThat(first.subtract(second)).isEqualTo(Money.of("749.90"));
    }

    @Test
    void allocatesWithoutLosingMinorUnit() {
        Money amount = Money.of("100.00");
        List<Money> parts = amount.allocate(List.of(1, 1, 1));

        Money total = parts.stream().reduce(Money.zero(), Money::add);

        assertThat(total).isEqualTo(amount);
        assertThat(parts).containsExactly(Money.of("33.34"), Money.of("33.33"), Money.of("33.33"));
    }

    @Test
    void rejectsInvalidAmount() {
        assertThatThrownBy(() -> Money.of("abc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Amount is invalid");
    }
}

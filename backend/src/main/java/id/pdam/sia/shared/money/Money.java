package id.pdam.sia.shared.money;

import id.pdam.sia.shared.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Money implements Comparable<Money> {
    public static final int SCALE = 2;
    private static final BigDecimal MINOR_UNIT = new BigDecimal("0.01");

    private final BigDecimal amount;
    private final CurrencyCode currency;

    private Money(BigDecimal amount, CurrencyCode currency) {
        if (amount == null) {
            throw new BusinessException("MONEY_AMOUNT_REQUIRED", "Amount is required.");
        }
        if (currency == null) {
            throw new BusinessException("MONEY_CURRENCY_REQUIRED", "Currency is required.");
        }
        this.amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
        this.currency = currency;
    }

    public static Money of(String amount) {
        return of(amount, CurrencyCode.IDR);
    }

    public static Money of(String amount, CurrencyCode currency) {
        try {
            return new Money(new BigDecimal(amount), currency);
        } catch (NumberFormatException ex) {
            throw new BusinessException("MONEY_AMOUNT_INVALID", "Amount is invalid.");
        }
    }

    public static Money of(BigDecimal amount, CurrencyCode currency) {
        return new Money(amount, currency);
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO, CurrencyCode.IDR);
    }

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money multiply(BigDecimal factor) {
        if (factor == null) {
            throw new BusinessException("MONEY_FACTOR_REQUIRED", "Factor is required.");
        }
        return new Money(this.amount.multiply(factor), this.currency);
    }

    public Money divide(BigDecimal divisor) {
        if (divisor == null || divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("MONEY_DIVISOR_INVALID", "Divisor must be non-zero.");
        }
        return new Money(this.amount.divide(divisor, SCALE, RoundingMode.HALF_UP), this.currency);
    }

    public List<Money> allocate(List<Integer> ratios) {
        if (ratios == null || ratios.isEmpty()) {
            throw new BusinessException("MONEY_ALLOCATION_EMPTY", "Ratios are required.");
        }
        int total = ratios.stream().mapToInt(Integer::intValue).sum();
        if (total <= 0 || ratios.stream().anyMatch(r -> r < 0)) {
            throw new BusinessException("MONEY_ALLOCATION_INVALID", "Ratios must be non-negative with positive total.");
        }

        List<BigDecimal> parts = new ArrayList<>();
        BigDecimal remainder = this.amount;
        for (Integer ratio : ratios) {
            BigDecimal share = this.amount
                    .multiply(BigDecimal.valueOf(ratio))
                    .divide(BigDecimal.valueOf(total), SCALE, RoundingMode.DOWN);
            parts.add(share);
            remainder = remainder.subtract(share);
        }

        int index = 0;
        while (remainder.compareTo(BigDecimal.ZERO) > 0) {
            if (ratios.get(index % ratios.size()) > 0) {
                int target = index % parts.size();
                parts.set(target, parts.get(target).add(MINOR_UNIT));
                remainder = remainder.subtract(MINOR_UNIT);
            }
            index++;
        }

        return parts.stream().map(part -> new Money(part, this.currency)).toList();
    }

    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public BigDecimal amount() {
        return amount;
    }

    public CurrencyCode currency() {
        return currency;
    }

    private void assertSameCurrency(Money other) {
        if (other == null || this.currency != other.currency) {
            throw new BusinessException("MONEY_CURRENCY_MISMATCH", "Currency mismatch.");
        }
    }

    @Override
    public int compareTo(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return amount.compareTo(money.amount) == 0 && currency == money.currency;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return currency + " " + amount;
    }
}

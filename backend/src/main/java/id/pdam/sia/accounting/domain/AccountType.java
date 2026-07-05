package id.pdam.sia.accounting.domain;

public enum AccountType {
    ASSET(NormalBalance.DEBIT),
    LIABILITY(NormalBalance.CREDIT),
    EQUITY(NormalBalance.CREDIT),
    REVENUE(NormalBalance.CREDIT),
    EXPENSE(NormalBalance.DEBIT);

    private final NormalBalance normalBalance;

    AccountType(NormalBalance normalBalance) {
        this.normalBalance = normalBalance;
    }

    public NormalBalance normalBalance() {
        return normalBalance;
    }
}

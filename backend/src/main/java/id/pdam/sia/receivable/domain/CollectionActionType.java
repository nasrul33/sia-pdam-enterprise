package id.pdam.sia.receivable.domain;

public enum CollectionActionType {
    REMINDER(true),
    WARNING_LETTER(true),
    DISCONNECTION_NOTICE(true),
    FIELD_VISIT(false),
    PHONE_CALL(false),
    PAYMENT_PROMISE(false);

    private final boolean requiresInvoice;

    CollectionActionType(boolean requiresInvoice) {
        this.requiresInvoice = requiresInvoice;
    }

    public boolean requiresInvoice() {
        return requiresInvoice;
    }
}

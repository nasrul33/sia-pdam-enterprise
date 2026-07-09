package id.pdam.sia.shared.security;

public final class Permissions {
    public static final String COLLECTION_ACTION_READ = "hasAuthority('collection-action.read')";
    public static final String COLLECTION_ACTION_CREATE = "hasAuthority('collection-action.create')";
    public static final String COLLECTION_ACTION_EXECUTE = "hasAuthority('collection-action.execute')";
    public static final String COLLECTION_ACTION_CANCEL = "hasAuthority('collection-action.cancel')";
    public static final String PAYMENT_COUNTER = "hasAuthority('payment.counter')";
    public static final String PAYMENT_READ = "hasAuthority('payment.read')";
    public static final String PAYMENT_RECONCILE = "hasAuthority('payment.reconcile')";
    public static final String PAYMENT_RECONCILIATION_HANDOFF_NOTE = "hasAuthority('payment.reconciliation.handoff-note')";
    public static final String PAYMENT_RECONCILIATION_SIGNOFF = "hasAuthority('payment.reconciliation.signoff')";
    public static final String PAYMENT_RECONCILIATION_STALE_ACKNOWLEDGE =
            "hasAuthority('payment.reconciliation.stale-acknowledge')";
    public static final String PAYMENT_REVERSE = "hasAuthority('payment.reverse')";
    public static final String PAYMENT_WEBHOOK_READ = "hasAuthority('payment.webhook.read')";
    public static final String ACCOUNT_MANAGE = "hasAuthority('account.manage')";
    public static final String PERIOD_MANAGE = "hasAuthority('period.manage')";
    public static final String PERIOD_CLOSE = "hasAuthority('period.close')";
    public static final String JOURNAL_CREATE = "hasAuthority('journal.create')";
    public static final String JOURNAL_POST = "hasAuthority('journal.post')";
    public static final String PAYMENT_RECONCILE_AND_JOURNAL_POST = PAYMENT_RECONCILE + " and " + JOURNAL_POST;
    public static final String PAYMENT_RECONCILE_AND_RECONCILIATION_SIGNOFF =
            PAYMENT_RECONCILE + " and " + PAYMENT_RECONCILIATION_SIGNOFF;
    public static final String PAYMENT_RECONCILE_AND_RECONCILIATION_HANDOFF_NOTE =
            PAYMENT_RECONCILE + " and " + PAYMENT_RECONCILIATION_HANDOFF_NOTE;
    public static final String PAYMENT_RECONCILE_AND_RECONCILIATION_STALE_ACKNOWLEDGE =
            PAYMENT_RECONCILE + " and " + PAYMENT_RECONCILIATION_STALE_ACKNOWLEDGE;
    public static final String BILLING_GENERATE = "hasAuthority('billing.generate')";
    public static final String INVOICE_VIEW = "hasAuthority('invoice.view')";
    public static final String INVOICE_ISSUE = "hasAuthority('invoice.issue')";
    public static final String INVOICE_CORRECT_APPROVE = "hasAuthority('invoice.correct.approve')";

    private Permissions() {
    }
}

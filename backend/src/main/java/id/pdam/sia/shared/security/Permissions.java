package id.pdam.sia.shared.security;

public final class Permissions {
    public static final String COLLECTION_ACTION_READ = "hasAuthority('collection-action.read')";
    public static final String COLLECTION_ACTION_CREATE = "hasAuthority('collection-action.create')";
    public static final String COLLECTION_ACTION_EXECUTE = "hasAuthority('collection-action.execute')";
    public static final String COLLECTION_ACTION_CANCEL = "hasAuthority('collection-action.cancel')";
    public static final String PAYMENT_COUNTER = "hasAuthority('payment.counter')";
    public static final String PAYMENT_REVERSE = "hasAuthority('payment.reverse')";
    public static final String PAYMENT_WEBHOOK_READ = "hasAuthority('payment.webhook.read')";

    private Permissions() {
    }
}

package id.pdam.sia.accounting.application;

public record PreCloseBlocker(
        String code,
        String message,
        long count,
        PreCloseSeverity severity,
        String actionPath
) {
}

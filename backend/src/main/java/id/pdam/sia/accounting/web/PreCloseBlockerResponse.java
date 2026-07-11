package id.pdam.sia.accounting.web;

import id.pdam.sia.accounting.application.PreCloseBlocker;
import id.pdam.sia.accounting.application.PreCloseSeverity;

public record PreCloseBlockerResponse(
        String code,
        String message,
        long count,
        PreCloseSeverity severity,
        String actionPath
) {
    public static PreCloseBlockerResponse from(PreCloseBlocker blocker) {
        return new PreCloseBlockerResponse(
                blocker.code(),
                blocker.message(),
                blocker.count(),
                blocker.severity(),
                blocker.actionPath()
        );
    }
}

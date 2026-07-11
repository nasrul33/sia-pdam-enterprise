package id.pdam.sia.accounting.web;

import id.pdam.sia.accounting.application.PreCloseChecklist;

import java.util.List;
import java.util.UUID;

public record PreCloseChecklistResponse(
        UUID periodId,
        String period,
        boolean closable,
        List<PreCloseBlockerResponse> blockers
) {
    public static PreCloseChecklistResponse from(PreCloseChecklist checklist) {
        return new PreCloseChecklistResponse(
                checklist.periodId(),
                checklist.period(),
                checklist.closable(),
                checklist.blockers().stream().map(PreCloseBlockerResponse::from).toList()
        );
    }
}

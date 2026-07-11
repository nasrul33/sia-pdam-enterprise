package id.pdam.sia.accounting.application;

import java.util.List;
import java.util.UUID;

public record PreCloseChecklist(
        UUID periodId,
        String period,
        boolean closable,
        List<PreCloseBlocker> blockers
) {
    public PreCloseChecklist {
        blockers = List.copyOf(blockers);
        closable = blockers.isEmpty();
    }
}

package id.pdam.sia.receivable.domain;

import java.util.List;

public enum CollectionActionStatus {
    OPEN,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public static List<CollectionActionStatus> activeStatuses() {
        return List.of(OPEN, IN_PROGRESS);
    }
}

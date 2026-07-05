package id.pdam.sia.receivable.repository;

import id.pdam.sia.receivable.domain.CollectionAction;
import id.pdam.sia.receivable.domain.CollectionActionStatus;
import id.pdam.sia.receivable.domain.CollectionActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.UUID;

public interface CollectionActionRepository extends JpaRepository<CollectionAction, UUID>, JpaSpecificationExecutor<CollectionAction> {
    boolean existsByInvoiceIdAndActionTypeAndStatusIn(
            UUID invoiceId,
            CollectionActionType actionType,
            Collection<CollectionActionStatus> statuses
    );

    boolean existsByCustomerIdAndInvoiceIdIsNullAndActionTypeAndStatusIn(
            UUID customerId,
            CollectionActionType actionType,
            Collection<CollectionActionStatus> statuses
    );
}

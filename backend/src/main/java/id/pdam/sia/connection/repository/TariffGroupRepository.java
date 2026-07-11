package id.pdam.sia.connection.repository;

import id.pdam.sia.connection.domain.TariffGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TariffGroupRepository extends JpaRepository<TariffGroup, UUID> {
    Optional<TariffGroup> findByCode(String code);

    @Query("""
            select tariffGroup from TariffGroup tariffGroup
            where lower(tariffGroup.code) like lower(concat('%', :search, '%'))
               or lower(tariffGroup.name) like lower(concat('%', :search, '%'))
            """)
    Page<TariffGroup> search(@Param("search") String search, Pageable pageable);
}

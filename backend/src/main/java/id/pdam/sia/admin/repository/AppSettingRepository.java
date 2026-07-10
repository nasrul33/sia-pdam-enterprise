package id.pdam.sia.admin.repository;

import id.pdam.sia.admin.domain.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppSettingRepository extends JpaRepository<AppSetting, UUID> {
    Optional<AppSetting> findBySettingKey(String settingKey);
}

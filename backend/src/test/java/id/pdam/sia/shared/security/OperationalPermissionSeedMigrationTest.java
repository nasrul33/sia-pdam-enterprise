package id.pdam.sia.shared.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalPermissionSeedMigrationTest {
    private static final Path MIGRATION_PATH = Path.of(
            "src/main/resources/db/migration/V25__operational_permission_parity.sql"
    );

    private static final List<String> OPERATIONAL_PERMISSIONS = List.of(
            "customer.read",
            "customer.manage",
            "connection.read",
            "connection.manage",
            "meter-route.read",
            "meter-route.manage",
            "meter-reading.read",
            "meter-reading.create",
            "meter-reading.verify",
            "meter-reading.lock",
            "tariff.read",
            "tariff.manage",
            "tariff.calculate",
            "receivable-aging.read",
            "receivable-aging.generate"
    );

    private static final List<String> READ_PERMISSIONS = List.of(
            "customer.read",
            "connection.read",
            "meter-route.read",
            "meter-reading.read",
            "tariff.read",
            "receivable-aging.read"
    );

    @Test
    void migrationSeedsAllOperationalPermissionsIdempotently() throws IOException {
        String migration = migrationSql();

        assertThat(migration)
                .contains(OPERATIONAL_PERMISSIONS.toArray(String[]::new))
                .contains("ON CONFLICT (code) DO UPDATE")
                .contains("ON CONFLICT DO NOTHING")
                .doesNotContain("DROP ")
                .doesNotContain("ALTER TABLE ");
    }

    @Test
    void migrationGrantsOperationalPermissionsUsingLeastPrivilegeRoles() throws IOException {
        String migration = migrationSql();

        for (String permission : OPERATIONAL_PERMISSIONS) {
            assertThat(migration).contains(grant("super-admin", permission));
        }
        for (String permission : READ_PERMISSIONS) {
            assertThat(migration)
                    .contains(grant("auditor-internal", permission))
                    .contains(grant("direksi-manajemen", permission));
        }

        assertThat(migration)
                .contains(
                        grant("petugas-pelanggan", "customer.read"),
                        grant("petugas-pelanggan", "customer.manage"),
                        grant("petugas-pelanggan", "connection.read"),
                        grant("petugas-pelanggan", "connection.manage"),
                        grant("petugas-meter", "meter-route.read"),
                        grant("petugas-meter", "meter-reading.read"),
                        grant("petugas-meter", "meter-reading.create"),
                        grant("petugas-meter", "meter-reading.verify"),
                        grant("supervisor-meter", "meter-route.manage"),
                        grant("supervisor-meter", "meter-reading.verify"),
                        grant("supervisor-meter", "meter-reading.lock"),
                        grant("billing-officer", "tariff.calculate"),
                        grant("billing-supervisor", "tariff.manage"),
                        grant("petugas-piutang", "receivable-aging.generate"),
                        grant("supervisor-piutang", "receivable-aging.generate")
                );
    }

    private static String migrationSql() throws IOException {
        assertThat(MIGRATION_PATH).exists();
        return Files.readString(MIGRATION_PATH);
    }

    private static String grant(String roleCode, String permissionCode) {
        return "('" + roleCode + "', '" + permissionCode + "')";
    }
}

package id.pdam.sia.shared.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TariffComponentMigrationTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V26__tariff_non_air_components.sql"
    );

    @Test
    void migrationAddsComponentSnapshotsAndNonNegativeConstraintsWithoutDestructiveChanges() throws IOException {
        assertThat(MIGRATION).exists();
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("fixed_charge", "levy_charge", "admin_charge", "waste_charge", "penalty_rate")
                .contains("usage_charge", "CHECK", "DEFAULT 0", "penalty_rate <= 1")
                .contains("UPDATE invoices", "SET usage_charge = subtotal")
                .doesNotContain("DROP ", "RENAME ", "TRUNCATE ", "DELETE ");
    }
}

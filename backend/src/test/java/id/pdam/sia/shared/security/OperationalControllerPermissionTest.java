package id.pdam.sia.shared.security;

import id.pdam.sia.billing.web.TariffController;
import id.pdam.sia.connection.web.ConnectionController;
import id.pdam.sia.customer.web.CustomerController;
import id.pdam.sia.metering.web.MeteringController;
import id.pdam.sia.receivable.web.ReceivableAgingController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalControllerPermissionTest {
    @Test
    void permissionsExposeOperationalAuthorityExpressions() {
        assertThat(Permissions.CUSTOMER_READ).isEqualTo("hasAuthority('customer.read')");
        assertThat(Permissions.CUSTOMER_MANAGE).isEqualTo("hasAuthority('customer.manage')");
        assertThat(Permissions.CONNECTION_READ).isEqualTo("hasAuthority('connection.read')");
        assertThat(Permissions.CONNECTION_MANAGE).isEqualTo("hasAuthority('connection.manage')");
        assertThat(Permissions.METER_ROUTE_READ).isEqualTo("hasAuthority('meter-route.read')");
        assertThat(Permissions.METER_ROUTE_MANAGE).isEqualTo("hasAuthority('meter-route.manage')");
        assertThat(Permissions.METER_READING_READ).isEqualTo("hasAuthority('meter-reading.read')");
        assertThat(Permissions.METER_READING_CREATE).isEqualTo("hasAuthority('meter-reading.create')");
        assertThat(Permissions.METER_READING_VERIFY).isEqualTo("hasAuthority('meter-reading.verify')");
        assertThat(Permissions.METER_READING_LOCK).isEqualTo("hasAuthority('meter-reading.lock')");
        assertThat(Permissions.TARIFF_READ).isEqualTo("hasAuthority('tariff.read')");
        assertThat(Permissions.TARIFF_MANAGE).isEqualTo("hasAuthority('tariff.manage')");
        assertThat(Permissions.TARIFF_CALCULATE).isEqualTo("hasAuthority('tariff.calculate')");
        assertThat(Permissions.RECEIVABLE_AGING_READ).isEqualTo("hasAuthority('receivable-aging.read')");
        assertThat(Permissions.RECEIVABLE_AGING_GENERATE).isEqualTo("hasAuthority('receivable-aging.generate')");
    }

    @Test
    void customerAndConnectionEndpointsRequireDomainPermissions() {
        assertPermission(CustomerController.class, "listCustomers", Permissions.CUSTOMER_READ);
        assertPermission(CustomerController.class, "getCustomer", Permissions.CUSTOMER_READ);
        assertPermission(CustomerController.class, "createCustomer", Permissions.CUSTOMER_MANAGE);

        assertPermission(ConnectionController.class, "listTariffGroups", Permissions.TARIFF_READ);
        assertPermission(ConnectionController.class, "createTariffGroup", Permissions.TARIFF_MANAGE);
        assertPermission(ConnectionController.class, "listConnections", Permissions.CONNECTION_READ);
        assertPermission(ConnectionController.class, "getConnection", Permissions.CONNECTION_READ);
        assertPermission(ConnectionController.class, "createConnection", Permissions.CONNECTION_MANAGE);
        assertPermission(ConnectionController.class, "activateConnection", Permissions.CONNECTION_MANAGE);
        assertPermission(ConnectionController.class, "suspendConnection", Permissions.CONNECTION_MANAGE);
        assertPermission(ConnectionController.class, "terminateConnection", Permissions.CONNECTION_MANAGE);
    }

    @Test
    void meteringEndpointsRequireDomainPermissions() {
        assertPermission(MeteringController.class, "listRoutes", Permissions.METER_ROUTE_READ);
        assertPermission(MeteringController.class, "getRoute", Permissions.METER_ROUTE_READ);
        assertPermission(MeteringController.class, "createRoute", Permissions.METER_ROUTE_MANAGE);
        assertPermission(MeteringController.class, "listReadings", Permissions.METER_READING_READ);
        assertPermission(MeteringController.class, "getReading", Permissions.METER_READING_READ);
        assertPermission(MeteringController.class, "createReading", Permissions.METER_READING_CREATE);
        assertPermission(MeteringController.class, "importOfflineReadings", Permissions.METER_READING_CREATE);
        assertPermission(MeteringController.class, "submitReading", Permissions.METER_READING_VERIFY);
        assertPermission(MeteringController.class, "verifyReading", Permissions.METER_READING_VERIFY);
        assertPermission(MeteringController.class, "rejectReading", Permissions.METER_READING_VERIFY);
        assertPermission(MeteringController.class, "lockReading", Permissions.METER_READING_LOCK);
    }

    @Test
    void tariffAndReceivableAgingEndpointsRequireDomainPermissions() {
        assertPermission(TariffController.class, "listVersions", Permissions.TARIFF_READ);
        assertPermission(TariffController.class, "getVersion", Permissions.TARIFF_READ);
        assertPermission(TariffController.class, "listBlocks", Permissions.TARIFF_READ);
        assertPermission(TariffController.class, "createVersion", Permissions.TARIFF_MANAGE);
        assertPermission(TariffController.class, "addBlock", Permissions.TARIFF_MANAGE);
        assertPermission(TariffController.class, "activateVersion", Permissions.TARIFF_MANAGE);
        assertPermission(TariffController.class, "archiveVersion", Permissions.TARIFF_MANAGE);
        assertPermission(TariffController.class, "calculate", Permissions.TARIFF_CALCULATE);

        assertPermission(ReceivableAgingController.class, "listSnapshots", Permissions.RECEIVABLE_AGING_READ);
        assertPermission(ReceivableAgingController.class, "getSnapshot", Permissions.RECEIVABLE_AGING_READ);
        assertPermission(ReceivableAgingController.class, "getSnapshotByPeriod", Permissions.RECEIVABLE_AGING_READ);
        assertPermission(ReceivableAgingController.class, "generateSnapshot", Permissions.RECEIVABLE_AGING_GENERATE);
    }

    private static void assertPermission(Class<?> controllerType, String methodName, String expectedExpression) {
        Method method = Arrays.stream(controllerType.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing controller method: " + methodName));
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation)
                .as(controllerType.getSimpleName() + "." + methodName + " must have @PreAuthorize")
                .isNotNull();
        assertThat(annotation.value()).isEqualTo(expectedExpression);
    }
}

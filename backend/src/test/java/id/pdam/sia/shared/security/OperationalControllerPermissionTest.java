package id.pdam.sia.shared.security;

import id.pdam.sia.billing.application.TariffCalculationResult;
import id.pdam.sia.billing.application.TariffEngineApplicationService;
import id.pdam.sia.billing.domain.TariffVersion;
import id.pdam.sia.billing.web.TariffController;
import id.pdam.sia.connection.application.ConnectionApplicationService;
import id.pdam.sia.connection.web.ConnectionController;
import id.pdam.sia.connection.domain.Connection;
import id.pdam.sia.customer.application.CustomerApplicationService;
import id.pdam.sia.customer.domain.Customer;
import id.pdam.sia.customer.web.CustomerController;
import id.pdam.sia.metering.application.MeteringApplicationService;
import id.pdam.sia.metering.domain.MeterReading;
import id.pdam.sia.metering.domain.MeterRoute;
import id.pdam.sia.metering.web.MeteringController;
import id.pdam.sia.receivable.application.ReceivableAgingApplicationService;
import id.pdam.sia.receivable.domain.ReceivableAgingSnapshot;
import id.pdam.sia.receivable.web.ReceivableAgingController;
import id.pdam.sia.shared.money.CurrencyCode;
import id.pdam.sia.shared.money.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        CustomerController.class,
        ConnectionController.class,
        MeteringController.class,
        TariffController.class,
        ReceivableAgingController.class
})
@Import(SecurityConfig.class)
@ImportAutoConfiguration({
        SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class
})
class OperationalControllerPermissionTest {
    private static final String AUTHORIZED_USER = "authorized-user";
    private static final String REASON = "Authorized operational mutation";
    private static final UUID CUSTOMER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TARIFF_GROUP_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CONNECTION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ROUTE_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID READING_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID TARIFF_VERSION_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerApplicationService customerApplicationService;

    @MockitoBean
    private ConnectionApplicationService connectionApplicationService;

    @MockitoBean
    private MeteringApplicationService meteringApplicationService;

    @MockitoBean
    private TariffEngineApplicationService tariffEngineApplicationService;

    @MockitoBean
    private ReceivableAgingApplicationService receivableAgingApplicationService;

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

    @Test
    void customerMutationEnforcesCustomerManageAtRuntime() throws Exception {
        when(customerApplicationService.createCustomer(any(), eq(AUTHORIZED_USER)))
                .thenReturn(customer());

        assertRuntimeEnforcement(
                () -> jsonPost("/api/customers", """
                        {
                          "customerNumber": "CUS-001",
                          "fullName": "Customer Runtime Test",
                          "identityNumber": "ID-001",
                          "phoneNumber": "08123456789",
                          "addressLine": "Jl. Pengujian 1",
                          "areaCode": "AREA-01",
                          "latitude": -6.2,
                          "longitude": 106.8,
                          "reason": "%s"
                        }
                        """.formatted(REASON)),
                "customer.manage",
                status().isCreated(),
                () -> verify(customerApplicationService).createCustomer(any(), eq(AUTHORIZED_USER)),
                customerApplicationService
        );
    }

    @Test
    void connectionMutationEnforcesConnectionManageAtRuntime() throws Exception {
        when(connectionApplicationService.createConnection(any(), eq(AUTHORIZED_USER)))
                .thenReturn(connection());

        assertRuntimeEnforcement(
                () -> jsonPost("/api/connections", """
                        {
                          "customerId": "%s",
                          "tariffGroupId": "%s",
                          "connectionNumber": "CON-001",
                          "meterNumber": "MTR-001",
                          "installedAt": "2026-07-11",
                          "reason": "%s"
                        }
                        """.formatted(CUSTOMER_ID, TARIFF_GROUP_ID, REASON)),
                "connection.manage",
                status().isCreated(),
                () -> verify(connectionApplicationService).createConnection(any(), eq(AUTHORIZED_USER)),
                connectionApplicationService
        );
    }

    @Test
    void meterRouteMutationEnforcesMeterRouteManageAtRuntime() throws Exception {
        when(meteringApplicationService.createRoute(any(), eq(AUTHORIZED_USER)))
                .thenReturn(new MeterRoute("ROUTE-001", "Route Runtime Test", "AREA-01"));

        assertRuntimeEnforcement(
                () -> jsonPost("/api/meter-routes", """
                        {
                          "routeCode": "ROUTE-001",
                          "name": "Route Runtime Test",
                          "areaCode": "AREA-01",
                          "reason": "%s"
                        }
                        """.formatted(REASON)),
                "meter-route.manage",
                status().isCreated(),
                () -> verify(meteringApplicationService).createRoute(any(), eq(AUTHORIZED_USER)),
                meteringApplicationService
        );
    }

    @Test
    void meterReadingCreateEnforcesMeterReadingCreateAtRuntime() throws Exception {
        when(meteringApplicationService.createReading(any(), eq(AUTHORIZED_USER)))
                .thenReturn(meterReading());

        assertRuntimeEnforcement(
                () -> jsonPost("/api/meter-readings", meterReadingRequest()),
                "meter-reading.create",
                status().isCreated(),
                () -> verify(meteringApplicationService).createReading(any(), eq(AUTHORIZED_USER)),
                meteringApplicationService
        );
    }

    @Test
    void meterReadingVerifyEnforcesMeterReadingVerifyAtRuntime() throws Exception {
        MeterReading reading = meterReading();
        reading.submit();
        when(meteringApplicationService.verifyReading(READING_ID, REASON, AUTHORIZED_USER)).thenReturn(reading);

        assertRuntimeEnforcement(
                () -> jsonPost("/api/meter-readings/%s/verify".formatted(READING_ID), workflowRequest()),
                "meter-reading.verify",
                status().isOk(),
                () -> verify(meteringApplicationService).verifyReading(READING_ID, REASON, AUTHORIZED_USER),
                meteringApplicationService
        );
    }

    @Test
    void meterReadingLockEnforcesMeterReadingLockAtRuntime() throws Exception {
        MeterReading reading = meterReading();
        reading.submit();
        reading.verify();
        when(meteringApplicationService.lockReading(READING_ID, REASON, AUTHORIZED_USER)).thenReturn(reading);

        assertRuntimeEnforcement(
                () -> jsonPost("/api/meter-readings/%s/lock".formatted(READING_ID), workflowRequest()),
                "meter-reading.lock",
                status().isOk(),
                () -> verify(meteringApplicationService).lockReading(READING_ID, REASON, AUTHORIZED_USER),
                meteringApplicationService
        );
    }

    @Test
    void tariffManageMutationEnforcesTariffManageAtRuntime() throws Exception {
        when(tariffEngineApplicationService.createVersion(any(), eq(AUTHORIZED_USER)))
                .thenReturn(new TariffVersion(TARIFF_GROUP_ID, LocalDate.of(2026, 8, 1)));

        assertRuntimeEnforcement(
                () -> jsonPost("/api/tariff-versions", """
                        {
                          "tariffGroupId": "%s",
                          "effectiveDate": "2026-08-01",
                          "reason": "%s"
                        }
                        """.formatted(TARIFF_GROUP_ID, REASON)),
                "tariff.manage",
                status().isCreated(),
                () -> verify(tariffEngineApplicationService).createVersion(any(), eq(AUTHORIZED_USER)),
                tariffEngineApplicationService
        );
    }

    @Test
    void tariffCalculationEnforcesTariffCalculateAtRuntime() throws Exception {
        when(tariffEngineApplicationService.calculate(any())).thenReturn(new TariffCalculationResult(
                TARIFF_VERSION_ID,
                TARIFF_GROUP_ID,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 11),
                new BigDecimal("12.500"),
                List.of(),
                Money.of(new BigDecimal("18000"), CurrencyCode.IDR)
        ));

        assertRuntimeEnforcement(
                () -> jsonPost("/api/tariff-calculations", """
                        {
                          "tariffGroupId": "%s",
                          "billingDate": "2026-07-11",
                          "usageM3": 12.5
                        }
                        """.formatted(TARIFF_GROUP_ID)),
                "tariff.calculate",
                status().isOk(),
                () -> verify(tariffEngineApplicationService).calculate(any()),
                tariffEngineApplicationService
        );
    }

    @Test
    void receivableAgingGenerationEnforcesGenerateAtRuntime() throws Exception {
        when(receivableAgingApplicationService.generateSnapshot(any(), eq(AUTHORIZED_USER)))
                .thenReturn(new ReceivableAgingSnapshot(
                        "2026-07",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        Instant.parse("2026-07-11T00:00:00Z")
                ));

        assertRuntimeEnforcement(
                () -> jsonPost("/api/receivable-aging-snapshots/generate", """
                        {
                          "period": "2026-07",
                          "asOfDate": "2026-07-11",
                          "reason": "%s"
                        }
                        """.formatted(REASON)),
                "receivable-aging.generate",
                status().isCreated(),
                () -> verify(receivableAgingApplicationService).generateSnapshot(any(), eq(AUTHORIZED_USER)),
                receivableAgingApplicationService
        );
    }

    private void assertRuntimeEnforcement(
            Supplier<MockHttpServletRequestBuilder> requestSupplier,
            String requiredAuthority,
            ResultMatcher successfulStatus,
            Runnable successfulInvocation,
            Object... protectedServices
    ) throws Exception {
        mockMvc.perform(requestSupplier.get())
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(protectedServices);

        mockMvc.perform(requestSupplier.get().with(user("wrong-authority")
                        .authorities(new SimpleGrantedAuthority("unrelated.authority"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(protectedServices);

        mockMvc.perform(requestSupplier.get().with(user(AUTHORIZED_USER)
                        .authorities(new SimpleGrantedAuthority(requiredAuthority))))
                .andExpect(successfulStatus);
        successfulInvocation.run();
    }

    private static MockHttpServletRequestBuilder jsonPost(String url, String content) {
        return post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content);
    }

    private static String meterReadingRequest() {
        return """
                {
                  "connectionId": "%s",
                  "routeId": "%s",
                  "period": "2026-07",
                  "previousReading": 10,
                  "currentReading": 22.5,
                  "readAt": "2026-07-11T00:00:00Z",
                  "anomalyFlag": false,
                  "reason": "%s"
                }
                """.formatted(CONNECTION_ID, ROUTE_ID, REASON);
    }

    private static String workflowRequest() {
        return """
                {
                  "reason": "%s"
                }
                """.formatted(REASON);
    }

    private static Customer customer() {
        Customer customer = new Customer("CUS-001", "Customer Runtime Test", "ID-001", "08123456789");
        customer.addAddress("Jl. Pengujian 1", "AREA-01", new BigDecimal("-6.2"), new BigDecimal("106.8"));
        return customer;
    }

    private static Connection connection() {
        return new Connection(CUSTOMER_ID, TARIFF_GROUP_ID, "CON-001", "MTR-001", LocalDate.of(2026, 7, 11));
    }

    private static MeterReading meterReading() {
        return new MeterReading(
                CONNECTION_ID,
                ROUTE_ID,
                "2026-07",
                new BigDecimal("10"),
                new BigDecimal("22.5"),
                Instant.parse("2026-07-11T00:00:00Z"),
                null,
                false,
                null
        );
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

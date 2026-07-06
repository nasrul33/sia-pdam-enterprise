package id.pdam.sia.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class BootstrapAdminUserRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminUserRunner.class);

    private final BootstrapAdminProperties properties;
    private final BootstrapAdminUserService service;

    BootstrapAdminUserRunner(BootstrapAdminProperties properties, BootstrapAdminUserService service) {
        this.properties = properties;
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        BootstrapAdminResult result = service.bootstrap(properties);
        if (result != BootstrapAdminResult.SKIPPED) {
            log.info("Bootstrap admin provisioning result: {}", result);
        }
    }
}

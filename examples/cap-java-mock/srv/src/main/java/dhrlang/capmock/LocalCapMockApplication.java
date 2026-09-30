package dhrlang.capmock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.List;

@SpringBootApplication
public class LocalCapMockApplication {
    public static final String PROFILE = "LOCAL_CAP_MOCK_ADAPTER";

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(LocalCapMockApplication.class);
        application.addInitializers(context -> requireLocalEnvironment(context.getEnvironment()));
        application.run(args);
    }

    static void requireLocalEnvironment(Environment environment) {
        String[] profiles = environment.getActiveProfiles().length == 0
                ? environment.getDefaultProfiles() : environment.getActiveProfiles();
        if (!Arrays.equals(profiles, new String[]{"local-cap-mock"})) {
            throw new IllegalStateException("Only the local-cap-mock profile is supported");
        }
        if (!List.of("127.0.0.1", "::1").contains(environment.getProperty("server.address"))) {
            throw new IllegalStateException("LOCAL_CAP_MOCK_ADAPTER must bind to loopback");
        }
        for (String binding : List.of("VCAP_SERVICES", "VCAP_APPLICATION", "SERVICE_BINDING_ROOT")) {
            String value = environment.getProperty(binding);
            if (value != null && !value.isBlank()) {
                throw new IllegalStateException("Cloud/service bindings are not allowed in LOCAL_CAP_MOCK_ADAPTER");
            }
        }
        if (!environment.getProperty("cds.security.mock.enabled", Boolean.class, false)
                || environment.getProperty("cds.security.mock.defaultUsers", Boolean.class, true)
                || !"always".equals(environment.getProperty("cds.security.authentication.mode"))
                || environment.getProperty("cds.security.xsuaa.enabled", Boolean.class, true)
                || environment.getProperty("cds.security.identity.enabled", Boolean.class, true)) {
            throw new IllegalStateException("The required local-only authentication configuration was changed");
        }
    }
}

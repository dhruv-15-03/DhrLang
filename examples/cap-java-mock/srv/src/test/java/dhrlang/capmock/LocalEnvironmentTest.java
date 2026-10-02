package dhrlang.capmock;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocalEnvironmentTest {
    private static MockEnvironment local() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("server.address", "127.0.0.1")
                .withProperty("cds.security.mock.enabled", "true")
                .withProperty("cds.security.mock.defaultUsers", "false")
                .withProperty("cds.security.authentication.mode", "always")
                .withProperty("cds.security.xsuaa.enabled", "false")
                .withProperty("cds.security.identity.enabled", "false");
        environment.setActiveProfiles("local-cap-mock");
        return environment;
    }

    @Test
    void allowsOnlyTheExplicitLocalProfile() {
        assertDoesNotThrow(() -> LocalCapMockApplication.requireLocalEnvironment(local()));
        for (String[] profiles : List.of(new String[]{"production"}, new String[]{"local-cap-mock", "production"})) {
            MockEnvironment environment = local();
            environment.setActiveProfiles(profiles);
            assertThrows(IllegalStateException.class, () -> LocalCapMockApplication.requireLocalEnvironment(environment));
        }
    }

    @Test
    void refusesCloudBindingsAndNonLoopbackAddressesWithoutReadingSecrets() {
        for (String binding : List.of("VCAP_SERVICES", "VCAP_APPLICATION", "SERVICE_BINDING_ROOT")) {
            MockEnvironment environment = local().withProperty(binding, "synthetic-presence-only");
            assertThrows(IllegalStateException.class, () -> LocalCapMockApplication.requireLocalEnvironment(environment));
        }
        for (String address : List.of("0.0.0.0", "10.0.0.1", "localhost")) {
            MockEnvironment environment = local().withProperty("server.address", address);
            assertThrows(IllegalStateException.class, () -> LocalCapMockApplication.requireLocalEnvironment(environment));
        }
    }

    @Test
    void refusesWeakenedAuthenticationOrDefaultPrivilegedUsers() {
        for (String[] change : List.of(
                new String[]{"cds.security.mock.enabled", "false"},
                new String[]{"cds.security.mock.defaultUsers", "true"},
                new String[]{"cds.security.authentication.mode", "never"},
                new String[]{"cds.security.xsuaa.enabled", "true"},
                new String[]{"cds.security.identity.enabled", "true"})) {
            MockEnvironment environment = local().withProperty(change[0], change[1]);
            assertThrows(IllegalStateException.class, () -> LocalCapMockApplication.requireLocalEnvironment(environment));
        }
    }

    @Test
    void requiresTheMatchingLocalCompilerArtifact() {
        assertDoesNotThrow(() -> CompilerArtifacts.requireMatchingCompiler(Path.of(System.getProperty("dhrlang.compiler-jar"))));
        assertThrows(IOException.class, () -> CompilerArtifacts.requireMatchingCompiler(Path.of("missing-compiler.jar")));
    }
}

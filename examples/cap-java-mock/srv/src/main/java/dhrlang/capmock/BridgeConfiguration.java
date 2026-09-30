package dhrlang.capmock;

import dhrlang.enterprise.PurchaseDecisionRunner;
import dhrlang.enterprise.PurchaseWorkflow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
class BridgeConfiguration {
    @Bean
    Path compilerJar(@Value("${dhrlang.compiler-jar}") String path) throws IOException {
        Path compiler = Path.of(path).toAbsolutePath().normalize();
        CompilerArtifacts.requireMatchingCompiler(compiler);
        return compiler;
    }

    @Bean
    PurchaseWorkflow.DecisionEngine dhrlangDecisionEngine(Path compilerJar) throws IOException {
        return new PurchaseDecisionRunner(compilerJar, PurchaseDecisionRunner.referenceSource());
    }
}

package rip.ysm.port26;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatBoundaryContractTest {
    @Test
    void directTlmReferencesStayInsideCompatBoundary() throws IOException {
        Path root = IdentityContractTest.repoFile("fabric/src/main/java");
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/rip/ysm/compat/touhoulittlemaid/"))
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path, StandardCharsets.UTF_8);
                            assertTrue(!source.contains("com.github.tartaricacid.touhoulittlemaid"),
                                    "TLM reference escaped compat boundary: " + path);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        }
    }
}

package rip.ysm.port26;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceCorpusContractTest {
    @Test
    void builtinYsmCorpusIsPresent() throws IOException {
        Path root = IdentityContractTest.repoFile("common/src/main/resources/assets/yes_steve_model/builtin");
        long files;
        try (Stream<Path> stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".ysm") || path.getFileName().toString().equals("ysm.json"))
                    .count();
        }
        assertTrue(files >= 10, "built-in YSM corpus unexpectedly small: " + files);
    }

    @Test
    void representativeBuiltinsRemainAvailable() {
        assertTrue(Files.isRegularFile(IdentityContractTest.repoFile(
                "common/src/main/resources/assets/yes_steve_model/builtin/wine_fox/22_elf/ysm.json")));
        assertTrue(Files.isRegularFile(IdentityContractTest.repoFile(
                "common/src/main/resources/assets/yes_steve_model/builtin/misc/3_default_boy/ysm.json")));
    }
}

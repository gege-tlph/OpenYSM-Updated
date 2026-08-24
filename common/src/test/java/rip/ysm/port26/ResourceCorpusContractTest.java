package rip.ysm.port26;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceCorpusContractTest {
    private static final long EXPECTED_BUILTIN_FILE_COUNT = 621;
    private static final long EXPECTED_MODEL_DESCRIPTOR_COUNT = 27;

    @Test
    void builtinResourceCorpusKeepsExactFileCount() throws IOException {
        Path root = IdentityContractTest.repoFile("common/src/main/resources/assets/yes_steve_model/builtin");
        try (Stream<Path> stream = Files.walk(root)) {
            long files = stream.filter(Files::isRegularFile).count();
            assertTrue(files == EXPECTED_BUILTIN_FILE_COUNT,
                    "built-in resource corpus changed: expected " + EXPECTED_BUILTIN_FILE_COUNT + ", got " + files);
        }
    }

    @Test
    void builtinYsmCorpusIsPresent() throws IOException {
        Path root = IdentityContractTest.repoFile("common/src/main/resources/assets/yes_steve_model/builtin");
        long files;
        try (Stream<Path> stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".ysm") || path.getFileName().toString().equals("ysm.json"))
                    .count();
        }
        assertTrue(files == EXPECTED_MODEL_DESCRIPTOR_COUNT,
                "built-in model descriptor corpus changed: expected " + EXPECTED_MODEL_DESCRIPTOR_COUNT + ", got " + files);
    }

    @Test
    void representativeBuiltinsRemainAvailable() {
        assertTrue(Files.isRegularFile(IdentityContractTest.repoFile(
                "common/src/main/resources/assets/yes_steve_model/builtin/wine_fox/22_elf/ysm.json")));
        assertTrue(Files.isRegularFile(IdentityContractTest.repoFile(
                "common/src/main/resources/assets/yes_steve_model/builtin/misc/3_default_boy/ysm.json")));
    }
}

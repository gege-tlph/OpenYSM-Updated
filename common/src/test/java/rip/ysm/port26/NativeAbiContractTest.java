package rip.ysm.port26;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeAbiContractTest {
    private static final List<String> NATIVE_FILES = List.of(
            "windows-x64/ysm-core.dll",
            "windows-x86/ysm-core.dll",
            "linux-x64/libysm-core.so",
            "macos-x64/libysm-core.dylib",
            "macos-arm64/libysm-core.dylib",
            "android-arm64/libysm-core.so"
    );

    @Test
    void allSupportedNativeArtifactsExistWithLegacyJniMarkers() throws IOException {
        Path root = IdentityContractTest.repoFile("common/src/main/resources/natives");
        for (String relative : NATIVE_FILES) {
            Path file = root.resolve(relative);
            assertTrue(Files.isRegularFile(file), "missing native artifact: " + relative);
            String bytes = Files.readString(file, StandardCharsets.ISO_8859_1);
            assertTrue(bytes.contains("nInitModelCache"), "missing JNI marker in " + relative);
            assertTrue(bytes.contains("NativeModelRenderer"), "legacy ABI marker changed in " + relative);
        }
    }
}

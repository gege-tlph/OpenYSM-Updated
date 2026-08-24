package rip.ysm.port26;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern ARTIFACT_PATTERN = Pattern.compile(
            "\\\"path\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*"
                    + "\\\"bytes\\\"\\s*:\\s*(\\d+)\\s*,\\s*"
                    + "\\\"sha256\\\"\\s*:\\s*\\\"([0-9a-f]{64})\\\"",
            Pattern.DOTALL);

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

    @Test
    void nativeManifestMatchesAllArtifacts() throws Exception {
        String manifest = Files.readString(IdentityContractTest.repoFile("common/src/main/resources/native-manifest.json"), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("\"formatVersion\": 1"));
        assertTrue(manifest.contains("\"abi\": \"legacy-ysm-core-v1\""));
        assertTrue(manifest.contains("\"nInitModelCache\""));
        assertTrue(manifest.contains("\"NativeModelRenderer\""));

        Map<String, ManifestArtifact> artifacts = new java.util.HashMap<>();
        Matcher matcher = ARTIFACT_PATTERN.matcher(manifest);
        while (matcher.find()) {
            artifacts.put(matcher.group(1), new ManifestArtifact(
                    Long.parseLong(matcher.group(2)), matcher.group(3)));
        }
        assertTrue(artifacts.size() == NATIVE_FILES.size(), "native manifest artifact count changed");

        Path root = IdentityContractTest.repoFile("common/src/main/resources");
        for (String relative : NATIVE_FILES) {
            Path file = root.resolve("natives").resolve(relative);
            ManifestArtifact expected = artifacts.get("natives/" + relative);
            assertTrue(expected != null, "native manifest missing artifact: " + relative);
            byte[] bytes = Files.readAllBytes(file);
            assertTrue(bytes.length == expected.bytes(), "native size mismatch: " + relative);
            assertTrue(sha256Hex(bytes).equals(expected.sha256()), "native SHA-256 mismatch: " + relative);
        }
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record ManifestArtifact(long bytes, String sha256) {
    }
}

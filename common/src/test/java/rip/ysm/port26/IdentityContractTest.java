package rip.ysm.port26;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityContractTest {
    @Test
    void fabricMetadataKeepsOpenYsmIdentity() throws Exception {
        String metadata = readRepoFile("fabric/src/main/resources/fabric.mod.json");
        assertTrue(metadata.contains("\"id\": \"yes_steve_model\""));
        assertTrue(metadata.contains("yes_steve_model.mixins.json"));
        assertTrue(metadata.contains("yes_steve_model_fabric.mixins.json"));
        assertFalse(metadata.contains("sparkle_morpher"));
    }

    @Test
    void resourceAndMixinFilesRemainAtOurPaths() {
        assertTrue(Files.isRegularFile(repoFile("common/src/main/resources/yes_steve_model.mixins.json")));
        assertTrue(Files.isRegularFile(repoFile("fabric/src/main/resources/yes_steve_model_fabric.mixins.json")));
        assertTrue(Files.isDirectory(repoFile("common/src/main/resources/assets/yes_steve_model")));
        assertFalse(Files.exists(repoFile("common/src/main/resources/assets/sparkle_morpher")));
    }

    @Test
    void classicGuiFilesRemainInOpenYsmTree() {
        String guiRoot = "common/src/main/java/com/elfmcys/yesstevemodel/client/gui/";
        for (String file : List.of(
                "PlayerModelScreen.java",
                "PlayerTextureScreen.java",
                "ModelInfoScreen.java",
                "AnimationRouletteScreen.java",
                "ModelUploadScreen.java",
                "DownloadScreen.java",
                "OpenModelFolderScreen.java",
                "ExtraPlayerConfigScreen.java",
                "PauseScreenButtonBuilder.java")) {
            assertTrue(Files.isRegularFile(repoFile(guiRoot + file)), "missing classic GUI file: " + file);
        }
    }

    @Test
    void classicGuiHasKeyboardAndPauseEntrypoints() throws Exception {
        String toggle = readRepoFile("common/src/main/java/com/elfmcys/yesstevemodel/client/input/PlayerModelToggleKey.java");
        assertTrue(toggle.contains("InputConstants.Type.KEYSYM, 89"));
        assertTrue(toggle.contains("new PlayerModelScreen()"));

        String pause = readRepoFile("common/src/main/java/com/elfmcys/yesstevemodel/client/gui/PauseScreenButtonBuilder.java");
        assertTrue(pause.contains("new PlayerModelScreen()"));
        assertTrue(pause.contains("new AnimationRouletteScreen"));
    }

    @Test
    void classicGuiIsTheDefaultPresentation() throws Exception {
        String config = readRepoFile("common/src/main/java/com/elfmcys/yesstevemodel/config/GeneralConfig.java");
        assertTrue(config.contains("defineEnum(\"TextureScreenMode\", TextureScreenMode.CLASSIC)"));
        assertTrue(config.contains("defineEnum(\"ModelInfoScreenMode\", ModelInfoScreenMode.CLASSIC)"));
        assertTrue(config.contains("defineEnum(\"RouletteSettingsMode\", RouletteSettingsMode.CLASSIC)"));
    }

    private static String readRepoFile(String relative) throws Exception {
        return Files.readString(repoFile(relative), StandardCharsets.UTF_8);
    }

    @Test
    void tlmDeclarationMatchesTheFrozenCompatState() throws Exception {
        String metadata = readRepoFile("fabric/src/main/resources/fabric.mod.json");
        // The TLM compat layer is entirely no-op on 26.1.2 (fabric/tlm/** is excluded from
        // compilation, the platform entries return no-ops), and upstream ships no 26.x build.
        // Declaring `breaks: touhou_little_maid` would refuse to load next to a mod we neither
        // integrate with nor conflict with; `suggests` would advertise an ability we do not have.
        assertFalse(metadata.contains("\"breaks\""),
                "no breaks declaration should remain while TLM compat is frozen");
        assertFalse(metadata.contains("touhou_little_maid"),
                "fabric.mod.json must not reference TLM while its integration is frozen");
    }

    @Test
    void loaderFloorMatchesWhatWeBuildAgainst() throws Exception {
        String metadata = readRepoFile("fabric/src/main/resources/fabric.mod.json");
        String properties = readRepoFile("gradle.properties");
        String built = properties.lines()
                .filter(line -> line.startsWith("fabric_loader_version"))
                .map(line -> line.substring(line.indexOf('=') + 1).trim())
                .findFirst()
                .orElseThrow();
        assertTrue(metadata.contains("\"fabricloader\": \">=" + built + "\""),
                "fabric.mod.json loader floor must match the loader we build against (" + built + ")");
    }

    static Path repoFile(String relative) {
        List<Path> starts = new ArrayList<>();
        starts.add(Path.of("").toAbsolutePath());
        starts.add(Path.of(System.getProperty("user.dir")).toAbsolutePath());
        try {
            starts.add(Path.of(IdentityContractTest.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath());
        } catch (Exception ignored) {
            // Gradle may expose a non-file code source; the working-directory probes remain sufficient.
        }
        for (Path start : starts) {
            Path directory = start;
            while (directory != null) {
                Path candidate = directory.resolve(relative);
                if (Files.isRegularFile(directory.resolve("settings.gradle"))
                        || Files.isDirectory(directory.resolve(".git"))) {
                    return candidate;
                }
                directory = directory.getParent();
            }
        }
        throw new IllegalStateException("Repository file not found: " + relative);
    }
}

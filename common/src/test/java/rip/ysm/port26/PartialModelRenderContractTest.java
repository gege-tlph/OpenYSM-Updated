package rip.ysm.port26;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the render-backend selection for partial-model draws.
 *
 * <p>{@code NativeModelRenderer.renderMesh} takes a {@code renderPartMask}: 0 renders the whole
 * model, non-zero renders one subtree (the first-person arm uses LEFT/RIGHT, the background arm
 * uses 3). The accelerated GPU and native SIMD paths only handle the whole-model case correctly;
 * asked for a subset they emit a partial set of faces, which shows up in game as a first-person
 * arm with faces missing. Reproduced 2026-08-29 by switching backends live: the pure-Java path
 * matched vanilla's arm exactly, the accelerated paths did not.
 *
 * <p>Whole-model rendering - every player, vehicle and projectile in the world - keeps the
 * accelerated paths, so this costs nothing measurable: the masked callers draw a handful of bones.
 */
class PartialModelRenderContractTest {
    private static final String RENDERER =
            "common/src/main/java/com/elfmcys/yesstevemodel/geckolib3/geo/NativeModelRenderer.java";

    private static String source() throws Exception {
        return Files.readString(IdentityContractTest.repoFile(RENDERER), StandardCharsets.UTF_8);
    }

    @Test
    void partialModelFlagIsDerivedFromTheRenderPartMask() throws Exception {
        assertTrue(source().contains("boolean partialModel = renderPartMask != 0;"),
                "renderMesh must recognise a partial-model draw from renderPartMask");
    }

    @Test
    void acceleratedPathsAreSkippedForPartialModels() throws Exception {
        String source = source();
        int gpuGatesSeen = 0;
        int simdGatesSeen = 0;
        for (String line : source.split("\n")) {
            String trimmed = line.trim();
            boolean gpuGate = trimmed.startsWith("if (textureLocation != null")
                    && trimmed.contains("USE_GPU_RENDERER");
            boolean simdGate = trimmed.startsWith("if (NativeLibLoader.isLoaded()")
                    && trimmed.contains("isPreview");
            if (gpuGate || simdGate) {
                if (gpuGate) {
                    gpuGatesSeen++;
                } else {
                    simdGatesSeen++;
                }
                assertTrue(trimmed.contains("!partialModel"),
                        "an accelerated render path is reachable for a partial model, which drops "
                                + "faces from the first-person arm: " + trimmed);
            }
        }
        // Without this the loop body can stop matching - both gates are single long lines, and
        // wrapping either one across lines would make the assertions above run zero times and
        // the test pass while the regression it guards is live.
        assertEquals(1, gpuGatesSeen, "did not find the GPU render gate to check; has it been "
                + "reformatted across lines, or renamed? This test cannot pass vacuously.");
        assertEquals(1, simdGatesSeen, "did not find the SIMD render gate to check; has it been "
                + "reformatted across lines, or renamed? This test cannot pass vacuously.");
    }

    @Test
    void theJavaFallbackStillHandlesEveryBoneMask() throws Exception {
        // The Java path is what partial draws now rely on, so its bone filter must stay intact.
        assertTrue(source().contains("renderPartMask != 0 && bone.partMask != renderPartMask"),
                "the Java mesh path must keep filtering bones by renderPartMask");
    }
}

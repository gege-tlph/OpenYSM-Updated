package rip.ysm.port26;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the 26.1.2 two-phase render contract.
 *
 * <p>26.1.2 splits entity rendering: {@code submit()} only queues nodes into a
 * {@code SubmitNodeCollector}, and geometry is written during a later draw phase.
 * The port initially kept the pre-26.1 behaviour of writing vertices straight into
 * the shared {@code BufferSource} and force-flushing it, which drew the replacement
 * model with whatever matrices were bound at flush time: the model appeared pinned
 * to a fixed screen position and remote clients kept seeing the vanilla player.
 *
 * <p>These are source-level assertions on purpose - the failure they guard against is
 * invisible to a headless test and only shows up on another player's screen.
 */
class WorldSubmitPipelineContractTest {
    private static final String GEO_RENDERER =
            "common/src/main/java/com/elfmcys/yesstevemodel/geckolib3/geo/IGeoRenderer.java";
    private static final String PLAYER_EVENT =
            "common/src/main/java/com/elfmcys/yesstevemodel/client/event/ReplacePlayerRenderEvent.java";
    private static final String DISPATCHER_MIXIN =
            "common/src/main/java/com/elfmcys/yesstevemodel/mixin/client/EntityRenderDispatcherMixin.java";

    private static String read(String relative) throws Exception {
        return Files.readString(IdentityContractTest.repoFile(relative), StandardCharsets.UTF_8);
    }

    @Test
    void worldModelsAreSubmittedThroughTheCollector() throws Exception {
        String source = read(GEO_RENDERER);
        assertTrue(source.contains("RenderContext.collector()"),
                "IGeoRenderer must read the active SubmitNodeCollector from RenderContext");
        assertTrue(source.contains("collector.submitCustomGeometry("),
                "world model geometry must be submitted via SubmitNodeCollector#submitCustomGeometry, "
                        + "not written immediately into the shared BufferSource");
    }

    @Test
    void submittedGeometrySnapshotsPoseBuffers() throws Exception {
        String source = read(GEO_RENDERER);
        assertTrue(source.contains("model.getMatrixData().clone()"),
                "bone matrices must be snapshotted at submit time: the draw callback runs later, "
                        + "after the shared pose buffers have been rewritten for the next entity");
        assertTrue(source.contains("model.getAbsPivotData().clone()"),
                "pivot data must be snapshotted at submit time for the same reason");
    }

    @Test
    void immediateModePathRemainsOnlyForCollectorlessRendering() throws Exception {
        String source = read(GEO_RENDERER);
        assertTrue(source.contains("collector != null && vertexConsumer == null"),
                "the collector path must be taken whenever a collector is available; the immediate "
                        + "path is only for GUI/preview rendering, which flushes its own buffer");
    }

    @Test
    void legacyLayerFlushKeepsSkippingTheShadowPass() throws Exception {
        for (String path : new String[]{PLAYER_EVENT, DISPATCHER_MIXIN}) {
            // Comments in both files discuss the shadow pass at length, so a file-wide
            // substring search is satisfied by the prose alone - it would still pass with the
            // guard inverted, which is exactly the defect that shipped in 62c4257. Strip
            // comments and check the condition that actually encloses each endBatch() call.
            String source = stripComments(read(path));
            int from = 0;
            int guarded = 0;
            while (true) {
                int call = source.indexOf("endBatch()", from);
                if (call < 0) {
                    break;
                }
                int ifStart = source.lastIndexOf("if (", call);
                assertTrue(ifStart >= 0, path + ": endBatch() is not inside any if - flushing the "
                        + "shared BufferSource unconditionally breaks the Oculus shadow pass");
                String condition = source.substring(ifStart, call);
                assertTrue(condition.contains("!OculusCompat.isRenderingShadowPass()"),
                        path + ": flushing the shared BufferSource during the Oculus shadow pass "
                                + "makes shader packs cull player geometry out of the shadow map "
                                + "(2026-05-15, 62c4257). The guard must SKIP the shadow pass, not "
                                + "select it. Enclosing condition was: " + condition.trim());
                guarded++;
                from = call + 1;
            }
            assertTrue(guarded > 0 || !source.contains("endBatch"),
                    path + ": expected either a guarded endBatch() or none at all");
        }
    }

    /** Removes block and line comments so assertions see code, not prose about the code. */
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }
}

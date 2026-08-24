package rip.ysm.port26;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolContractTest {
    @Test
    void channelVersionAndIdRemainStable() throws Exception {
        String source = read("common/src/main/java/com/elfmcys/yesstevemodel/network/NetworkHandler.java");
        assertTrue(source.contains("VERSION = \"2.6.0\""));
        assertTrue(source.contains("YesSteveModel.MOD_ID, VERSION.replace('.', '_')"));
        assertTrue(source.contains("yes_steve_model_channel_version"));
    }

    @Test
    void criticalPayloadDiscriminatorsRemainRegistered() throws Exception {
        String source = read("common/src/main/java/com/elfmcys/yesstevemodel/network/NetworkHandler.java");
        assertTrue(source.contains("register(1, S2CModelSyncPayload.class"));
        assertTrue(source.contains("register(2, C2SModelSyncPayload.class"));
        assertTrue(source.contains("register(4, S2CSetModelAndTexturePacket.class"));
        assertTrue(source.contains("register(7, C2SPlayAnimationPacket.class"));
        assertTrue(source.contains("register(70, C2SModelUploadStartPacket.class"));
        assertTrue(source.contains("register(74, S2CModelUploadResultPacket.class"));
    }

    private static String read(String relative) throws Exception {
        return java.nio.file.Files.readString(IdentityContractTest.repoFile(relative), StandardCharsets.UTF_8);
    }
}

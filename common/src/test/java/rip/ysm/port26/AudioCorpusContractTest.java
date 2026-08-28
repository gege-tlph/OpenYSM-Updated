package rip.ysm.port26;

import com.elfmcys.yesstevemodel.audio.NativeAudioDecoder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audio acceptance for the shipped model corpus.
 *
 * <p>Sound cannot be judged from a screenshot, so the G5 audio cluster is closed here
 * instead: every bundled track is checked to be a well-formed Ogg stream in a codec the
 * mod supports, and the Opus tracks are decoded through the shipped decoder. The Vorbis
 * path delegates to vanilla's JOrbisAudioStream, which needs a client runtime, so it is
 * only checked structurally here.
 */
class AudioCorpusContractTest {
    private static final int EXPECTED_TRACK_COUNT = 55;
    private static final int EXPECTED_OPUS_COUNT = 4;

    private static List<Path> audioTracks() throws IOException {
        // Whole namespace, not just builtin: sounds/empty.ogg is the placeholder the
        // sound events fall back to, and it has to stay a decodable stream too.
        Path root = IdentityContractTest.repoFile("common/src/main/resources/assets/yes_steve_model");
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> tracks = new ArrayList<>(stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".ogg"))
                    .toList());
            tracks.sort(Path::compareTo);
            return tracks;
        }
    }

    /** Ogg page header plus the codec identification string of the first packet. */
    private static String codecOf(Path track) throws IOException {
        byte[] head = new byte[64];
        try (var in = Files.newInputStream(track)) {
            int read = in.readNBytes(head, 0, head.length);
            if (read < 32) {
                return "TRUNCATED";
            }
        }
        String magic = new String(head, 0, 4, StandardCharsets.US_ASCII);
        if (!"OggS".equals(magic)) {
            return "NOT_OGG";
        }
        String header = new String(head, 0, head.length, StandardCharsets.ISO_8859_1);
        if (header.contains("OpusHead")) {
            return "OPUS";
        }
        if (header.contains("vorbis")) {
            return "VORBIS";
        }
        return "UNKNOWN";
    }

    @Test
    void everyBundledTrackIsASupportedOggStream() throws IOException {
        List<Path> tracks = audioTracks();
        assertEquals(EXPECTED_TRACK_COUNT, tracks.size(),
                "bundled audio corpus changed: expected " + EXPECTED_TRACK_COUNT + " .ogg files");

        List<String> unsupported = new ArrayList<>();
        int opus = 0;
        for (Path track : tracks) {
            String codec = codecOf(track);
            if ("OPUS".equals(codec)) {
                opus++;
            } else if (!"VORBIS".equals(codec)) {
                unsupported.add(track.getFileName() + " -> " + codec);
            }
        }
        assertTrue(unsupported.isEmpty(), "unsupported audio in the shipped corpus: " + unsupported);
        assertEquals(EXPECTED_OPUS_COUNT, opus, "Opus track count in the shipped corpus changed");
    }

    @Test
    void opusTracksDecodeThroughTheShippedDecoder() throws IOException {
        List<Path> tracks = audioTracks();
        int decoded = 0;
        for (Path track : tracks) {
            if (!"OPUS".equals(codecOf(track))) {
                continue;
            }
            byte[] bytes = Files.readAllBytes(track);
            // The decoder requires a direct buffer, as it does in production.
            ByteBuffer input = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
            input.put(bytes).flip();

            NativeAudioDecoder decoder = new NativeAudioDecoder();
            try {
                assertTrue(decoder.openStream(input), "failed to open Opus stream: " + track.getFileName());
                ByteBuffer output = ByteBuffer.allocateDirect(64 * 1024).order(ByteOrder.nativeOrder());
                int produced = decoder.decodeFrame(output);
                assertTrue(produced > 0, "decoder produced no PCM for " + track.getFileName());
                decoded++;
            } finally {
                decoder.destroy();
            }
        }
        assertEquals(EXPECTED_OPUS_COUNT, decoded, "not every Opus track was decoded");
    }
}

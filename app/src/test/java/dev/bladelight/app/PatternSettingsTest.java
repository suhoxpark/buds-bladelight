package dev.bladelight.app;

import java.util.Arrays;

public final class PatternSettingsTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void rejects(Runnable operation) {
        try { operation.run(); throw new AssertionError("Invalid input accepted"); }
        catch (IllegalArgumentException | IllegalStateException expected) { }
    }
    static byte[] frame(int bits, int style, int advanced) {
        byte[] frame = new byte[68];
        frame[0] = (byte) 0xfd; frame[1] = 64; frame[3] = 0x61; frame[4] = 4;
        frame[14] = (byte) bits; frame[55] = (byte) style; frame[56] = (byte) advanced;
        checksum(frame); return frame;
    }
    static void checksum(byte[] frame) {
        int crc = Protocol.crc(frame, 3, frame.length - 6);
        frame[frame.length - 3] = (byte) crc; frame[frame.length - 2] = (byte) (crc >>> 8);
        frame[frame.length - 1] = (byte) 0xdd;
    }
    public static void main(String[] args) {
        int[] order = {7, 3, 2, 1, 0, 4, 5, 6};
        for (int bits = 0; bits < 256; bits++) {
            final int value = bits;
            PatternSettings snapshot = PatternSettings.fromFrame(frame(bits, 3, 0));
            if ((bits & 0x81) != 0x81) { rejects(() -> snapshot.frameForStyle(2)); continue; }
            byte[] write = snapshot.frameForStyle(2);
            check(write.length == 17 && (write[3] & 255) == 0x90, "frame shape");
            for (int i = 0; i < 8; i++) check(write[4 + i] == ((value >>> order[i]) & 1), "touch mapping");
            check(write[12] == 2 && write[13] == 0, "target and advanced");
            check(Protocol.crc(write, 3, 11) == ((write[14] & 255) | ((write[15] & 255) << 8)), "CRC");
        }
        byte[] source = frame(255, 3, 0);
        PatternSettings original = PatternSettings.fromFrame(source);
        source[14] = 0;
        check(original.controlBits() == 255, "snapshot must copy source");
        check(original.sameControls(PatternSettings.fromFrame(frame(255, 1, 0))), "style is not a touch control");
        check(!original.sameControls(PatternSettings.fromFrame(frame(254, 3, 0))), "detect control change");
        check(!original.sameControls(null), "null");
        rejects(() -> PatternSettings.fromFrame(null));
        rejects(() -> PatternSettings.fromFrame(Arrays.copyOf(frame(255, 3, 0), 67)));
        byte[] bad = frame(255, 3, 0); bad[55] ^= 1; byte[] wrongCrc = bad; rejects(() -> PatternSettings.fromFrame(wrongCrc));
        bad = frame(255, 3, 0); bad[0] = 0; byte[] wrongStart = bad; rejects(() -> PatternSettings.fromFrame(wrongStart));
        bad = frame(255, 3, 0); bad[2] = 0x20; byte[] metadata = bad; rejects(() -> PatternSettings.fromFrame(metadata));
        bad = frame(255, 3, 0); bad[4] = 5; checksum(bad); byte[] revision = bad; rejects(() -> PatternSettings.fromFrame(revision));
        rejects(() -> PatternSettings.fromFrame(frame(255, 0, 0)).frameForStyle(2));
        rejects(() -> PatternSettings.fromFrame(frame(255, 3, 0)).frameForStyle(4));
        rejects(() -> PatternSettings.fromFrame(frame(255, 3, 1)).frameForStyle(2));
        System.out.println("PatternSettingsTest passed");
    }
}

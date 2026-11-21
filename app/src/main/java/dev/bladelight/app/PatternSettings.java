package dev.bladelight.app;

/** Immutable saved settings from a validated R630 revision-4 status frame.
 * These settings do not report whether either physical light is on. */
public final class PatternSettings {
    private static final int PAYLOAD_LENGTH = 61;
    private static final int[] CONTROL_BITS = {7, 3, 2, 1, 0, 4, 5, 6};
    private final byte[] frame;

    private PatternSettings(byte[] frame) { this.frame = frame.clone(); }

    public static PatternSettings fromFrame(byte[] raw) {
        if (raw == null || raw.length != PAYLOAD_LENGTH + 7)
            throw new IllegalArgumentException("unexpected_snapshot_length");
        if ((raw[0] & 0xff) != 0xfd || (raw[raw.length - 1] & 0xff) != 0xdd)
            throw new IllegalArgumentException("invalid_snapshot_boundary");
        int length = (raw[1] & 0xff) | ((raw[2] & 3) << 8);
        if (length != PAYLOAD_LENGTH + 3 || (raw[2] & 0x20) != 0 || (raw[3] & 0xff) != 0x61)
            throw new IllegalArgumentException("unexpected_snapshot_format");
        int expected = (raw[raw.length - 3] & 0xff) | ((raw[raw.length - 2] & 0xff) << 8);
        if (Protocol.crc(raw, 3, PAYLOAD_LENGTH + 1) != expected)
            throw new IllegalArgumentException("invalid_snapshot_crc");
        if ((raw[4] & 0xff) != 4) throw new IllegalArgumentException("unsupported_snapshot_revision");
        return new PatternSettings(raw);
    }

    public int style() { return frame[55] & 0xff; }
    public int controlBits() { return frame[14] & 0xff; }
    public int advanced() { return frame[56] & 0xff; }

    /** Check the observed source and target before constructing or journaling a write. */
    public byte[] frameForStyle(int targetStyle) {
        if (style() < 1 || style() > 3 || targetStyle < 1 || targetStyle > 3)
            throw new IllegalArgumentException("unsupported_style");
        if ((controlBits() & 0x81) != 0x81)
            throw new IllegalStateException("touch_settings_would_be_normalized");
        if (advanced() != 0) throw new IllegalStateException("unsupported_advanced_controls");
        byte[] result = new byte[17];
        result[0] = (byte) 0xfd; result[1] = 13; result[3] = (byte) 0x90;
        for (int i = 0; i < CONTROL_BITS.length; i++) result[4 + i] = (byte) ((controlBits() >>> CONTROL_BITS[i]) & 1);
        result[12] = (byte) targetStyle; result[13] = (byte) advanced();
        int crc = Protocol.crc(result, 3, 11);
        result[14] = (byte) crc; result[15] = (byte) (crc >>> 8); result[16] = (byte) 0xdd;
        return result;
    }

    public boolean sameControls(PatternSettings other) {
        return other != null && controlBits() == other.controlBits() && advanced() == other.advanced();
    }
}

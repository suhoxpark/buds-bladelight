package dev.bladelight.app;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Samsung SPP framing; this does not establish the meaning of a lighting value. */
public final class Protocol {
    private Protocol() {}
    public static int crc(byte[] bytes, int offset, int length) {
        int value = 0;
        for (int i = offset; i < offset + length; i++) {
            value ^= (bytes[i] & 0xff) << 8;
            for (int b = 0; b < 8; b++) value = ((value & 0x8000) != 0 ? (value << 1) ^ 0x1021 : value << 1) & 0xffff;
        }
        return value;
    }
    public static byte[] lighting(int value) {
        if (value != 0 && value != 1) throw new IllegalArgumentException("unsupported lighting value");
        byte[] frame = {(byte) 0xfd, 4, 0, (byte) 0x8e, (byte) value, 0, 0, (byte) 0xdd};
        int crc = crc(frame, 3, 2);
        frame[5] = (byte) crc;
        frame[6] = (byte) (crc >>> 8);
        return frame;
    }
    private static byte[] frame(int id, byte[] payload) {
        int length = payload.length + 3;
        byte[] frame = new byte[payload.length + 7];
        frame[0] = (byte) 0xfd; frame[1] = (byte) length; frame[2] = (byte) (length >>> 8);
        frame[3] = (byte) id;
        System.arraycopy(payload, 0, frame, 4, payload.length);
        int crc = crc(frame, 3, payload.length + 1);
        frame[frame.length - 3] = (byte) crc; frame[frame.length - 2] = (byte) (crc >>> 8);
        frame[frame.length - 1] = (byte) 0xdd;
        return frame;
    }
    public static byte[] diagnosticMode(boolean enabled) { return frame(0x12, new byte[]{(byte) (enabled ? 1 : 0)}); }
    public static byte[] diagnosticLed(boolean enabled) {
        return frame(0x13, new byte[]{'F', 'F', 'F', 'E', '"', (byte) (enabled ? '1' : '0'), '"'});
    }
    /** One outstanding same-connection exchange; the protocol provides no request sequence number. */
    public static final class ResponseGate {
        private static final Pattern LED_OK = Pattern.compile("(?:^|[\\r\\n])(?:[LR]\\+)?LEDTEST:[^\\r\\n]{0,80},OK(?:[\\r\\n]|$)");
        private static final Pattern LED_ERROR = Pattern.compile("(?:ERROR|FAIL|(?:^|[^A-Z])NG(?:[^A-Z]|$))");
        public static final class Ticket {
            private final long session;
            private final int id, value;
            private final StringBuilder text = new StringBuilder();
            private boolean done;
            private String failure;
            private Ticket(long session, int id, int value) { this.session = session; this.id = id; this.value = value; }
        }
        private Ticket pending;
        public synchronized Ticket arm(long session, int id, int value) {
            if (pending != null) throw new IllegalStateException("exchange already pending");
            if (id != 0x12 && id != 0x13) throw new IllegalArgumentException("unsupported exchange");
            return pending = new Ticket(session, id, value);
        }
        public synchronized void accept(long session, byte[] frame) {
            Ticket ticket = pending;
            if (ticket == null || ticket.done || ticket.session != session || frame.length < 7) return;
            int id = frame[3] & 255;
            int flags = (frame[2] & 255) << 8;
            if (id != ticket.id || (flags & 0x2000) != 0) return;
            if (id == 0x12) {
                if (frame.length == 8 && (frame[4] & 255) == ticket.value) ticket.done = true;
            } else {
                int count = Math.min(frame.length - 7, 512 - ticket.text.length());
                if (count > 0) ticket.text.append(new String(frame, 4, count, StandardCharsets.US_ASCII).toUpperCase(Locale.ROOT));
                String text = ticket.text.toString();
                if (LED_ERROR.matcher(text).find()) { ticket.failure = "LEDTEST 오류 응답"; ticket.done = true; }
                else if (LED_OK.matcher(text).find()) ticket.done = true;
                else if (ticket.text.length() >= 512) { ticket.failure = "LEDTEST 응답 길이 초과"; ticket.done = true; }
            }
            if (ticket.done) notifyAll();
        }
        public synchronized void await(Ticket ticket, long timeoutMillis) throws Exception {
            if (pending != ticket) throw new IllegalStateException("stale exchange");
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (!ticket.done) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new java.util.concurrent.TimeoutException("진단 응답 시간 초과");
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
                if (pending != ticket) throw new IllegalStateException("exchange cancelled");
            }
            if (ticket.failure != null) throw new IllegalStateException(ticket.failure);
        }
        public synchronized void cancel(Ticket ticket) {
            if (pending == ticket) { pending = null; notifyAll(); }
        }
    }
    /** The two cleanup writes are attempted independently even when any earlier operation fails. */
    public interface TrialOps {
        void write(byte[] frame, String label) throws Exception;
        void pause(long millis) throws Exception;
        void phase(String text);
        void cleanupError(String label, Exception error);
    }
    public static void runTrial(TrialOps ops) throws Exception {
        try {
            ops.write(diagnosticMode(true), "진단 모드 진입");
            ops.pause(300);
            ops.write(diagnosticLed(true), "LED 진단 켜기");
            for (int seconds = 5; seconds > 0; seconds--) {
                ops.phase("LED 진단 유지 " + seconds + "초 (실제 점등 확인 필요)");
                ops.pause(1000);
            }
        } finally {
            try { ops.write(diagnosticLed(false), "LED 진단 끄기"); }
            catch (Exception e) { ops.cleanupError("LED 진단 끄기", e); }
            exitMode(ops);
        }
    }
    /** Try a persistent LED state, exit diagnostic mode, then leave connection management to caller. */
    public static void runPersistentTrial(TrialOps ops, boolean enabled) throws Exception {
        boolean completed = false;
        try {
            ops.write(diagnosticMode(true), "진단 모드 진입");
            ops.pause(300);
            ops.write(diagnosticLed(enabled), enabled ? "LED 진단 켜기" : "LED 진단 끄기");
            ops.pause(300);
            completed = true;
        } finally {
            if (enabled && !completed) {
                try { ops.write(diagnosticLed(false), "LED 진단 끄기 (롤백)"); }
                catch (Exception e) { ops.cleanupError("LED 진단 끄기 (롤백)", e); }
            }
            exitMode(ops);
        }
    }
    private static void exitMode(TrialOps ops) {
        try { ops.write(diagnosticMode(false), "진단 모드 종료"); }
        catch (Exception first) {
            ops.phase("진단 모드 종료 응답 미확인: 한 번 재시도합니다.");
            try { ops.write(diagnosticMode(false), "진단 모드 종료"); }
            catch (Exception second) { ops.cleanupError("진단 모드 종료", second); }
        }
    }
    public interface Listener {
        void frame(byte[] frame);
        void invalid(String reason);
    }
    /** Streaming decoder tolerates fragmentation, concatenation and noise without unbounded buffering. */
    public static final class Decoder {
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
        private final Listener listener;
        public Decoder(Listener listener) { this.listener = listener; }
        public void accept(byte[] data, int length) {
            if (length < 0 || length > data.length) throw new IllegalArgumentException();
            for (int i = 0; i < length; i++) {
                int v = data[i] & 255;
                if (pending.size() == 0 && v != 0xfd) continue;
                pending.write(v);
                byte[] bytes = pending.toByteArray();
                if (bytes.length < 3) continue;
                int payloadLength = ((bytes[1] & 255) | ((bytes[2] & 3) << 8));
                if (payloadLength < 3) { resetWithPossibleStart(v); listener.invalid("잘못된 길이"); continue; }
                int total = payloadLength + 4;
                if (bytes.length < total) continue;
                if (v != 0xdd) { resetWithPossibleStart(v); listener.invalid("종료 바이트 오류"); continue; }
                int expected = (bytes[total - 3] & 255) | ((bytes[total - 2] & 255) << 8);
                int actual = crc(bytes, 3, total - 6);
                pending.reset();
                if (actual != expected) listener.invalid("CRC 오류");
                else listener.frame(Arrays.copyOf(bytes, bytes.length));
            }
        }
        private void resetWithPossibleStart(int value) {
            pending.reset();
            if (value == 0xfd) pending.write(value);
        }
    }
    public static String hex(byte[] data) {
        StringBuilder out = new StringBuilder();
        for (byte b : data) {
            if (out.length() > 0) out.append(' ');
            out.append(String.format(java.util.Locale.ROOT, "%02X", b & 255));
        }
        return out.toString();
    }
}

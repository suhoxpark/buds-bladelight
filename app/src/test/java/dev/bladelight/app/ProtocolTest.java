package dev.bladelight.app;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Standalone tests: compile Protocol.java and this class with javac, then run with java -ea. */
public final class ProtocolTest {
    private static void check(boolean value, String description) { if (!value) throw new AssertionError(description); }
    private static byte[] response(int id, String text) {
        byte[] payload = text.getBytes(StandardCharsets.US_ASCII);
        byte[] frame = new byte[payload.length + 7];
        frame[0] = (byte) 0xfd; frame[1] = (byte) (payload.length + 3); frame[3] = (byte) id;
        System.arraycopy(payload, 0, frame, 4, payload.length);
        int crc = Protocol.crc(frame, 3, payload.length + 1);
        frame[frame.length - 3] = (byte) crc; frame[frame.length - 2] = (byte) (crc >>> 8); frame[frame.length - 1] = (byte) 0xdd;
        return frame;
    }
    public static void main(String[] args) throws Exception {
        byte[] vector = "123456789".getBytes(StandardCharsets.US_ASCII);
        check(Protocol.crc(vector, 0, vector.length) == 0x31c3, "CRC-16/XMODEM known vector");
        check(Protocol.hex(Protocol.lighting(0)).equals("FD 04 00 8E 00 97 38 DD"), "OFF frame");
        check(Protocol.hex(Protocol.lighting(1)).equals("FD 04 00 8E 01 B6 28 DD"), "candidate frame");
        check(Protocol.hex(Protocol.diagnosticMode(true)).equals("FD 04 00 12 01 30 75 DD"), "diagnostic mode on");
        check(Protocol.hex(Protocol.diagnosticLed(true)).equals("FD 0A 00 13 46 46 46 45 22 31 22 EF BA DD"), "diagnostic LED on");
        check(Protocol.hex(Protocol.diagnosticLed(false)).equals("FD 0A 00 13 46 46 46 45 22 30 22 DE 89 DD"), "diagnostic LED off");
        check(Protocol.hex(Protocol.diagnosticMode(false)).equals("FD 04 00 12 00 11 65 DD"), "diagnostic mode off");
        for (int failAt = 0; failAt <= 4; failAt++) {
            final int injected = failAt;
            List<String> writes = new ArrayList<>();
            List<String> cleanupErrors = new ArrayList<>();
            Protocol.TrialOps trial = new Protocol.TrialOps() {
                int count;
                public void write(byte[] bytes, String label) throws Exception {
                    writes.add(Protocol.hex(bytes));
                    if (++count == injected) throw new Exception("injected");
                }
                public void pause(long millis) {}
                public void phase(String text) {}
                public void cleanupError(String label, Exception error) { cleanupErrors.add(label); }
            };
            try { Protocol.runTrial(trial); } catch (Exception expected) { check(injected == 1 || injected == 2, "only initial write failure propagates"); }
            check(writes.contains(Protocol.hex(Protocol.diagnosticLed(false))), "cleanup LED off after failure " + injected);
            check(writes.get(writes.size() - 1).equals(Protocol.hex(Protocol.diagnosticMode(false))), "cleanup mode off after failure " + injected);
            check(cleanupErrors.size() == (injected == 3 ? 1 : 0), "cleanup error recorded or retry succeeded");
        }
        for (boolean enabled : new boolean[]{true, false}) {
            for (int failAt = 0; failAt <= 2; failAt++) {
                final int injected = failAt;
                List<String> writes = new ArrayList<>();
                List<Long> pauses = new ArrayList<>();
                Protocol.TrialOps trial = new Protocol.TrialOps() {
                    int count;
                    public void write(byte[] bytes, String label) throws Exception {
                        writes.add(Protocol.hex(bytes));
                        if (++count == injected) throw new Exception("injected");
                    }
                    public void pause(long millis) { pauses.add(millis); }
                    public void phase(String text) {}
                    public void cleanupError(String label, Exception error) {}
                };
                try { Protocol.runPersistentTrial(trial, enabled); }
                catch (Exception expected) { check(injected != 0, "unexpected persistent failure"); }
                check(writes.get(0).equals(Protocol.hex(Protocol.diagnosticMode(true))), "persistent enters mode");
                check(writes.get(writes.size() - 1).equals(Protocol.hex(Protocol.diagnosticMode(false))), "persistent exits mode on all outcomes");
                if (enabled && injected != 0) check(writes.get(writes.size() - 2).equals(Protocol.hex(Protocol.diagnosticLed(false))), "failed ON attempts LED off");
                if (injected == 0) {
                    check(writes.size() == 3 && writes.get(1).equals(Protocol.hex(Protocol.diagnosticLed(enabled))), "persistent requested state only");
                    check(pauses.size() == 2 && pauses.stream().allMatch(ms -> ms == 300), "persistent settling delays");
                }
            }
        }
        List<String> interruptedWrites = new ArrayList<>();
        Protocol.TrialOps interrupted = new Protocol.TrialOps() {
            int pauses;
            public void write(byte[] bytes, String label) { interruptedWrites.add(Protocol.hex(bytes)); }
            public void pause(long millis) throws InterruptedException { if (++pauses == 2) throw new InterruptedException("cancel"); }
            public void phase(String text) {}
            public void cleanupError(String label, Exception error) {}
        };
        try { Protocol.runPersistentTrial(interrupted, true); throw new AssertionError("interruption not propagated"); }
        catch (InterruptedException expected) { /* cleanup follows interruption */ }
        check(interruptedWrites.get(2).equals(Protocol.hex(Protocol.diagnosticLed(false))), "cancelled ON attempts LED off");
        check(interruptedWrites.get(3).equals(Protocol.hex(Protocol.diagnosticMode(false))), "cancelled ON exits mode");
        List<String> retries = new ArrayList<>(); List<String> retryErrors = new ArrayList<>();
        Protocol.runPersistentTrial(new Protocol.TrialOps() {
            public void write(byte[] bytes, String label) throws Exception {
                retries.add(label);
                if (label.equals("진단 모드 종료")) throw new java.util.concurrent.TimeoutException("injected");
            }
            public void pause(long millis) {}
            public void phase(String text) {}
            public void cleanupError(String label, Exception error) { retryErrors.add(label); }
        }, true);
        check(retries.stream().filter("진단 모드 종료"::equals).count() == 2, "mode exit retries exactly once");
        check(retryErrors.equals(java.util.List.of("진단 모드 종료")), "mode exit failure surfaced");
        Protocol.ResponseGate gate = new Protocol.ResponseGate();
        Protocol.ResponseGate.Ticket mode = gate.arm(11, 0x12, 1);
        gate.accept(11, Protocol.diagnosticMode(true));
        gate.await(mode, 100); gate.cancel(mode); // immediate response arrives before await
        Protocol.ResponseGate.Ticket led = gate.arm(12, 0x13, -1);
        Thread delayed = new Thread(() -> {
            try { Thread.sleep(40); } catch (InterruptedException e) { throw new AssertionError(e); }
            gate.accept(12, response(0x13, "L+LEDTEST:2,OK\r\n\r\nOK\r\n"));
        });
        delayed.start(); gate.await(led, 1000); gate.cancel(led); delayed.join();
        Protocol.ResponseGate.Ticket mismatch = gate.arm(20, 0x12, 1);
        gate.accept(19, Protocol.diagnosticMode(true));
        gate.accept(20, Protocol.diagnosticMode(false));
        byte[] fragmented = Protocol.diagnosticMode(true); fragmented[2] = 0x20;
        gate.accept(20, fragmented);
        try { gate.await(mismatch, 30); throw new AssertionError("mismatched session or fragment accepted"); }
        catch (java.util.concurrent.TimeoutException expected) { } finally { gate.cancel(mismatch); }
        Protocol.ResponseGate.Ticket failed = gate.arm(30, 0x13, -1);
        gate.accept(30, response(0x13, "L+LEDTEST:2,ERROR\r\nOK\r\n"));
        try { gate.await(failed, 30); throw new AssertionError("negative response accepted"); }
        catch (IllegalStateException expected) { } finally { gate.cancel(failed); }
        Protocol.ResponseGate.Ticket cancelled = gate.arm(40, 0x12, 1);
        Thread cancelThread = new Thread(() -> { try { Thread.sleep(30); } catch (InterruptedException e) { throw new AssertionError(e); } gate.cancel(cancelled); });
        cancelThread.start();
        try { gate.await(cancelled, 1000); throw new AssertionError("cancel ignored"); }
        catch (IllegalStateException expected) { } finally { gate.cancel(cancelled); }
        cancelThread.join();
        List<byte[]> frames = new ArrayList<>(); List<String> errors = new ArrayList<>();
        Protocol.Decoder decoder = new Protocol.Decoder(new Protocol.Listener() {
            public void frame(byte[] value) { frames.add(value); }
            public void invalid(String reason) { errors.add(reason); }
        });
        byte[] off = Protocol.lighting(0), on = Protocol.lighting(1);
        decoder.accept(new byte[]{9, off[0], off[1]}, 3);
        byte[] rest = new byte[off.length - 2 + on.length];
        System.arraycopy(off, 2, rest, 0, off.length - 2);
        System.arraycopy(on, 0, rest, off.length - 2, on.length);
        decoder.accept(rest, rest.length);
        check(frames.size() == 2 && Protocol.hex(frames.get(0)).equals(Protocol.hex(off)), "fragmented and concatenated frames");
        byte[] bad = off.clone(); bad[4] = 1;
        decoder.accept(bad, bad.length);
        check(errors.size() == 1 && frames.size() == 2, "bad CRC rejected");
        decoder.accept(off, off.length);
        check(frames.size() == 3, "decoder recovers after bad CRC");
        System.out.println("Protocol tests passed");
    }
}

package dev.bladelight.app;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PatternTransactionTest {
    private static final String ADDRESS = "registered-buds";
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static PatternSettings state(int bits, int style, int advanced) {
        return PatternSettings.fromFrame(PatternSettingsTest.frame(bits, style, advanced));
    }
    static final class Fixture implements PatternTransaction.Journal, PatternTransaction.Transport {
        final List<String> steps = new ArrayList<>();
        PatternTransaction.Pending pending;
        PatternSettings current = state(255, 3, 0);
        boolean failMark, failClear, failWrite, failReadback, changeControls, ignoreWrite;
        int reads, writes, connections;
        public PatternTransaction.Pending pending() { return pending; }
        public void mark(PatternTransaction.Pending value) throws Exception {
            steps.add("mark"); if (failMark) throw new IOException("mark"); pending = value;
        }
        public void clear(String address) throws Exception {
            steps.add("clear"); if (failClear) throw new IOException("clear");
            check(pending != null && address.equals(pending.address), "clear address"); pending = null;
        }
        public PatternTransaction.Session connect(String address) {
            check(address.equals(ADDRESS), "target"); connections++; steps.add("connect");
            return new PatternTransaction.Session() {
                public PatternSettings snapshot() throws Exception {
                    steps.add("snapshot"); reads++;
                    if (failReadback && reads > 1) throw new IOException("readback");
                    return current;
                }
                public void writeStyle(PatternSettings source, int target) throws Exception {
                    steps.add("write"); writes++;
                    check(pending != null, "journal first");
                    source.frameForStyle(target);
                    if (!ignoreWrite) current = state(changeControls ? 0x81 : source.controlBits(), target, 0);
                    if (failWrite) throw new IOException("socket dropped after uncertain write");
                }
                public void close() { steps.add("close"); }
            };
        }
        public void settle() { steps.add("settle"); }
        PatternTransaction transaction() { return new PatternTransaction(this, this); }
    }
    public static void main(String[] args) {
        Fixture f = new Fixture();
        PatternTransaction.Result r = f.transaction().set(ADDRESS, 2);
        check(r.success && !r.pending && r.snapshot.style() == 2, "verified write");
        check(f.steps.equals(Arrays.asList("connect", "snapshot", "mark", "write", "close", "settle", "connect", "snapshot", "close", "clear")), "fresh readback order");

        f = new Fixture(); f.failMark = true; r = f.transaction().set(ADDRESS, 2);
        check(!r.success && !r.pending && f.writes == 0, "no write when journal fails");
        f = new Fixture(); f.failWrite = true; r = f.transaction().set(ADDRESS, 2);
        check(r.success && !r.pending && f.writes == 1 && f.reads == 2, "uncertain write resolved by readback");
        f = new Fixture(); f.ignoreWrite = true; r = f.transaction().set(ADDRESS, 2);
        check(!r.success && !r.pending && r.snapshot.style() == 3 && f.writes == 1, "mismatch without replay");
        f = new Fixture(); f.changeControls = true; r = f.transaction().set(ADDRESS, 2);
        check(!r.success && !r.pending && r.snapshot.controlBits() == 0x81 && f.writes == 1, "concurrent touch change reported");
        f = new Fixture(); f.failReadback = true; r = f.transaction().set(ADDRESS, 2);
        check(!r.success && r.pending && f.writes == 1, "readback failure retains journal");
        f.failReadback = false; r = f.transaction().recover();
        check(r.success && !r.pending && f.writes == 1, "recovery only reads");
        f = new Fixture(); f.failClear = true; r = f.transaction().set(ADDRESS, 2);
        check(!r.success && r.pending && r.snapshot.style() == 2, "clear failure retains journal");
        f = new Fixture(); f.current = state(0x80, 3, 0); r = f.transaction().set(ADDRESS, 2);
        check(!r.success && !r.pending && f.writes == 0, "unsafe mask rejected");
        f = new Fixture(); f.current = state(255, 3, 1); r = f.transaction().set(ADDRESS, 2);
        check(!r.success && !r.pending && f.writes == 0, "advanced controls rejected");
        f = new Fixture(); r = f.transaction().set(ADDRESS, 3);
        check(r.success && f.writes == 0 && f.pending == null, "same style no-op");
        f = new Fixture(); f.pending = new PatternTransaction.Pending(ADDRESS, 2, 255, 0); f.failReadback = true;
        // First fresh read resolves to mismatch. The requested new set must stop rather than overwrite it.
        r = f.transaction().set(ADDRESS, 1);
        check(!r.success && !r.pending && f.writes == 0 && r.snapshot.style() == 3, "pending mismatch stops next set");
        System.out.println("PatternTransactionTest passed");
    }
}

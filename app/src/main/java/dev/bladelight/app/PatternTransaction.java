package dev.bladelight.app;

/** One compound-settings transaction. All calls must be serialized by the owner.
 * A pending write is reconciled by reading, never by replaying or rolling back. */
public final class PatternTransaction {
    public static final class Pending {
        public final String address;
        public final int targetStyle, controlBits, advanced;
        public Pending(String address, int targetStyle, int controlBits, int advanced) {
            if (address == null || address.isEmpty() || targetStyle < 1 || targetStyle > 3 ||
                    controlBits < 0 || controlBits > 255 || advanced < 0 || advanced > 255)
                throw new IllegalArgumentException("invalid_pending_pattern");
            this.address = address; this.targetStyle = targetStyle;
            this.controlBits = controlBits; this.advanced = advanced;
        }
    }
    public interface Journal {
        Pending pending();
        void mark(Pending pending) throws Exception;
        void clear(String address) throws Exception;
    }
    public interface Session extends AutoCloseable {
        PatternSettings snapshot() throws Exception;
        void writeStyle(PatternSettings source, int targetStyle) throws Exception;
        @Override void close();
    }
    public interface Transport {
        Session connect(String address) throws Exception;
        void settle() throws Exception;
    }
    public static final class Result {
        public final boolean success, pending;
        public final PatternSettings snapshot;
        public final String detail;
        private Result(boolean success, boolean pending, PatternSettings snapshot, String detail) {
            this.success = success; this.pending = pending; this.snapshot = snapshot; this.detail = detail;
        }
    }

    private final Journal journal;
    private final Transport transport;
    public PatternTransaction(Journal journal, Transport transport) {
        if (journal == null || transport == null) throw new IllegalArgumentException("null_dependency");
        this.journal = journal; this.transport = transport;
    }
    private Result result(boolean success, PatternSettings snapshot, String detail) {
        return new Result(success, journal.pending() != null, snapshot, detail);
    }
    private PatternSettings snapshot(String address) throws Exception {
        try (Session session = transport.connect(address)) {
            PatternSettings current = session.snapshot();
            if (current == null || current.style() < 1 || current.style() > 3)
                throw new IllegalArgumentException("unsupported_snapshot_style");
            return current;
        }
    }
    /** A complete fresh read resolves uncertainty even when it shows a mismatch. */
    public Result recover() {
        Pending pending = journal.pending();
        if (pending == null) return result(true, null, "no_pending_pattern");
        PatternSettings current;
        try { current = snapshot(pending.address); }
        catch (Exception e) { return result(false, null, "pattern_readback_unavailable"); }
        boolean matches = current.style() == pending.targetStyle && current.controlBits() == pending.controlBits &&
                current.advanced() == pending.advanced;
        try { journal.clear(pending.address); }
        catch (Exception e) { return result(false, current, "pattern_journal_clear_failed"); }
        return result(matches, current, matches ? "pattern_verified" : "pattern_readback_mismatch");
    }
    public Result read(String address) {
        if (address == null || address.isEmpty()) return result(false, null, "invalid_address");
        if (journal.pending() != null) {
            Result recovered = recover();
            if (recovered.pending || !recovered.success) return recovered;
        }
        try { return result(true, snapshot(address), "pattern_read"); }
        catch (Exception e) { return result(false, null, "pattern_read_failed"); }
    }
    public Result set(String address, int targetStyle) {
        if (address == null || address.isEmpty()) return result(false, null, "invalid_address");
        if (targetStyle < 1 || targetStyle > 3) return result(false, null, "unsupported_style");
        if (journal.pending() != null) {
            Result recovered = recover();
            if (recovered.pending || !recovered.success) return recovered;
        }
        PatternSettings original;
        boolean marked = false;
        try (Session session = transport.connect(address)) {
            original = session.snapshot();
            if (original == null) return result(false, null, "pattern_read_failed");
            // Validate every setting that the compound command would send, even for a no-op.
            try { original.frameForStyle(targetStyle); }
            catch (IllegalArgumentException e) { return result(false, original, "unsupported_style"); }
            catch (IllegalStateException e) { return result(false, original, "pattern_unsupported_controls"); }
            if (original.style() == targetStyle) return result(true, original, "pattern_unchanged");
            journal.mark(new Pending(address, targetStyle, original.controlBits(), original.advanced()));
            marked = true;
            session.writeStyle(original, targetStyle);
        } catch (Exception e) {
            if (!marked) return result(false, null, "pattern_write_not_started");
            // A failed flush or socket close does not establish whether the command took effect.
        }
        try { transport.settle(); }
        catch (Exception e) { return result(false, null, "pattern_readback_unavailable"); }
        return recover();
    }
}

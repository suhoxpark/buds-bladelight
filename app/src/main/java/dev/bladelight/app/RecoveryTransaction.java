package dev.bladelight.app;

import java.io.IOException;
import java.util.function.BooleanSupplier;

/** Transaction policy independent of Android, socket lifetime and UI. */
public final class RecoveryTransaction {
    public interface Journal {
        String pendingAddress();
        void mark(String address, String phase) throws Exception;
        void clear(String address) throws Exception;
    }
    public interface Session extends AutoCloseable {
        void mode(boolean on) throws Exception;
        void led(boolean on) throws Exception;
        @Override void close();
    }
    public interface Transport { Session connect(String address) throws Exception; }
    public interface Hooks {
        void log(String message);
        void afterMode(Session session) throws Exception;
        void afterLed(Session session) throws Exception;
    }
    public static final class Result {
        public final boolean success, pending;
        public final String detail;
        Result(boolean success, boolean pending, String detail) {
            this.success = success; this.pending = pending; this.detail = detail;
        }
    }
    private final Journal journal;
    private final Transport transport;
    private final Hooks hooks;
    public RecoveryTransaction(Journal journal, Transport transport, Hooks hooks) {
        this.journal = journal; this.transport = transport; this.hooks = hooks;
    }
    /** Recovery runs on a fresh session so a delayed ON acknowledgement cannot confirm OFF. */
    private void recoverOne() throws Exception {
        String address = journal.pendingAddress();
        if (address.isEmpty()) return;
        hooks.log("미완료 명령 복구: 새 연결에서 조명 끄기와 모드 종료를 확인합니다.");
        try (Session session = transport.connect(address)) {
            Exception failure = null;
            try {
                session.mode(true);
                session.led(false);
            } catch (Exception e) { failure = e; }
            // Even when mode entry or the OFF reply fails, attempt to leave diagnostic mode.
            try { session.mode(false); }
            catch (Exception e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
            if (failure != null) throw failure;
        }
        journal.clear(address);
        hooks.log("복구 명령과 모드 종료 응답 확인. 실제 조명은 별도 확인이 필요합니다.");
    }
    public Result recover() {
        try { recoverOne(); return result(true, "복구 처리 완료"); }
        catch (Exception e) { hooks.log("복구 미완료: " + safeError(e)); return result(false, "복구 미완료: " + safeError(e)); }
    }
    public Result request(String address, boolean enabled, BooleanSupplier stillWanted) {
        try {
            if (!journal.pendingAddress().isEmpty()) {
                recoverOne();
                if (!journal.pendingAddress().isEmpty()) throw new IOException("복구 기록이 남아 있습니다.");
            }
        } catch (Exception e) {
            hooks.log("기존 미완료 작업 복구 실패: " + safeError(e));
            return result(false, "기존 작업 복구 미완료: " + safeError(e));
        }
        // The OFF operation is never suppressed by auto-ON option changes.
        if (enabled && !stillWanted.getAsBoolean()) return result(false, "켜기 요청이 취소되었습니다.");
        boolean marked = false;
        try {
            try (Session session = transport.connect(address)) {
                if (enabled && !stillWanted.getAsBoolean()) throw new IOException("켜기 요청이 취소되었습니다.");
                journal.mark(address, enabled ? "on_before_mode" : "off_before_mode");
                marked = true;
                session.mode(true);
                hooks.afterMode(session);
                if (enabled && !stillWanted.getAsBoolean()) throw new IOException("켜기 요청이 취소되었습니다.");
                journal.mark(address, enabled ? "on_before_led" : "off_before_led");
                session.led(enabled);
                hooks.afterLed(session);
                if (enabled && !stillWanted.getAsBoolean()) throw new IOException("켜기 요청이 취소되었습니다.");
                journal.mark(address, enabled ? "on_before_exit" : "off_before_exit");
                session.mode(false);
            }
            journal.clear(address);
            return result(true, enabled ? "켜기와 진단 종료 응답 확인" : "끄기와 진단 종료 응답 확인");
        } catch (Exception failure) {
            hooks.log("명령 중단: " + safeError(failure));
            // One bounded fresh-session recovery, never reuse the interrupted session.
            boolean restored = false;
            if (marked || !journal.pendingAddress().isEmpty()) {
                try { recoverOne(); restored = true; }
                catch (Exception recoveryError) { hooks.log("자동 복구 미완료: " + safeError(recoveryError)); }
            }
            return result(false, "요청 미완료" + (restored ? ", 복구 명령 응답 확인" : "") + ": " + safeError(failure));
        }
    }
    private Result result(boolean success, String text) {
        return new Result(success, !journal.pendingAddress().isEmpty(), text);
    }
    private static String safeError(Exception e) {
        String message = e.getMessage();
        if (message == null) return e.getClass().getSimpleName();
        message = message.replaceAll("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}", "[주소 가림]").replaceAll("[\\r\\n\\t]", " ");
        return e.getClass().getSimpleName() + ": " + (message.length() > 150 ? message.substring(0, 150) : message);
    }
}

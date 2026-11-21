package dev.bladelight.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.io.IOException;

/** Small durable journal. Device effects must never precede a successful pending commit. */
public final class StateStore {
    private static final Object LOCK = new Object();
    private final SharedPreferences prefs;
    public StateStore(Context context) { prefs = context.getApplicationContext().getSharedPreferences("blade_state", Context.MODE_PRIVATE); }
    public String registeredAddress() { return prefs.getString("registered_address", ""); }
    public String registeredName() { return prefs.getString("registered_name", ""); }
    public boolean autoEnabled() { return prefs.getBoolean("auto_enabled", false); }
    public String pendingAddress() { return prefs.getString("pending_address", ""); }
    public String pendingPhase() { return prefs.getString("pending_phase", ""); }
    public boolean hasPending() { return !pendingAddress().isEmpty() || !prefs.getString("pattern_pending_address", "").isEmpty(); }
    public String lastRequest() { return prefs.getString("last_request", ""); }
    public void lastRequest(String request) { prefs.edit().putString("last_request", request).apply(); }
    public PatternTransaction.Pending pendingPattern() {
        String address = prefs.getString("pattern_pending_address", "");
        if (address.isEmpty()) return null;
        return new PatternTransaction.Pending(address, prefs.getInt("pattern_target", -1),
                prefs.getInt("pattern_controls", -1), prefs.getInt("pattern_advanced", -1));
    }
    public void markPattern(PatternTransaction.Pending pending) throws IOException {
        synchronized (LOCK) {
            if (!registeredAddress().equals(pending.address) || hasPending()) throw new IOException("설정 작업을 시작할 수 없습니다.");
            commit(prefs.edit().putString("pattern_pending_address", pending.address)
                    .putInt("pattern_target", pending.targetStyle).putInt("pattern_controls", pending.controlBits)
                    .putInt("pattern_advanced", pending.advanced));
        }
    }
    public void clearPattern(String address) throws IOException {
        synchronized (LOCK) {
            if (!address.equals(prefs.getString("pattern_pending_address", ""))) throw new IOException("설정 확인 대상이 다릅니다.");
            commit(prefs.edit().remove("pattern_pending_address").remove("pattern_target")
                    .remove("pattern_controls").remove("pattern_advanced"));
        }
    }
    public String status() { return prefs.getString("last_status", "등록한 버즈가 없습니다."); }
    public void status(String text) { prefs.edit().putString("last_status", text).apply(); }
    public void register(String address, String name) throws IOException {
        synchronized (LOCK) {
            if (hasPending()) throw new IOException("미완료 작업을 복구한 뒤 기기를 변경하세요.");
            commit(prefs.edit().putString("registered_address", address).putString("registered_name", name)
                    .putBoolean("auto_enabled", false).putBoolean("acl_connected", false).remove("last_request")
                    .putLong("connection_epoch", epoch() + 1).putLong("handled_epoch", -1));
        }
    }
    public void setAutoEnabled(boolean enabled) throws IOException {
        synchronized (LOCK) {
            if (enabled && registeredAddress().isEmpty()) throw new IOException("먼저 버즈를 등록하세요.");
            commit(prefs.edit().putBoolean("auto_enabled", enabled));
        }
    }
    public void markPending(String address, String phase) throws IOException {
        synchronized (LOCK) {
            if (!registeredAddress().equals(address)) throw new IOException("등록 기기가 바뀌어 명령을 중단합니다.");
            if (!pendingAddress().isEmpty() && !pendingAddress().equals(address)) throw new IOException("다른 기기의 복구가 남아 있습니다.");
            String patternAddress = prefs.getString("pattern_pending_address", "");
            if (!patternAddress.isEmpty() && !patternAddress.equals(address)) throw new IOException("다른 기기의 설정 확인이 남아 있습니다.");
            commit(prefs.edit().putString("pending_address", address).putString("pending_phase", phase));
        }
    }
    public void clearPending(String address) throws IOException {
        synchronized (LOCK) {
            if (!pendingAddress().equals(address)) throw new IOException("복구 기록의 기기가 일치하지 않습니다.");
            commit(prefs.edit().remove("pending_address").remove("pending_phase"));
        }
    }
    public long epoch() { return prefs.getLong("connection_epoch", 0); }
    public boolean connectionActive() { return prefs.getBoolean("acl_connected", false); }
    public long handledEpoch() { return prefs.getLong("handled_epoch", -1); }
    public long connectionEvent(String address, boolean connected) throws IOException {
        synchronized (LOCK) {
            if (!registeredAddress().equals(address)) return -1;
            long next = epoch();
            long now = SystemClock.elapsedRealtime();
            long last = prefs.getLong("last_connection_elapsed", -1);
            // A genuine CONNECTED edge must recover after a missed DISCONNECTED
            // (e.g. force-stop/reboot). Suppress only immediate duplicate delivery.
            boolean duplicate = connected && connectionActive() && last >= 0 && now >= last && now - last < 1000;
            if (connected && !duplicate) next++;
            SharedPreferences.Editor edit = prefs.edit().putBoolean("acl_connected", connected).putLong("connection_epoch", next);
            if (connected && !duplicate) edit.putLong("last_connection_elapsed", now);
            commit(edit);
            return next;
        }
    }
    public boolean claimAuto(long expectedEpoch) throws IOException {
        synchronized (LOCK) {
            if (!autoEnabled() || !connectionActive() || epoch() != expectedEpoch || handledEpoch() == expectedEpoch) return false;
            commit(prefs.edit().putLong("handled_epoch", expectedEpoch));
            return true;
        }
    }
    private static void commit(SharedPreferences.Editor editor) throws IOException {
        if (!editor.commit()) throw new IOException("복구/설정 기록을 저장하지 못했습니다. 기기 명령을 중단합니다.");
    }
}

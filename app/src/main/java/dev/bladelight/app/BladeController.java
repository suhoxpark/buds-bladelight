package dev.bladelight.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** One process-wide owner of all factory-channel transactions. */
public final class BladeController {
    public enum Fault { NONE, DROP_AFTER_MODE, DROP_AFTER_LED, PAUSE_AFTER_LED }
    public interface Listener {
        void log(String message);
        void finished(boolean success, boolean recoveryPending, String detail);
    }
    public interface PatternListener {
        void finished(PatternTransaction.Result result, String error);
    }
    private static final String TAG = "BladeLight";
    private static final UUID FACTORY = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private static BladeController instance;
    private final Context context;
    private final StateStore store;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final AtomicLong nextSession = new AtomicLong();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

    private BladeController(Context context) {
        this.context = context.getApplicationContext();
        this.store = new StateStore(this.context);
    }
    public static synchronized BladeController get(Context context) {
        if (instance == null) instance = new BladeController(context);
        return instance;
    }
    public boolean isBusy() { return busy.get(); }
    public boolean request(String address, boolean enabled, BooleanSupplier stillWanted, Fault fault, Listener listener) {
        if (!busy.compareAndSet(false, true)) return false;
        executor.execute(() -> {
            RecoveryTransaction.Result result;
            try {
                if (address == null || !address.equals(store.registeredAddress())) throw new IOException("등록된 버즈가 아닙니다.");
                result = transaction(listener, fault == null ? Fault.NONE : fault).request(address, enabled, stillWanted == null ? () -> true : stillWanted);
            } catch (Exception e) {
                result = new RecoveryTransaction.Result(false, store.hasPending(), "요청 실패: " + describe(e));
            }
            store.lastRequest(result.success ? (enabled ? "on" : "off") : "unconfirmed");
            finish(listener, result);
        });
        return true;
    }
    public boolean recover(Listener listener) {
        if (!busy.compareAndSet(false, true)) return false;
        executor.execute(() -> {
            RecoveryTransaction.Result result;
            try {
                boolean lightingPending = !store.pendingAddress().isEmpty();
                result = transaction(listener, Fault.NONE).recover();
                if (lightingPending) store.lastRequest(result.success ? "off" : "unconfirmed");
                if (result.success && !result.pending && store.pendingPattern() != null) {
                    PatternTransaction.Result checked = patternTransaction().recover();
                    result = new RecoveryTransaction.Result(checked.success, store.hasPending(), checked.detail);
                }
            }
            catch (Exception e) { result = new RecoveryTransaction.Result(false, store.hasPending(), "복구 실패: " + describe(e)); }
            finish(listener, result);
        });
        return true;
    }
    public boolean pattern(String address, Integer targetStyle, PatternListener listener) {
        if (!busy.compareAndSet(false, true)) return false;
        executor.execute(() -> {
            PatternTransaction.Result result = null; String error = "";
            try {
                if (address == null || !address.equals(store.registeredAddress())) throw new IOException("Registered device changed");
                if (!store.pendingAddress().isEmpty()) throw new IOException("Lighting recovery required");
                result = targetStyle == null ? patternTransaction().read(address) : patternTransaction().set(address, targetStyle);
            } catch (Exception e) { error = describe(e); }
            finally { busy.set(false); }
            try { listener.finished(result, error); }
            catch (RuntimeException e) { Log.w(TAG, "pattern listener failed", e); }
        });
        return true;
    }
    private PatternTransaction patternTransaction() {
        PatternTransaction.Journal journal = new PatternTransaction.Journal() {
            public PatternTransaction.Pending pending() { return store.pendingPattern(); }
            public void mark(PatternTransaction.Pending pending) throws Exception { store.markPattern(pending); }
            public void clear(String address) throws Exception { store.clearPattern(address); }
        };
        return new PatternTransaction(journal, new PatternTransaction.Transport() {
            public PatternTransaction.Session connect(String address) throws Exception { return BladeController.this.connect(address, null); }
            public void settle() throws Exception { Thread.sleep(250); }
        });
    }
    private void finish(Listener listener, RecoveryTransaction.Result result) {
        boolean pending = store.hasPending();
        String detail = result.detail + (pending ? " · 복구 대기" : "") + ". 실제 LED 상태는 별도 확인이 필요합니다.";
        store.status(detail);
        busy.set(false);
        if (listener != null) {
            try { listener.finished(result.success, pending, detail); }
            catch (RuntimeException e) { Log.w(TAG, "listener completion failed", e); }
        }
    }
    private void log(Listener listener, String message) {
        Log.i(TAG, message);
        if (listener != null) {
            try { listener.log(message); }
            catch (RuntimeException e) { Log.w(TAG, "listener log failed", e); }
        }
    }
    private RecoveryTransaction transaction(Listener listener, Fault fault) {
        RecoveryTransaction.Journal journal = new RecoveryTransaction.Journal() {
            public String pendingAddress() { return store.pendingAddress(); }
            public void mark(String address, String phase) throws Exception { store.markPending(address, phase); log(listener, "복구 기록: " + phase); }
            public void clear(String address) throws Exception { store.clearPending(address); log(listener, "복구 기록 해제"); }
        };
        RecoveryTransaction.Hooks hooks = new RecoveryTransaction.Hooks() {
            public void log(String text) { BladeController.this.log(listener, text); }
            public void beforeOn() throws Exception {
                if (store.pendingPattern() != null) {
                    PatternTransaction.Result checked = patternTransaction().recover();
                    if (!checked.success || checked.pending) throw new IOException(checked.detail);
                }
            }
            public void afterMode(RecoveryTransaction.Session session) throws Exception {
                if (fault == Fault.DROP_AFTER_MODE) { log("FAULT_DROP_AFTER_MODE"); session.close(); throw new IOException("시험용 연결 끊김 (진단 모드 진입 후)"); }
            }
            public void afterLed(RecoveryTransaction.Session session) throws Exception {
                if (fault == Fault.DROP_AFTER_LED) { log("FAULT_DROP_AFTER_LED"); session.close(); throw new IOException("시험용 연결 끊김 (LED 응답 후)"); }
                if (fault == Fault.PAUSE_AFTER_LED) { log("FAULT_PAUSE_AFTER_LED"); Thread.sleep(20000); }
            }
        };
        return new RecoveryTransaction(journal, address -> connect(address, listener), hooks);
    }
    @SuppressLint("MissingPermission") private SocketSession connect(String address, Listener listener) throws Exception {
        if (!address.equals(store.registeredAddress())) throw new IOException("복구 대상이 등록된 버즈와 일치하지 않습니다.");
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            throw new IOException("주변 기기 권한이 필요합니다.");
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) throw new IOException("Bluetooth가 꺼져 있습니다.");
        BluetoothDevice device;
        try { device = adapter.getRemoteDevice(address); }
        catch (IllegalArgumentException e) { throw new IOException("등록된 주소가 올바르지 않습니다.", e); }
        if (device.getBondState() != BluetoothDevice.BOND_BONDED || device.getType() == BluetoothDevice.DEVICE_TYPE_LE)
            throw new IOException("등록된 일반 Bluetooth 페어링을 확인하세요.");
        BluetoothSocket socket = device.createInsecureRfcommSocketToServiceRecord(FACTORY);
        log(listener, "팩토리 제어 채널 연결 중");
        ScheduledFuture<?> deadline = timer.schedule(() -> close(socket), 10, TimeUnit.SECONDS);
        try { socket.connect(); }
        catch (Exception e) { close(socket); throw e; }
        finally { deadline.cancel(false); }
        log(listener, "팩토리 제어 채널 연결됨");
        try { return new SocketSession(socket, listener, nextSession.incrementAndGet()); }
        catch (Exception e) { close(socket); throw e; }
    }
    private final class SocketSession implements RecoveryTransaction.Session, PatternTransaction.Session {
        private final BluetoothSocket socket;
        private final Listener listener;
        private final long id;
        private final Protocol.ResponseGate gate = new Protocol.ResponseGate();
        private volatile boolean closed;
        private final Object snapshotLock = new Object();
        private byte[] initialSnapshot;
        private boolean readerEnded;
        SocketSession(BluetoothSocket socket, Listener listener, long id) throws IOException {
            this.socket = socket; this.listener = listener; this.id = id;
            InputStream input = socket.getInputStream();
            Thread reader = new Thread(() -> {
                Protocol.Decoder decoder = new Protocol.Decoder(new Protocol.Listener() {
                    public void frame(byte[] frame) {
                        gate.accept(id, frame);
                        if ((frame[3] & 255) == 0x61 && (frame[2] & 0x20) == 0) synchronized (snapshotLock) {
                            if (initialSnapshot == null) initialSnapshot = frame.clone();
                            snapshotLock.notifyAll();
                        }
                    }
                    public void invalid(String reason) { log(listener, "잘못된 수신 프레임: " + reason); }
                });
                byte[] buffer = new byte[512];
                try {
                    int count;
                    while ((count = input.read(buffer)) >= 0) decoder.accept(buffer, count);
                } catch (IOException e) { if (!closed) log(listener, "수신 연결 끊김: " + describe(e)); }
                finally { synchronized (snapshotLock) { readerEnded = true; snapshotLock.notifyAll(); } }
            }, "blade-rx-" + id);
            reader.setDaemon(true); reader.start();
        }
        public void mode(boolean on) throws Exception { exchange(Protocol.diagnosticMode(on), 0x12, on ? 1 : 0, on ? "진단 모드 진입" : "진단 모드 종료"); }
        public void led(boolean on) throws Exception { exchange(Protocol.diagnosticLed(on), 0x13, -1, on ? "LED 켜기" : "LED 끄기"); }
        public void nativeOff() throws Exception { exchange(Protocol.lighting(0), 0x8e, 0, "기본 조명 종료"); }
        public PatternSettings snapshot() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            synchronized (snapshotLock) {
                while (initialSnapshot == null && !readerEnded && !closed) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) throw new IOException("Settings snapshot timeout");
                    TimeUnit.NANOSECONDS.timedWait(snapshotLock, remaining);
                }
                if (initialSnapshot == null) throw new IOException("Settings snapshot unavailable");
                return PatternSettings.fromFrame(initialSnapshot);
            }
        }
        public void writeStyle(PatternSettings source, int targetStyle) throws Exception {
            PatternTransaction.Pending pending = store.pendingPattern();
            if (pending == null || pending.targetStyle != targetStyle || !pending.address.equals(store.registeredAddress())
                    || pending.controlBits != source.controlBits() || pending.advanced != source.advanced())
                throw new IOException("Settings journal required");
            writeBounded(source.frameForStyle(targetStyle));
            Thread.sleep(400);
        }
        private void writeBounded(byte[] frame) throws Exception {
            if (closed) throw new IOException("Connection closed");
            AtomicBoolean expired = new AtomicBoolean();
            ScheduledFuture<?> deadline = timer.schedule(() -> { expired.set(true); BladeController.close(socket); }, 2, TimeUnit.SECONDS);
            try { socket.getOutputStream().write(frame); socket.getOutputStream().flush(); }
            finally { deadline.cancel(false); }
            if (expired.get()) throw new IOException("Write timeout");
        }
        private void exchange(byte[] frame, int message, int value, String phase) throws Exception {
            if (closed) throw new IOException("연결이 종료되었습니다.");
            Protocol.ResponseGate.Ticket ticket = gate.arm(id, message, value);
            try {
                log(listener, "TX 시도 " + phase + ": " + Protocol.hex(frame));
                writeBounded(frame);
                gate.await(ticket, 3000);
                log(listener, "RX 확인 " + phase + " (실제 조명 상태 별도 확인)");
            } finally { gate.cancel(ticket); }
        }
        public void close() { closed = true; BladeController.close(socket); }
    }
    private static void close(BluetoothSocket socket) { try { socket.close(); } catch (IOException ignored) {} }
    private static String describe(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return e.getClass().getSimpleName();
        msg = msg.replaceAll("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}", "[주소 가림]").replaceAll("[\\r\\n\\t]", " ");
        return e.getClass().getSimpleName() + ": " + (msg.length() > 150 ? msg.substring(0, 150) : msg);
    }
}

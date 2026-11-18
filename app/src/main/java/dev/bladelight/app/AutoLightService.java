package dev.bladelight.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.io.IOException;
import java.util.function.BooleanSupplier;

/** Short lived service only for a real registered-device connection event. */
public final class AutoLightService extends Service {
    public static final String ACTION_CONNECTION = "dev.bladelight.app.AUTO_CONNECTION";
    public static final String EXTRA_EPOCH = "connection_epoch";
    public static final String EXTRA_RECOVERY_ONLY = "recovery_only";
    private static final String TAG = "BladeLightAuto";
    private static final String CHANNEL = "blade_auto_work";
    private static final int NOTIFICATION = 4101;
    private final Handler main = new Handler(Looper.getMainLooper());
    private StateStore store;
    private long latestEpoch = -1;
    private long attemptedEpoch = -1;
    private long busyDeadline;
    private int latestStartId;
    private boolean running;
    private boolean scheduled;
    private boolean destroyed;
    private boolean foregroundFailed;
    private boolean recoveryOnly;
    private boolean recoveryRequested;

    @Override public void onCreate() {
        super.onCreate();
        store = new StateStore(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW));
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent tap = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notice = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(tap).setOngoing(true).build();
        try {
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(NOTIFICATION, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            else startForeground(NOTIFICATION, notice);
        } catch (SecurityException | IllegalStateException e) {
            foregroundFailed = true;
            report("자동 조명 서비스를 시작하지 못했습니다: " + e.getClass().getSimpleName());
            stopSelf();
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        latestStartId = startId;
        if (foregroundFailed) { stopSelfResult(startId); return START_NOT_STICKY; }
        if (intent == null || !ACTION_CONNECTION.equals(intent.getAction())) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        long incoming = intent.getLongExtra(EXTRA_EPOCH, -1);
        if (incoming < 0) { stopSelfResult(startId); return START_NOT_STICKY; }
        boolean incomingRecoveryOnly = intent.getBooleanExtra(EXTRA_RECOVERY_ONLY, false);
        if (incoming != latestEpoch) {
            latestEpoch = incoming;
            busyDeadline = SystemClock.elapsedRealtime() + 30000;
            recoveryOnly = incomingRecoveryOnly;
            recoveryRequested = incomingRecoveryOnly;
        } else if (incomingRecoveryOnly) {
            recoveryOnly = true;
            recoveryRequested = true;
        }
        schedule(1000); // Let the earbud's services finish connecting.
        return START_NOT_STICKY;
    }

    private void schedule(long delay) {
        if (scheduled || running || destroyed) return;
        scheduled = true;
        main.postDelayed(() -> { scheduled = false; process(); }, delay);
    }

    private void process() {
        if (destroyed || running) return;
        long epoch = latestEpoch;
        if (epoch < 0 || epoch == attemptedEpoch || !store.connectionActive() || store.epoch() != epoch
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            stopSelfResult(latestStartId);
            return;
        }
        BladeController controller = BladeController.get(this);
        if (controller.isBusy()) {
            if (SystemClock.elapsedRealtime() < busyDeadline) schedule(250);
            else { report("다른 버즈 작업이 끝나지 않아 자동 처리를 생략했습니다. 앱에서 상태를 확인하세요."); stopSelfResult(latestStartId); }
            return;
        }
        boolean auto = !recoveryOnly && AutoConnectPolicy.shouldAuto(store.autoEnabled(), store.connectionActive(),
                store.epoch(), epoch, store.handledEpoch());
        if (!auto && !store.hasPending()) { stopSelfResult(latestStartId); return; }
        // A busy receiver request is consumed once; retries after a failed
        // recovery would otherwise loop forever for the same ACL connection.
        recoveryRequested = false;
        final String address = store.registeredAddress();
        final boolean[] claimed = {false};
        BooleanSupplier wanted = () -> {
            if (!store.autoEnabled() || !store.connectionActive() || store.epoch() != epoch
                    || !address.equals(store.registeredAddress())) return false;
            if (claimed[0]) return true;
            try { claimed[0] = store.claimAuto(epoch); return claimed[0]; }
            catch (IOException e) { report("자동 켜기 기록 저장 실패: " + e.getClass().getSimpleName()); return false; }
        };
        running = true;
        BladeController.Listener listener = new BladeController.Listener() {
            @Override public void log(String text) { Log.i(TAG, text); }
            @Override public void finished(boolean success, boolean recoveryPending, String detail) {
                main.post(() -> {
                    running = false;
                    attemptedEpoch = epoch;
                    report((success ? "연결 시 조명 처리 완료: " : "연결 시 조명 처리 미완료: ") + detail);
                    if (latestEpoch != epoch && store.connectionActive()) schedule(1000);
                    else if (recoveryRequested && store.hasPending() && store.connectionActive()) {
                        attemptedEpoch = -1;
                        schedule(1000);
                    }
                    else stopSelfResult(latestStartId);
                });
            }
        };
        boolean accepted = auto
                ? controller.request(address, true, wanted, BladeController.Fault.NONE, listener)
                : controller.recover(listener);
        if (!accepted) {
            running = false;
            if (SystemClock.elapsedRealtime() < busyDeadline) schedule(250);
            else { report("버즈 작업을 시작하지 못했습니다. 앱에서 상태를 확인하세요."); stopSelfResult(latestStartId); }
        }
    }

    private void report(String text) { store.status(text); Log.i(TAG, text); }

    @Override public void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

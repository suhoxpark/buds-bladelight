package dev.bladelight.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import java.io.IOException;

/** Receives system ACL broadcasts for the one explicitly registered device. */
public final class AutoConnectReceiver extends BroadcastReceiver {
    private static final String TAG = "BladeLightAuto";

    @SuppressLint("MissingPermission")
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        boolean connected = BluetoothDevice.ACTION_ACL_CONNECTED.equals(action);
        if (!connected && !BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) return;
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
        BluetoothDevice device = Build.VERSION.SDK_INT >= 33
                ? intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class)
                : intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (device == null) return;
        StateStore store = new StateStore(context);
        try {
            int transport = intent.getIntExtra(BluetoothDevice.EXTRA_TRANSPORT, -1);
            String address = device.getAddress();
            if (!AutoConnectPolicy.matches(store.registeredAddress(), address,
                    device.getBondState() == BluetoothDevice.BOND_BONDED,
                    transport, BluetoothDevice.TRANSPORT_BREDR)) return;
            long epoch = store.connectionEvent(address, connected);
            if (!connected || epoch < 0) return;
            // A manual/recovery transaction may itself establish the ACL. Do not
            // let that event override the user's operation with a second ON.
            boolean busy = BladeController.get(context).isBusy();
            if (busy) {
                store.claimAuto(epoch);
                Log.i(TAG, "기존 작업 중 연결됨: 자동 켜기 생략");
                // A genuine reconnection can arrive while the failing worker is
                // still unwinding. Preserve a recovery request even though the
                // manual action wins over automatic ON for this epoch.
                if (!store.hasPending()) return;
            }
            if (!busy && !store.hasPending() && !AutoConnectPolicy.shouldAuto(store.autoEnabled(),
                    store.connectionActive(), store.epoch(), epoch, store.handledEpoch())) return;
            Intent service = new Intent(context, AutoLightService.class)
                    .setAction(AutoLightService.ACTION_CONNECTION)
                    .putExtra(AutoLightService.EXTRA_EPOCH, epoch)
                    .putExtra(AutoLightService.EXTRA_RECOVERY_ONLY, busy);
            context.startForegroundService(service);
        } catch (SecurityException | IllegalStateException | IOException e) {
            String detail = "자동 연결 처리 불가: " + e.getClass().getSimpleName();
            store.status(detail);
            Log.w(TAG, detail, e);
        }
    }
}

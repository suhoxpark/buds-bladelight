package dev.bladelight.app;

/** Standalone JVM test; no Android runtime or Bluetooth hardware required. */
public final class AutoConnectPolicyTest {
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
    public static void main(String[] args) {
        String registered = "00:11:22:33:44:55";
        check(AutoConnectPolicy.matches(registered, registered, true, 1, 1), "bonded classic device matches");
        check(AutoConnectPolicy.matches(registered, "00:11:22:33:44:55".toLowerCase(), true, 1, 1), "case insensitive address");
        check(!AutoConnectPolicy.matches(registered, "00:11:22:33:44:56", true, 1, 1), "different device rejected");
        check(!AutoConnectPolicy.matches(registered, registered, false, 1, 1), "unbonded rejected");
        check(!AutoConnectPolicy.matches(registered, registered, true, 2, 1), "LE rejected");
        check(!AutoConnectPolicy.matches("", registered, true, 1, 1), "unregistered rejected");
        check(AutoConnectPolicy.shouldAuto(true, true, 5, 5, 4), "new active epoch");
        check(!AutoConnectPolicy.shouldAuto(false, true, 5, 5, 4), "disabled");
        check(!AutoConnectPolicy.shouldAuto(true, false, 5, 5, 4), "disconnected");
        check(!AutoConnectPolicy.shouldAuto(true, true, 6, 5, 4), "stale event");
        check(!AutoConnectPolicy.shouldAuto(true, true, 5, 5, 5), "already handled");
        System.out.println("Auto-connect policy tests passed");
    }
}

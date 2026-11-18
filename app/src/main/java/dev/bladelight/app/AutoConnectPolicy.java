package dev.bladelight.app;

/** Pure decision helpers; a connection epoch is claimed only once. */
public final class AutoConnectPolicy {
    private AutoConnectPolicy() {}

    public static boolean matches(String registered, String actual, boolean bonded, int transport, int bredr) {
        return bonded && registered != null && !registered.isEmpty()
                && registered.equalsIgnoreCase(actual) && transport == bredr;
    }

    public static boolean shouldAuto(boolean enabled, boolean connected, long current,
                                     long expected, long handled) {
        return enabled && connected && current == expected && current != handled;
    }
}

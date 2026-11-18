package dev.bladelight.app;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Standalone state-machine tests; no Android or Bluetooth hardware required. */
public final class RecoveryTransactionTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static final class Journal implements RecoveryTransaction.Journal {
        String pending = "", phase = "";
        boolean failCommit;
        final List<String> events;
        Journal(List<String> events) { this.events = events; }
        public String pendingAddress() { return pending; }
        public void mark(String addr, String value) throws Exception {
            events.add("mark:" + value);
            if (failCommit) throw new IOException("commit failed");
            pending = addr; phase = value;
        }
        public void clear(String addr) throws Exception {
            check(pending.equals(addr), "clearing wrong device");
            events.add("clear"); pending = ""; phase = "";
        }
    }
    static final class Transport implements RecoveryTransaction.Transport {
        final List<String> events;
        final Journal journal;
        String failStep = "";
        int failCount, connects;
        Transport(List<String> events, Journal journal) { this.events = events; this.journal = journal; }
        public RecoveryTransaction.Session connect(String address) throws Exception {
            int serial = ++connects; events.add("connect:" + serial);
            fail("connect");
            return new RecoveryTransaction.Session() {
                public void mode(boolean value) throws Exception {
                    check(!journal.pending.isEmpty(), "journal must exist before side effects");
                    String op = value ? "mode1" : "mode0"; events.add(serial + ":" + op); fail(op);
                }
                public void led(boolean value) throws Exception {
                    check(!journal.pending.isEmpty(), "journal must exist before LED");
                    String op = value ? "led1" : "led0"; events.add(serial + ":" + op); fail(op);
                }
                public void close() { events.add(serial + ":close"); }
            };
        }
        void fail(String step) throws IOException { if (step.equals(failStep) && failCount-- > 0) throw new IOException("injected " + step); }
    }
    static RecoveryTransaction.Hooks hooks(AtomicBoolean wanted, String fault) {
        return new RecoveryTransaction.Hooks() {
            public void log(String msg) { }
            public void afterMode(RecoveryTransaction.Session s) throws Exception { if (fault.equals("mode")) throw new IOException("drop"); }
            public void afterLed(RecoveryTransaction.Session s) throws Exception {
                if (fault.equals("led")) throw new IOException("drop");
                if (fault.equals("cancel")) wanted.set(false);
            }
        };
    }
    public static void main(String[] args) throws Exception {
        List<String> events = new ArrayList<>(); Journal journal = new Journal(events);
        Transport transport = new Transport(events, journal); AtomicBoolean wanted = new AtomicBoolean(true);
        RecoveryTransaction t = new RecoveryTransaction(journal, transport, hooks(wanted, ""));
        RecoveryTransaction.Result r = t.request("A", true, wanted::get);
        check(r.success && !r.pending, "successful ON");
        check(events.indexOf("mark:on_before_mode") < events.indexOf("1:mode1"), "journal before mode");
        check(events.indexOf("mark:on_before_led") < events.indexOf("1:led1"), "journal before LED");
        check(events.indexOf("1:mode0") < events.indexOf("clear"), "clear after exit");

        for (String fault : new String[]{"mode", "led", "cancel"}) {
            events.clear(); journal.pending = ""; transport.connects = 0; wanted.set(true);
            r = new RecoveryTransaction(journal, transport, hooks(wanted, fault)).request("A", true, wanted::get);
            check(!r.success && !r.pending, "failure is reported even if recovery succeeds " + fault);
            check(transport.connects == 2 && events.contains("2:led0") && events.contains("2:mode0"), "fresh OFF recovery " + fault);
            check(events.indexOf("1:close") < events.indexOf("connect:2"), "old connection closed first");
        }

        events.clear(); journal.pending = "A"; journal.phase = "on_before_led"; transport.connects = 0;
        transport.failStep = "connect"; transport.failCount = 1;
        r = t.recover(); check(!r.success && r.pending && journal.pending.equals("A"), "failed recovery retains journal");
        check(transport.connects == 1, "one bounded recovery attempt");
        transport.failStep = ""; events.clear(); transport.connects = 0;
        r = new RecoveryTransaction(journal, transport, hooks(wanted, "")).recover();
        check(r.success && !r.pending && events.contains("1:led0"), "new process can recover persisted phase");

        events.clear(); journal.pending = "A"; transport.connects = 0;
        transport.failStep = "led0"; transport.failCount = 1;
        r = t.recover();
        check(!r.success && r.pending, "missing OFF acknowledgement retains journal");
        check(events.contains("1:mode0") && !events.contains("clear"), "mode exit attempted after OFF error");
        journal.pending = ""; transport.failStep = "";

        events.clear(); journal.pending = ""; journal.failCommit = true; transport.connects = 0; wanted.set(true);
        r = t.request("A", true, wanted::get);
        check(!r.success && !r.pending && !events.contains("1:mode1"), "failed durable journal prevents device write");
        journal.failCommit = false;
        events.clear(); transport.connects = 0; wanted.set(false);
        r = t.request("A", true, wanted::get);
        check(!r.success && transport.connects == 0, "disabled auto request is not sent");
        r = t.request("A", false, wanted::get);
        check(r.success && events.contains("1:led0"), "OFF is not blocked by auto option");
        System.out.println("RecoveryTransaction tests passed");
    }
}

package be.mygod.pogoplusplus.rollback;

import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.VersionedPackage;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Rolls back the Google Play system update that installed a Bluetooth module that stops reporting MTU changes to GATT
 * servers. It runs as the adb shell user through app_process, which holds MANAGE_ROLLBACKS; see
 * rollback-bluetooth-update.template.sh.
 */
public final class RollbackBluetoothUpdate {
    private static final String[] BLUETOOTH_MODULES = { "com.google.android.bt", "com.android.bt" };
    /** First Bluetooth module version known to stop reporting MTU changes to GATT servers that do not hold the link. */
    private static final long FIRST_AFFECTED_VERSION = 373041380L;
    private static final String CALLER_PACKAGE = "com.android.shell";
    private static final String INTENT_SENDER_DESCRIPTOR = "android.content.IIntentSender";
    private static final String EXTRA_STATUS = "android.content.rollback.extra.STATUS";
    private static final String EXTRA_STATUS_MESSAGE = "android.content.rollback.extra.STATUS_MESSAGE";
    private static final String[] STATUS_NAMES = { "success", "failure", "rollback unavailable", "install failure" };

    private static final class Candidate {
        final Object info;
        final int id;
        final List<?> packages;
        long bluetoothFrom = -1;
        long bluetoothTo = -1;

        Candidate(Object info) throws Exception {
            this.info = info;
            id = (Integer) call(info, "getRollbackId");
            packages = (List<?>) call(info, "getPackages");
            for (Object pkg : packages) {
                for (String module : BLUETOOTH_MODULES) if (module.equals(call(pkg, "getPackageName"))) {
                    bluetoothFrom = version(pkg, "getVersionRolledBackFrom");
                    bluetoothTo = version(pkg, "getVersionRolledBackTo");
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        boolean commit = false;
        boolean force = false;
        Integer requestedId = null;
        for (String arg : args) {
            if (arg.equals("commit")) commit = true;
            else if (arg.equals("force")) force = true;
            else if (arg.startsWith("id=")) requestedId = Integer.valueOf(arg.substring(3));
            else {
                System.err.println("Unknown argument: " + arg);
                System.err.println("Arguments: [commit] [force] [id=<rollback id>]");
                System.exit(2);
            }
        }
        if (Process.myUid() != 2000) {
            System.err.println("Run this as the adb shell user (adb shell sh ...), not as root or an app.");
            System.exit(2);
        }

        Object manager = Class.forName("android.content.rollback.IRollbackManager$Stub")
                .getMethod("asInterface", IBinder.class)
                .invoke(null, Class.forName("android.os.ServiceManager")
                        .getMethod("getService", String.class).invoke(null, "rollback"));
        List<?> available = (List<?>) call(call(manager, "getAvailableRollbacks"), "getList");
        Candidate chosen = null;
        for (Object info : available) {
            Candidate candidate = new Candidate(info);
            if (requestedId != null ? candidate.id == requestedId : candidate.bluetoothFrom >= 0
                    && (chosen == null || candidate.bluetoothFrom > chosen.bluetoothFrom)) chosen = candidate;
        }

        int rollbackId;
        if (chosen == null) {
            if (requestedId == null) {
                System.out.println("No available rollback includes the Bluetooth module, so there is nothing to do.");
                System.out.println("Rollbacks are only kept for a limited time after a Google Play system update.");
                System.exit(1);
            }
            if (!commit || !force) {
                System.out.println("Rollback " + requestedId + " is not available.");
                System.exit(1);
            }
            rollbackId = requestedId;
        } else {
            describe(chosen);
            rollbackId = chosen.id;
            String problem = null;
            if (chosen.bluetoothFrom < 0) {
                problem = "This rollback does not include the Bluetooth module.";
            } else if (chosen.bluetoothFrom < FIRST_AFFECTED_VERSION) {
                problem = "The installed Bluetooth module predates " + FIRST_AFFECTED_VERSION
                        + ", the first version known to break Nintendo Switch postcards.";
            } else if (chosen.bluetoothTo >= FIRST_AFFECTED_VERSION) {
                problem = "Rolling back would still leave a Bluetooth module at or after " + FIRST_AFFECTED_VERSION
                        + ", which likely has the same bug.";
            }
            if (problem != null) {
                System.out.println();
                System.out.println(problem);
                if (!force) {
                    System.out.println("Not rolling back. Add \"force\" to roll back anyway.");
                    System.exit(1);
                }
            }
            if (!commit) {
                System.out.println();
                System.out.println("Nothing has been changed. To roll back and reboot, run it again with \"commit\".");
                return;
            }
        }

        System.out.println();
        System.out.println("Rolling back " + rollbackId + "...");
        final CountDownLatch done = new CountDownLatch(1);
        final Intent[] result = new Intent[1];
        Binder receiver = new Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws android.os.RemoteException {
                if (code != IBinder.FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags);
                try {
                    // IIntentSender.send(int code, in Intent intent, ...)
                    data.enforceInterface(INTENT_SENDER_DESCRIPTOR);
                    data.readInt();
                    if (data.readInt() != 0) result[0] = Intent.CREATOR.createFromParcel(data);
                } catch (RuntimeException e) {
                    System.err.println("Could not read the rollback result: " + e);
                }
                done.countDown();
                return true;
            }
        };
        receiver.attachInterface(null, INTENT_SENDER_DESCRIPTOR);
        Constructor<IntentSender> senderConstructor = IntentSender.class.getDeclaredConstructor(IBinder.class);
        senderConstructor.setAccessible(true);
        Object noCausePackages = Class.forName("android.content.pm.ParceledListSlice")
                .getConstructor(List.class).newInstance(new ArrayList<VersionedPackage>());
        call(manager, "commitRollback", rollbackId, noCausePackages, CALLER_PACKAGE,
                senderConstructor.newInstance(receiver));
        if (!done.await(5, TimeUnit.MINUTES)) {
            System.out.println("No result after 5 minutes. Check \"adb shell dumpsys rollback\" before rebooting.");
            System.exit(1);
        }
        int status = result[0] == null ? -1 : result[0].getIntExtra(EXTRA_STATUS, -1);
        String message = result[0] == null ? null : result[0].getStringExtra(EXTRA_STATUS_MESSAGE);
        System.out.println("Result: " + (status >= 0 && status < STATUS_NAMES.length ? STATUS_NAMES[status]
                : "unknown (" + status + ")") + (message == null ? "" : ": " + message));
        System.exit(status == 0 ? 0 : 1);
    }

    private static void describe(Candidate candidate) throws Exception {
        System.out.println("Rollback " + candidate.id + (Boolean.TRUE.equals(call(candidate.info, "isStaged"))
                ? " (applies after a reboot)" : "") + " restores " + candidate.packages.size() + " packages:");
        for (Object pkg : candidate.packages) {
            System.out.println("  " + call(pkg, "getPackageName") + ": " + version(pkg, "getVersionRolledBackFrom")
                    + " -> " + version(pkg, "getVersionRolledBackTo"));
        }
        if (candidate.bluetoothFrom >= 0) {
            System.out.println("Bluetooth module: " + candidate.bluetoothFrom + " -> " + candidate.bluetoothTo);
        }
    }

    private static long version(Object pkg, String getter) throws Exception {
        return ((VersionedPackage) call(pkg, getter)).getLongVersionCode();
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        for (Class<?> clazz = target.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            for (Class<?> type : candidatesOf(clazz)) for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterTypes().length == args.length) {
                    method.setAccessible(true);
                    return method.invoke(target, args);
                }
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }

    private static List<Class<?>> candidatesOf(Class<?> clazz) {
        List<Class<?>> types = new ArrayList<>();
        types.add(clazz);
        for (Class<?> type : clazz.getInterfaces()) types.add(type);
        return types;
    }
}

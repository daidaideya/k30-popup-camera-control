import android.os.HwBinder;
import android.os.HwParcel;
import android.os.IHwBinder;

import java.util.NoSuchElementException;

public final class MotorBridge {
    private static final String IFACE = "vendor.xiaomi.hardware.motor@1.0::IMotor";

    private static int call(int code, Integer cookie) throws Exception {
        IHwBinder motor = HwBinder.getService(IFACE, "default");
        if (motor == null) throw new NoSuchElementException("Motor HAL service not found");
        HwParcel request = new HwParcel();
        HwParcel reply = new HwParcel();
        try {
            request.writeInterfaceToken(IFACE);
            if (cookie != null) request.writeInt32(cookie);
            motor.transact(code, request, reply, 0);
            reply.verifySuccess();
            reply.releaseTemporaryStorage();
            return code == 6 ? reply.readInt32() : 0;
        } finally {
            reply.release();
        }
    }

    private static int status() throws Exception {
        return call(6, null);
    }

    public static void main(String[] args) {
        try {
            if (args.length == 0 || "status".equals(args[0])) {
                System.out.println("MOTOR_STATUS=" + status());
                return;
            }
            int current = status();
            if ("up".equals(args[0])) {
                if (current == 11) {
                    System.out.println("MOTOR_STATUS=11");
                    return;
                }
                if (current != 13) {
                    System.out.println("MOTOR_NOT_READY=" + current);
                    System.exit(2);
                }
                call(1, 1);
            } else if ("down".equals(args[0])) {
                if (current == 13) {
                    System.out.println("MOTOR_STATUS=13");
                    return;
                }
                if (current == 14) {
                    System.out.println("MOTOR_CONFIRM_REQUIRED=14");
                    System.exit(4);
                }
                if (current != 11) {
                    System.out.println("MOTOR_NOT_READY=" + current);
                    System.exit(2);
                }
                call(2, 1);
            } else if ("down-confirmed".equals(args[0])) {
                if (current == 13) {
                    System.out.println("MOTOR_STATUS=13");
                    return;
                }
                if (current != 11 && current != 14) {
                    System.out.println("MOTOR_NOT_READY=" + current);
                    System.exit(2);
                }
                call(2, 1);
            } else {
                throw new IllegalArgumentException("unknown action");
            }
            Thread.sleep(2200L);
            System.out.println("MOTOR_STATUS=" + status());
        } catch (NoSuchElementException e) {
            System.err.println("MOTOR_HAL_MISSING=" + IFACE + "/default");
            System.exit(3);
        } catch (Throwable t) {
            System.err.println("MOTOR_ERROR=" + t.getClass().getSimpleName() + ": " + t.getMessage());
            System.exit(1);
        }
    }
}

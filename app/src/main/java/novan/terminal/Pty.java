package novan.terminal;

import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/* loaded from: classes.dex */
public class Pty {
    static boolean loaded;
    public InputStream in;
    OutputStream out;
    ParcelFileDescriptor pfd;
    public int pid;

    static native void setSize(int i, int i2, int i3);

    static native int[] start(String str, String str2, String[] strArr, int i, int i2);

    public static native int waitFor(int i);

    static {
        try {
            System.loadLibrary("ptyterm");
            loaded = true;
        } catch (Throwable unused) {
            loaded = false;
        }
    }

    public static Pty open(String str, String str2, int i, int i2) {
        if (!loaded) {
            return null;
        }
        try {
            int[] iArrStart = start(str, str2, Env.vars(i, i2), i, i2);
            if (iArrStart != null && iArrStart[0] >= 0) {
                Pty pty = new Pty();
                pty.pid = iArrStart[1];
                pty.pfd = ParcelFileDescriptor.adoptFd(iArrStart[0]);
                pty.in = new FileInputStream(pty.pfd.getFileDescriptor());
                pty.out = new FileOutputStream(pty.pfd.getFileDescriptor());
                return pty;
            }
        } catch (Throwable unused) {
        }
        return null;
    }

    void resize(int i, int i2) {
        try {
            setSize(this.pfd.getFd(), i, i2);
        } catch (Throwable unused) {
        }
    }

    public void send(String str) throws IOException {
        try {
            this.out.write(str.getBytes("UTF-8"));
            this.out.flush();
        } catch (Exception unused) {
        }
    }

    public void hangup() throws ErrnoException {
        try {
            Os.kill(-this.pid, OsConstants.SIGHUP);
        } catch (Exception unused) {
        }
        try {
            Os.kill(this.pid, OsConstants.SIGKILL);
        } catch (Exception unused2) {
        }
    }

    public void close() throws IOException {
        try {
            this.pfd.close();
        } catch (Exception unused) {
        }
    }
}

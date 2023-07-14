package common.log;

import org.jetbrains.annotations.NotNull;

/**
 * Log Role<br>
 * <br>
 * Logs to the system console
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */
public class Log implements ILog {
    // These could be swapped out for files or network calls.
    private void debug(String tag, String msg) {
        System.out.println(tag + ": " + msg);
    }
    private void warning(String tag, String msg) {
        System.err.println(tag + ":(WARNING) " + msg);
    }
    private void error(String tag, String msg) {
        System.err.println(tag + ":(ERROR) " + msg);
    }

    // example: log.d(this, "message") will print "ClassName➤MethodName(): message"
    public void d(Object tag, String msg) {
        if(tag == null) {
            debug("null", msg);
            return;
        }
        if(tag instanceof String) {
            debug((String) tag, msg);
            return;
        }

        debug(calcLogPrefix(tag), msg);
    }

    public void e(Object tag, String msg, Exception e) {
        if(tag == null) {
            error("null", msg);
            return;
        }
        if(tag instanceof String) {
            error((String) tag, msg);
            return;
        }

        // Collect stacktrace to a comma delimited string
        StringBuilder stacktrace = new StringBuilder();
        for(StackTraceElement ste : e.getStackTrace()) {
            stacktrace.append(ste.toString()).append(", ");
        }

        error(calcLogPrefix(tag), msg + ", " + stacktrace);
        e.printStackTrace(); // LEAVE for debugging
    }

    // example: log.w(this, "message") will print "ClassName➤MethodName():(WARNING) message"
    public void w(Object tag, String msg) {
        if(tag == null) {
            warning("null", msg);
            return;
        }
        if(tag instanceof String) {
            warning((String) tag, msg);
            return;
        }

        warning(calcLogPrefix(tag), msg);
    }

    // example: log.e(this, "message") will print "ClassName➤MethodName():(ERROR) message"
    public void e(Object tag, String msg) {
        if(tag == null) {
            error("null", msg);
            return;
        }
        if(tag instanceof String) {
            error((String) tag, msg);
            return;
        }

        error(calcLogPrefix(tag), msg);
    }

    protected @NotNull String calcLogPrefix(Object obj) {
        return calcSimpleName(obj) + "➤" + calcMethodName() + "()";
    }

    protected @NotNull String calcMethodName() {
        return Thread.currentThread().getStackTrace()[4].getMethodName();
    }

    protected @NotNull String calcSimpleName(@NotNull Object obj) {
        return obj.getClass().getSimpleName();
    }
}

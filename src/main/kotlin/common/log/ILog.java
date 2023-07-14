package common.log;

/**
 * ILog Role interface.<br>
 *
 * Simple Logging Operations
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */

public interface ILog {
    void d(Object tag, String msg);
    void w(Object tag, String msg);
    void e(Object tag, String msg);
    void e(Object tag, String msg, Exception e);
}

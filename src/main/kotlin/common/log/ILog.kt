package common.log

/**
 * ILog Role interface.<br></br>
 *
 * Simple Logging Operations
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */
interface ILog {
    fun d(tag: Any?, msg: String?)
    fun w(tag: Any?, msg: String?)
    fun e(tag: Any?, msg: String?)
    fun e(tag: Any?, msg: String?, e: Exception?)
}

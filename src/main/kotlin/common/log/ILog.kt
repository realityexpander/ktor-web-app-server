package common.log

/**
 * ILog Role interface.
 *
 * Simple Logging Operations interface.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin conversion
 */

interface ILog {
    fun d(tag: Any?, msg: String)
    fun w(tag: Any?, msg: String)
    fun e(tag: Any?, msg: String)
    fun e(tag: Any?, msg: String, e: Exception)
}

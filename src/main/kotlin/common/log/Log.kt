package common.log

/**
 * Log Role<br></br>
 * <br></br>
 * Logs to the system console
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */
open class Log : ILog {
    // These could be swapped out for files or network calls.
    private fun debug(tag: String, msg: String) {
        println("$tag: $msg")
    }

    private fun warning(tag: String, msg: String) {
        System.err.println("$tag:(WARNING) $msg")
    }

    private fun error(tag: String, msg: String) {
        System.err.println("$tag:(ERROR) $msg")
    }

    // example: log.d(this, "message") will print "ClassName➤MethodName(): message"
    override fun d(tag: Any?, msg: String) {
        if (tag == null) {
            debug("null", msg)
            return
        }
        if (tag is String) {
            debug(tag, msg)
            return
        }
        debug(calcLogLabel(tag), msg)
    }

    override fun e(tag: Any?, msg: String, e: Exception) {
        if (tag == null) {
            error("null", msg)
            return
        }
        if (tag is String) {
            error(tag, msg)
            return
        }

        // Collect stacktrace to a comma delimited string
        val stacktrace = StringBuilder()
        for (ste in e.stackTrace) {
            stacktrace.append(ste.toString()).append(", ")
        }
        error(calcLogLabel(tag), "$msg, $stacktrace")
        e.printStackTrace() // LEAVE for debugging
    }

    // example: log.w(this, "message") will print "ClassName➤MethodName():(WARNING) message"
    override fun w(tag: Any?, msg: String) {
        if (tag == null) {
            warning("null", msg)
            return
        }
        if (tag is String) {
            warning(tag, msg)
            return
        }
        warning(calcLogLabel(tag), msg)
    }

    // example: log.e(this, "message") will print "ClassName➤MethodName():(ERROR) message"
    override fun e(tag: Any?, msg: String) {
        if (tag == null) {
            error("null", msg)
            return
        }
        if (tag is String) {
            error(tag, msg)
            return
        }
        error(calcLogLabel(tag), msg)
    }

    protected open fun calcLogLabel(obj: Any): String {
        return calcSimpleName(obj) + "➤" + calcMethodName() + "()"
    }

    protected open fun calcMethodName(): String {
        return Thread.currentThread().stackTrace[4].methodName
    }

    protected open fun calcSimpleName(obj: Any): String {
        return obj.javaClass.simpleName
    }
}

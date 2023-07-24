package testUtils

import common.log.Log


open class TestLog(private val shouldOnlyPrintWarningsAndErrors: Boolean) : Log() {
    override fun d(tag: Any?, msg: String) {
        if (shouldOnlyPrintWarningsAndErrors) return
        if (tag == null) {
            super.d("null", msg)
            return
        }
        super.d(calcLogLabel(tag), msg)
    }

    override fun w(tag: Any?, msg: String) {
        if (tag == null) {
            super.w("null", msg)
            return
        }
        super.w(calcLogLabel(tag), msg)
    }

    override fun e(tag: Any?, msg: String) {
        if (tag == null) {
            super.e("null", msg)
            return
        }
        super.e(calcLogLabel(tag), msg)
    }

    override fun e(tag: Any?, msg: String, e: Exception) {
        if (tag == null) {
            super.e("null", msg)
            return
        }
        super.e(calcLogLabel(tag), msg, e)
    }

    protected override fun calcMethodName(): String {
        return Thread.currentThread().stackTrace[4].methodName // note: 4, not 3. Tests run differently than production.
    }
}

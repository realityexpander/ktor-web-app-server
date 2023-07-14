package common

/**
 * Result Utility class.
 *
 *
 * Utility class to hold a result of an operation.
 *
 * ```
 * Example:
 *
 * val result: Result<String> = Result.Success("Hello")
 *
 * Example:
 *
 * val result: Result<String> = Result.Failure(new Exception("Error"))
 * ```
 *
 * @param <T> Type of the result.<br></br>
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */

open class FakeResult<T> {
    class Success<T>(value: T) : FakeResult<T>() {
        private val value: T?

        init {
            this.value = value
        }

        fun value(): T? {
            return value
        }

        override fun toString(): String {
            return value?.toString() ?: "null"
        }
    }

    class Failure<T>(private val exception: Exception?) : FakeResult<T>() {
        fun exception(): Exception? {
            return exception
        }

        override fun toString(): String {
            return if (exception == null) "null" else exception.localizedMessage
        }
    }
}
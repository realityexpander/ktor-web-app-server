package common;

/**
 * Result Utility class.
 * <p>
 * Utility class to hold a result of an operation.
 *
 * @param <T> Type of the result.<br>
 * Example:<br>
 * <pre>
 * Result&lt;String&gt; result = new Result.Success&lt;&gt;("Hello");
 * </pre>
 * Example:
 * <pre>
 * Result&lt;String&gt; result = new Result.Failure&lt;&gt;(new Exception("Error"));
 * </pre>
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */

@SuppressWarnings("unused")  // For T type parameter, which is actually used in Success<T> and Failure<T> subclasses...(todo - Why do we get that warning here?)
public class Result<T> {
    public static class Success<T> extends Result<T> {
        private final T value;

        public Success(T value) {
            this.value = value;
        }

        public T value() {
            return value;
        }

        public String toString() {
            if (value == null)
                return "null";
            return value.toString();
        }
    }

    public static class Failure<T> extends Result<T> {
        private final Exception exception;

        public Failure(Exception exception) {
            this.exception = exception;
        }

        public Exception exception() {
            return exception;
        }

        public String toString() {
            if (exception == null)
                return "null";
            return exception.getLocalizedMessage();
        }
    }
}
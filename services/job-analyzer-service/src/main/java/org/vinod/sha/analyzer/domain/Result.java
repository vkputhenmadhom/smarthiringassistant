package org.vinod.sha.analyzer.domain;

/**
 * Java 21 Sealed Interface: Represents a result of an operation.
 * Can be either a Success with a value, or a Failure with an error message.
 * 
 * Enables exhaustive pattern matching in switch expressions:
 * 
 * <pre>
 * var message = switch(result) {
 *     case Result.Success<?> s -> "Completed: " + s.value();
 *     case Result.Failure f -> "Error: " + f.error();
 * };
 * </pre>
 */
@SuppressWarnings("unchecked")
public sealed interface Result<T> permits Result.Success, Result.Failure {
    
    /**
     * Successful result containing a value
     */
    record Success<T>(T value) implements Result<T> {
    }
    
    /**
     * Failed result containing an error message
     */
    record Failure(String error) implements Result<Object> {
    }
    
    /**
     * Factory method for success
     */
    static <T> Result<T> ok(T value) {
        return new Success<>(value);
    }
    
    /**
     * Factory method for failure
     */
    static <T> Result<T> fail(String error) {
        return (Result<T>) new Failure(error);
    }
    
    /**
     * Check if this result represents success
     */
    default boolean isSuccess() {
        return this instanceof Success<?>;
    }
    
    /**
     * Check if this result represents failure
     */
    default boolean isFailure() {
        return this instanceof Failure;
    }
}

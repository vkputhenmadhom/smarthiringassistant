package org.vinod.smarthiringassistant.commons.exception;

/**
 * Base exception for all application-specific exceptions
 */
public class SmartHiringException extends RuntimeException {
    
    private final int errorCode;
    private final String errorMessage;
    
    public SmartHiringException(String message) {
        super(message);
        this.errorCode = 500;
        this.errorMessage = message;
    }
    
    public SmartHiringException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.errorMessage = message;
    }
    
    public SmartHiringException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = 500;
        this.errorMessage = message;
    }
    
    public SmartHiringException(int errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errorMessage = message;
    }
    
    public int getErrorCode() {
        return errorCode;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
}


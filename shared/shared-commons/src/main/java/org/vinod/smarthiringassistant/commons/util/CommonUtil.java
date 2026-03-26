package org.vinod.smarthiringassistant.commons.util;

import java.util.UUID;

/**
 * Utility class for common operations
 */
public class CommonUtil {
    
    private CommonUtil() {
        throw new AssertionError("Cannot instantiate utility class");
    }
    
    /**
     * Generates a unique UUID string
     */
    public static String generateId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Checks if string is null or empty
     */
    public static boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * Checks if string is not null and not empty
     */
    public static boolean isNotNullOrEmpty(String str) {
        return !isNullOrEmpty(str);
    }
}


package com.example.hrms.tenant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when tenant cannot be resolved from request
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class TenantNotResolvedException extends RuntimeException {
    
    public TenantNotResolvedException(String message) {
        super(message);
    }
    
    public TenantNotResolvedException(String message, Throwable cause) {
        super(message, cause);
    }
}

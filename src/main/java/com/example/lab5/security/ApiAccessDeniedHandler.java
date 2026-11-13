package com.example.lab5.security;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiAccessDeniedHandler.class);

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        log.warn("Access denied for {} {}: {}", request.getMethod(), request.getRequestURI(),
                exception.getClass().getSimpleName());
        if (exception instanceof CsrfException) {
            ErrorResponses.write(response, HttpServletResponse.SC_FORBIDDEN, "CSRF_INVALID",
                    "Missing or invalid CSRF token");
            return;
        }
        ErrorResponses.write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Access denied");
    }
}

package com.example.lab5.security;

import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;

final class ErrorResponses {
    private ErrorResponses() {}

    static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code
                + "\",\"message\":\"" + message + "\"}");
    }
}

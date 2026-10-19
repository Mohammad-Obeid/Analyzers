package health.goverment.service.soap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CbcApiKeyFilter extends OncePerRequestFilter {
    private final String apiKey;

    public CbcApiKeyFilter(@Value("${cbc.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if ("POST".equals(request.getMethod())
                && request.getRequestURI().startsWith(request.getContextPath() + "/ws")) {
            if (apiKey.isBlank()) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "CBC_API_KEY is not configured");
                return;
            }
            String provided = request.getHeader("X-CBC-Api-Key");
            if (provided == null || !MessageDigest.isEqual(apiKey.getBytes(StandardCharsets.UTF_8),
                    provided.getBytes(StandardCharsets.UTF_8))) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}

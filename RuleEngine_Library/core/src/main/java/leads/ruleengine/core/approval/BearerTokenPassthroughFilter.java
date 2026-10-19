package leads.ruleengine.core.approval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carries the caller's bearer token into the security context so changeboard can forward it.
 *
 * <p>changeboard looks up eligible approvers in Centrino with the maker's token, which it reads
 * from a {@link JwtAuthenticationToken} in the security context. Without one the lookup fails and
 * changeboard AUTO-APPROVES. This filter decodes the token's claims and puts it there.</p>
 *
 * <p><b>It never rejects a request and does not verify the signature</b> - it calls nothing and
 * changes no behaviour for requests without a token. Centrino validates the token when changeboard
 * forwards it. Add it to the service's security filter chain; it is not a bean by itself.</p>
 *
 * @author K M Farhat Snigdah
 */
public class BearerTokenPassthroughFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BearerTokenPassthroughFilter.class);
    private static final String PREFIX = "Bearer ";
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(PREFIX.length()).trim();
            try {
                Jwt jwt = decode(token);
                String name = jwt.getClaimAsString("preferred_username");
                JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(),
                        name != null ? name : jwt.getSubject());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (RuntimeException e) {
                log.debug("Bearer token could not be decoded; request continues without it: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }

    /** Read header + claims of a JWT without verifying it. */
    private Jwt decode(String token) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("not a JWT");
        }
        Map<String, Object> headers = json(parts[0]);
        Map<String, Object> claims = new HashMap<>(json(parts[1]));
        Instant issuedAt = epoch(claims.get("iat"));
        Instant expiresAt = epoch(claims.get("exp"));
        // Jwt expects Instants for the registered time claims
        if (issuedAt != null) {
            claims.put("iat", issuedAt);
        }
        if (expiresAt != null) {
            claims.put("exp", expiresAt);
        }
        claims.computeIfPresent("nbf", (k, v) -> epoch(v));
        return new Jwt(token, issuedAt, expiresAt, headers, claims);
    }

    private Map<String, Object> json(String base64Url) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(base64Url);
            return objectMapper.readValue(new String(bytes, StandardCharsets.UTF_8), MAP);
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid JWT segment", e);
        }
    }

    private static Instant epoch(Object value) {
        return value instanceof Number n ? Instant.ofEpochSecond(n.longValue()) : null;
    }
}

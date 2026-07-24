package pl.romcio.driperska.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;
import pl.romcio.driperska.common.AppInstance;

/** Issues and validates stateless JWT access and refresh tokens. */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_BOOT = "bid";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final JwtProperties properties;
    private final AppInstance appInstance;

    public JwtService(JwtProperties properties, AppInstance appInstance) {
        this.properties = properties;
        this.appInstance = appInstance;
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String issueAccessToken(UUID accountId, String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(accountId.toString())
                .claim("username", username)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_BOOT, appInstance.bootId())
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(properties.accessTokenMinutes(), ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public String issueRefreshToken(UUID accountId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(accountId.toString())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .claim(CLAIM_BOOT, appInstance.bootId())
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(properties.refreshTokenDays(), ChronoUnit.DAYS)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    /**
     * True when the token was issued by the currently running process. Tokens minted before a
     * restart carry a stale {@code bid} and are rejected, so every session ends on backend restart.
     */
    public boolean isCurrentBoot(Claims claims) {
        return appInstance.bootId().equals(claims.get(CLAIM_BOOT, String.class));
    }

    public long accessTokenSeconds() {
        return properties.accessTokenMinutes() * 60;
    }
}

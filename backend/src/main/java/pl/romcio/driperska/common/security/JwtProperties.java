package pl.romcio.driperska.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long accessTokenMinutes,
        long refreshTokenDays) {

    public JwtProperties {
        if (accessTokenMinutes <= 0) {
            accessTokenMinutes = 15;
        }
        if (refreshTokenDays <= 0) {
            refreshTokenDays = 7;
        }
    }
}

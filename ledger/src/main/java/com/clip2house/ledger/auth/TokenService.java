package com.clip2house.ledger.auth;

import com.clip2house.ledger.common.ApiException;
import com.clip2house.ledger.config.GameProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {

    private final AccessTokenRepository tokens;
    private final GameProperties props;
    private final SecureRandom random = new SecureRandom();

    public TokenService(AccessTokenRepository tokens, GameProperties props) {
        this.tokens = tokens;
        this.props = props;
    }

    public record IssuedToken(String token, Instant expiresAt) {}

    @Transactional
    public IssuedToken issue(Long playerId, AccessToken.Kind kind) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant expiresAt = Instant.now().plus(
                kind == AccessToken.Kind.GUEST ? props.auth().guestTokenTtl() : props.auth().accountTokenTtl());
        tokens.save(new AccessToken(hash(token), playerId, kind, expiresAt));
        return new IssuedToken(token, expiresAt);
    }

    @Transactional(readOnly = true)
    public Long resolvePlayerId(String token) {
        AccessToken found = tokens.findById(hash(token))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_TOKEN", "유효하지 않은 토큰입니다."));
        if (found.isExpired()) {
            throw ApiException.unauthorized("EXPIRED_TOKEN", "만료된 토큰입니다.");
        }
        return found.getPlayerId();
    }

    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원 JVM", e);
        }
    }
}

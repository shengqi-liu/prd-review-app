package com.prdreview.common.util;

import com.prdreview.auth.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类（HS256 算法）。
 *
 * <p>放置于 domain 层，避免 application ↔ api 循环依赖。</p>
 * <p>JWT 包含 claim：{@code userId}、{@code username}、{@code role}。</p>
 */
@Slf4j
@Component
public class JwtUtil {

    private static final String CLAIM_USER_ID  = "userId";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE     = "role";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400}")
    private long expirationSeconds;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("jwt.secret 长度必须 ≥ 32 字符，请检查配置");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT token。
     */
    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000L);

        return Jwts.builder()
            .claim(CLAIM_USER_ID,  user.getId())
            .claim(CLAIM_USERNAME, user.getUsername())
            .claim(CLAIM_ROLE,     user.getRole().name())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(secretKey)
            .compact();
    }

    /**
     * 解析 token，返回 Claims；token 非法或过期时抛出对应异常。
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * 从 token 中提取 userId（不验证过期）。
     */
    public Long getUserId(String token) {
        return parseToken(token).get(CLAIM_USER_ID, Long.class);
    }

    /**
     * 从 token 中提取 username。
     */
    public String getUsername(String token) {
        return parseToken(token).get(CLAIM_USERNAME, String.class);
    }

    /**
     * 从 token 中提取 role。
     */
    public String getRole(String token) {
        return parseToken(token).get(CLAIM_ROLE, String.class);
    }

    /**
     * 验证 token 是否有效（格式合法且未过期）。
     */
    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT 已过期: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.debug("JWT 无效: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 判断 token 是否已过期。
     */
    public boolean isTokenExpired(String token) {
        try {
            parseToken(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}

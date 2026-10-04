package ent.kz.entbackend.service;

import ent.kz.entbackend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final String jwtSecret;
  private final long jwtExpirationMs;

  public JwtService(
    @Value("${app.jwt.secret}") String jwtSecret,
    @Value("${app.jwt.expiration-ms}") long jwtExpirationMs
  ) {
    if (
      jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32 ||
      jwtSecret.startsWith("replace-") ||
      jwtExpirationMs <= 0
    ) {
      throw new IllegalArgumentException(
        "Configure a random JWT secret of at least 32 bytes and a positive expiry"
      );
    }
    this.jwtSecret = jwtSecret;
    this.jwtExpirationMs = jwtExpirationMs;
  }

  public String generateToken(User user) {
    Instant now = Instant.now();

    return Jwts.builder()
      .subject(user.getEmail())
      .claim("userId", user.getId().toString())
      .claim("role", user.getRole().name())
      .issuedAt(Date.from(now))
      .expiration(Date.from(now.plusMillis(jwtExpirationMs)))
      .signWith(getSigningKey())
      .compact();
  }

  public String extractEmail(String token) {
    try {
      return extractAllClaims(token).getSubject();
    } catch (JwtException | IllegalArgumentException exception) {
      return null;
    }
  }

  public boolean isTokenValid(String token, User user) {
    try {
      Claims claims = extractAllClaims(token);
      return (
        user.isEnabled() &&
        user.getId().toString().equals(claims.get("userId", String.class)) &&
        user.getEmail().equalsIgnoreCase(claims.getSubject()) &&
        claims.getExpiration() != null &&
        claims.getExpiration().after(new Date())
      );
    } catch (JwtException | IllegalArgumentException exception) {
      return false;
    }
  }

  private Claims extractAllClaims(String token) {
    return Jwts.parser()
      .verifyWith(getSigningKey())
      .build()
      .parseSignedClaims(token)
      .getPayload();
  }

  private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
  }
}

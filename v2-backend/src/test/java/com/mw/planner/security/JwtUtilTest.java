package com.mw.planner.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

  private static final String SECRET = "test-secret-key-at-least-256-bits-long-for-hs256";
  private JwtUtil jwtUtil;

  @BeforeEach
  void setUp() {
    jwtUtil = new JwtUtil();
  }

  @Test
  void extractUsername_WithValidToken_ReturnsSubject() throws Exception {
    String token = createToken("user@example.com", Instant.now().plusSeconds(3600));
    assertThat(jwtUtil.extractUsername(token)).isEqualTo("user@example.com");
  }

  @Test
  void extractUsername_WithInvalidToken_ThrowsRuntimeException() {
    assertThatThrownBy(() -> jwtUtil.extractUsername("invalid.token.here"))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(java.text.ParseException.class);
  }

  @Test
  void validateToken_WithValidNotExpiredToken_ReturnsTrue() throws Exception {
    String token = createToken("user@example.com", Instant.now().plusSeconds(3600));
    assertThat(jwtUtil.validateToken(token)).isTrue();
  }

  @Test
  void validateToken_WithExpiredToken_ReturnsFalse() throws Exception {
    String token = createToken("user@example.com", Instant.now().minusSeconds(3600));
    assertThat(jwtUtil.validateToken(token)).isFalse();
  }

  @Test
  void validateToken_WithInvalidToken_ThrowsRuntimeException() {
    assertThatThrownBy(() -> jwtUtil.validateToken("not.a.valid.jwt"))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(java.text.ParseException.class);
  }

  private String createToken(String subject, Instant expiration) throws Exception {
    SecretKey key = new SecretKeySpec(SECRET.getBytes(), "HmacSHA256");
    JWSSigner signer = new MACSigner(key);
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder().subject(subject).expirationTime(Date.from(expiration)).build();
    SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    signedJWT.sign(signer);
    return signedJWT.serialize();
  }
}

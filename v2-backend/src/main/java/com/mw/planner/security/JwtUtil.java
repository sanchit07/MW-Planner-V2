package com.mw.planner.security;

import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

  public String extractUsername(String token) {
    try {
      return SignedJWT.parse(token).getJWTClaimsSet().getSubject();
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean validateToken(String token) {
    try {
      return SignedJWT.parse(token)
          .getJWTClaimsSet()
          .getExpirationTime()
          .after(new java.util.Date());
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }
}

package com.roconmachine.security.auth.jwt;



import com.roconmachine.security.auth.model.TokenClaims;

import java.time.Duration;

public interface TokenIssuer {

    String issue(TokenClaims claims);
    String refresh(String existingToken, Duration newTimeToLive);
}

package com.roconmachine.security.auth.jwt;

import com.roconmachine.security.auth.model.AuthenticatedPrincipal;

public interface TokenValidator {

   AuthenticatedPrincipal validate(String token) throws TokenValidationException;
}

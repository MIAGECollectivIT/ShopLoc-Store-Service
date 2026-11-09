package com.shoploc.store_service.security;

import com.shoploc.store_service.exception.AccessDeniedException;
import com.shoploc.store_service.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC interceptor validating JWT Bearer token authenticity and checking user role
 * authorization for protected operations. Allows public read access for endpoints not annotated
 * with @RequiresRole.
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

  private static final Logger log = LoggerFactory.getLogger(RoleInterceptor.class);

  public static final String AUTH_HEADER = "Authorization";
  public static final String TOKEN_PREFIX = "Bearer ";

  public static final String ATTR_USER_ID = "authenticatedUserId";
  public static final String ATTR_USER_DB_ID = "authenticatedUserDbId";
  public static final String ATTR_USER_EMAIL = "authenticatedUserEmail";
  public static final String ATTR_USER_ROLE = "authenticatedUserRole";
  public static final String ATTR_CLAIMS = "jwtClaims";
  public static final String ATTR_RAW_TOKEN = "rawJwtToken";

  private static final Set<String> PUBLIC_EXACT_PATHS = Set.of("/swagger-ui.html", "/error");

  private final JwtValidatorService jwtValidatorService;

  public RoleInterceptor(JwtValidatorService jwtValidatorService) {
    this.jwtValidatorService = jwtValidatorService;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }

    String path = request.getRequestURI();
    if (isWhitelisted(path)) {
      return true;
    }

    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return true;
    }

    RequiresRole requiresRole = handlerMethod.getMethodAnnotation(RequiresRole.class);
    if (requiresRole == null) {
      requiresRole = handlerMethod.getBeanType().getAnnotation(RequiresRole.class);
    }

    String authHeader = request.getHeader(AUTH_HEADER);

    // If no token is provided
    if (authHeader == null
        || !authHeader.regionMatches(true, 0, TOKEN_PREFIX, 0, TOKEN_PREFIX.length())) {
      if (requiresRole != null) {
        throw new UnauthorizedException(
            "En-tête d'autorisation manquant ou invalide. Un jeton Bearer JWT est requis.");
      }
      // Public access allowed
      return true;
    }

    String token = authHeader.substring(TOKEN_PREFIX.length()).trim();
    if (token.isEmpty()) {
      if (requiresRole != null) {
        throw new UnauthorizedException("Jeton JWT vide ou manquant.");
      }
      return true;
    }

    Claims claims;
    try {
      claims = jwtValidatorService.validateAndExtractClaims(token);
    } catch (UnauthorizedException ex) {
      if (requiresRole == null) {
        // On a public endpoint (catalog/detail), an expired or revoked token must not prevent
        // viewing
        log.debug(
            "Jeton invalide/expiré sur endpoint public {}. Traitement en mode visiteur anonyme : {}",
            path,
            ex.getMessage());
        return true;
      }
      throw ex;
    }

    String userRole = jwtValidatorService.extractRole(claims);

    request.setAttribute(ATTR_USER_ID, claims.get("userId", String.class));
    Object dbIdObj = claims.get("dbId");
    if (dbIdObj instanceof Number num) {
      request.setAttribute(ATTR_USER_DB_ID, num.longValue());
    } else if (dbIdObj != null) {
      try {
        request.setAttribute(ATTR_USER_DB_ID, Long.parseLong(dbIdObj.toString()));
      } catch (NumberFormatException ignored) {
      }
    }
    request.setAttribute(ATTR_USER_EMAIL, claims.get("email", String.class));
    request.setAttribute(ATTR_USER_ROLE, userRole);
    request.setAttribute(ATTR_CLAIMS, claims);
    request.setAttribute(ATTR_RAW_TOKEN, token);

    if (requiresRole != null) {
      String[] allowedRoles = requiresRole.value();
      if (allowedRoles != null && allowedRoles.length > 0) {
        boolean hasRole = false;
        for (String allowed : allowedRoles) {
          if (allowed != null && allowed.trim().equalsIgnoreCase(userRole)) {
            hasRole = true;
            break;
          }
        }
        if (!hasRole) {
          throw new AccessDeniedException(
              "Accès refusé : un des rôles "
                  + Arrays.toString(allowedRoles)
                  + " est requis pour effectuer cette action");
        }
      }
    }

    return true;
  }

  private boolean isWhitelisted(String path) {
    if (PUBLIC_EXACT_PATHS.contains(path)) {
      return true;
    }
    return path.startsWith("/actuator")
        || path.startsWith("/v3/api-docs")
        || path.startsWith("/swagger-ui");
  }
}

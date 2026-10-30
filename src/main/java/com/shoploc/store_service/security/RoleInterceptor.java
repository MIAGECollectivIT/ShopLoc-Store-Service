package com.shoploc.store_service.security;

import com.shoploc.store_service.exception.AccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Intercepteur Spring MVC vérifiant la présence et la conformité du header 'X-User-Role'
 * pour les méthodes ou contrôleurs annotés avec @RequiresRole.
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    public static final String ROLE_HEADER = "X-User-Role";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequiresRole requiresRole = handlerMethod.getMethodAnnotation(RequiresRole.class);
        if (requiresRole == null) {
            requiresRole = handlerMethod.getBeanType().getAnnotation(RequiresRole.class);
        }

        if (requiresRole != null) {
            String roleHeader = request.getHeader(ROLE_HEADER);
            String expectedRole = requiresRole.value();

            if (roleHeader == null || !roleHeader.trim().equalsIgnoreCase(expectedRole.trim())) {
                throw new AccessDeniedException(
                        "Accès refusé : rôle '" + expectedRole + "' requis pour effectuer cette action"
                );
            }
        }

        return true;
    }
}

package com.myproject.questservice.auth.support;

import com.myproject.questservice.auth.CurrentUserContext;
import com.myproject.questservice.auth.CurrentUserProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class SecurityCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Optional<UUID> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof CurrentUserContext context) {
            return Optional.ofNullable(context.userId());
        }

        if (principal instanceof String value && !value.isBlank()) {
            try {
                return Optional.of(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }

        if (principal instanceof Jwt jwt) {
            String uid = jwt.getClaimAsString("uid");
            if (uid == null || uid.isBlank()) {
                uid = jwt.getSubject();
            }
            if (uid == null || uid.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(UUID.fromString(uid));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }
}

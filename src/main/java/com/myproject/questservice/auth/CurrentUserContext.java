package com.myproject.questservice.auth;

import java.util.UUID;

public record CurrentUserContext(
        UUID userId
) {
}

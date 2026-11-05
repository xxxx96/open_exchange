package com.openexchange.userservice.security;

import lombok.Getter;

public record UserPrincipal(Long userId, String email) {
}

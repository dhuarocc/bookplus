package com.bookplus.auth.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("UserRole (jerarquía)")
class UserRoleTest {

    @Test
    @DisplayName("authority() devuelve el nombre con prefijo ROLE_")
    void authority() {
        assertThat(UserRole.ROLE_ADMIN.authority()).isEqualTo("ROLE_ADMIN");
        assertThat(UserRole.ROLE_USER.authority()).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("isAtLeast() respeta la jerarquía SUPERADMIN > ADMIN > EDITOR > REPARTIDOR > USER")
    void hierarchy() {
        assertThat(UserRole.ROLE_ADMIN.isAtLeast(UserRole.ROLE_USER)).isTrue();
        assertThat(UserRole.ROLE_SUPERADMIN.isAtLeast(UserRole.ROLE_ADMIN)).isTrue();
        assertThat(UserRole.ROLE_USER.isAtLeast(UserRole.ROLE_ADMIN)).isFalse();
        assertThat(UserRole.ROLE_ADMIN.isAtLeast(UserRole.ROLE_ADMIN)).isTrue(); // igual también cumple
    }
}

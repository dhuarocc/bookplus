package com.bookplus.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica la migración a Argon2id manteniendo compatibilidad con los hashes BCrypt
 * existentes (DelegatingPasswordEncoder). No necesita Spring.
 */
class PasswordEncoderArgon2Test {

    private final PasswordEncoder encoder = new SecurityConfig(null, null).passwordEncoder();

    @Test
    void las_contrasenas_nuevas_se_cifran_con_argon2() {
        String hash = encoder.encode("S3cret!Pass");

        assertThat(hash).startsWith("{argon2}");
        assertThat(encoder.matches("S3cret!Pass", hash)).isTrue();
        assertThat(encoder.matches("incorrecta", hash)).isFalse();
    }

    @Test
    void sigue_validando_hashes_bcrypt_heredados_sin_prefijo() {
        // Hash creado por el encoder antiguo (BCrypt puro, sin prefijo {bcrypt}).
        String legacy = new BCryptPasswordEncoder(12).encode("OldPass1");

        assertThat(encoder.matches("OldPass1", legacy)).isTrue();
        assertThat(encoder.matches("nope", legacy)).isFalse();
    }

    @Test
    void marca_para_re_hashear_los_hashes_antiguos_pero_no_los_argon2() {
        String legacy = new BCryptPasswordEncoder(12).encode("OldPass1");

        // Un hash BCrypt antiguo debe re-hashearse a Argon2 en el próximo login...
        assertThat(encoder.upgradeEncoding(legacy)).isTrue();
        // ...pero uno ya en Argon2 no.
        assertThat(encoder.upgradeEncoding(encoder.encode("OldPass1"))).isFalse();
    }
}

# Hashing de contraseñas con Argon2id

Las contraseñas nunca se guardan en claro: se almacena un *hash* del que no se puede volver
atrás. Pero no todos los algoritmos de hash sirven — hay que usar uno **lento y resistente a
hardware especializado** (GPUs/ASIC) para que un atacante con la base de datos robada no
pueda probar millones de contraseñas por segundo. Migramos de **BCrypt** a **Argon2id**, el
algoritmo recomendado hoy por OWASP (ganador de la Password Hashing Competition).

## Por qué Argon2id

- **Memory-hard**: además de tiempo, exige mucha **memoria**, lo que neutraliza el paralelismo
  masivo de GPUs/ASIC que sí ataca bien a BCrypt.
- **Argon2id** combina resistencia a ataques de canal lateral y a *trade-offs* de memoria;
  es la variante recomendada para almacenar contraseñas.

## Migración sin romper nada

El reto es que ya hay usuarios con contraseñas hasheadas en **BCrypt**. No se pueden
re-hashear sin la contraseña en claro. La solución es un **`DelegatingPasswordEncoder`** de
Spring Security:

- Las contraseñas **nuevas** se cifran con Argon2 y se guardan con el prefijo `{argon2}`.
- Al validar, el prefijo (`{argon2}`, `{bcrypt}`…) indica qué algoritmo usar, así que los
  hashes **antiguos siguen funcionando**.
- Para los BCrypt heredados que se guardaron **sin prefijo**, se configura un
  `defaultPasswordEncoderForMatches` (BCrypt) como respaldo.
- `upgradeEncoding()` marca los hashes en formato antiguo para **re-hashearlos a Argon2 en el
  próximo login** del usuario (rehash transparente, sin pedir nada).

Resultado: la migración es **gradual y sin fricción** — nadie tiene que resetear su
contraseña, y el parque va pasando a Argon2 a medida que la gente entra.

## La integración

- **Dependencia**: `org.bouncycastle:bcprov-jdk18on` (lo requiere `Argon2PasswordEncoder`;
  versión gestionada por el BOM de Spring Boot).
- **`SecurityConfig.passwordEncoder()`** devuelve el `DelegatingPasswordEncoder`
  (`{argon2}` por defecto + `{bcrypt}` + fallback BCrypt sin prefijo). Todo el código de auth
  (registro, login, cambio/reset de contraseña) ya usaba el bean `PasswordEncoder`, así que
  **no hubo que tocar nada más**.

## Verificación

`PasswordEncoderArgon2Test` (unit, sin Spring) comprueba: las contraseñas nuevas salen con
`{argon2}` y validan; un hash BCrypt heredado sin prefijo **sigue validando**; y
`upgradeEncoding` marca los BCrypt antiguos para re-hash pero no los Argon2. Corre en el
`mvn test` normal.

## Siguiente nivel

- **Ajuste de parámetros** de Argon2 (memoria, iteraciones, paralelismo) según el hardware de
  producción, midiendo el tiempo objetivo (~0,5–1 s por hash).
- **Pepper** (secreto global, guardado en Vault) combinado con el hash, para que la base de
  datos robada no baste por sí sola.

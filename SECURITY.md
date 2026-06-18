# Seguridad de la cadena de suministro

Los bancos y grandes empresas auditan **de qué está hecho** el software y exigen detectar
vulnerabilidades antes de desplegar. BookPlus incorpora estas prácticas en el CI/CD.

## Qué se ha añadido

- **SBOM (CycloneDX)** — `.github/workflows/security.yml` genera el *Software Bill of Materials*
  del repositorio (inventario completo de dependencias) como artefacto. Es trazabilidad: si
  mañana se descubre un CVE en una librería, sabes al instante si te afecta.
- **Trivy** — escaneo de vulnerabilidades, secretos y misconfiguraciones del repositorio. Los
  hallazgos se suben a la pestaña **Security** de GitHub (formato SARIF).
- **OWASP Dependency-Check** — analiza las dependencias contra la base de CVEs conocidos y
  genera un informe HTML descargable.
- **Firma de imágenes (Sigstore/Cosign)** — en `cd.yml`, cada imagen publicada en GHCR se
  **firma** de forma *keyless* (OIDC de GitHub, sin gestionar claves). Garantiza que lo que se
  despliega es exactamente lo que construyó el pipeline (anti-tampering). Verificación:
  ```bash
  cosign verify ghcr.io/<owner>/bookplus-order-service:latest \
    --certificate-identity-regexp '.*' --certificate-oidc-issuer https://token.actions.githubusercontent.com
  ```
- **Dependabot** — `.github/dependabot.yml` abre PRs automáticas cuando hay actualizaciones de
  dependencias (Maven por servicio, npm del frontend, GitHub Actions).

## Cómo encaja con lo que ya había

Junto a **SonarCloud** (calidad/vulnerabilidades en el código) y **Vault** (secretos cifrados),
esto cubre las tres capas que miran en banca: **código** seguro, **dependencias** sin CVEs e
**imágenes** firmadas y trazables.

## Siguiente nivel

- **Vault dynamic secrets**: credenciales de BD temporales generadas y rotadas por Vault.
- **DAST con OWASP ZAP**: escaneo dinámico de la aplicación en ejecución.
- **Política de admisión**: rechazar en el despliegue imágenes sin firma válida.

# Jenkins — pipeline CI/CD avanzado (complemento de GitHub Actions)

El CI/CD principal del proyecto es **GitHub Actions** (workflows CI, CD y Security). Este
`Jenkinsfile` ofrece **el mismo pipeline expresado en Jenkins**, porque en muchos entornos
corporativos —especialmente banca— Jenkins sigue siendo el estándar (a menudo on-premise, por
políticas internas). No reemplaza a GitHub Actions: demuestra que el pipeline es **portable**
y que el flujo está como *pipeline-as-code* también en la herramienta más extendida del sector.

## Por qué los dos a la vez

Ambos hacen **lo mismo** y son **intercambiables**: si borraras `.github/workflows/` seguirías
teniendo CI/CD completo solo con Jenkins, y viceversa. En un proyecto real se elige **uno**
según la organización (GitHub Actions en cloud-native/startups; Jenkins en banca/on-premise).
Aquí coexisten **a propósito**, como demostración de dominio de ambas herramientas.

## Etapas del pipeline (`Jenkinsfile`)

1. **Checkout** — descarga el código y calcula el hash corto del commit (`SHORT_SHA`); hace
   `stash` del código para reutilizarlo entre agentes sin re-clonar.
2. **Build & Test** — dentro de un contenedor `maven:3.9-eclipse-temurin-21`, ejecuta el
   `mvn test` de los **9 microservicios EN PARALELO** (un branch por servicio) y publica los
   resultados JUnit.
3. **SonarCloud + Quality Gate** *(parametrizable)* — análisis de calidad con
   `withSonarQubeEnv` y, lo importante, `waitForQualityGate abortPipeline: true`: si el
   **Quality Gate** (cobertura, bugs, vulnerabilidades) no se cumple, **el pipeline se aborta**.
4. **Package** — empaqueta los JAR (`-DskipTests`) y los archiva con `stash`.
5. **Build & Push images** *(parametrizable)* — construye y publica las imágenes en GHCR
   **EN PARALELO**, etiquetadas con `SHORT_SHA`.
6. **Security scan (Trivy)** *(parametrizable)* — escaneo de vulnerabilidades de cada imagen,
   también en paralelo.
7. **Deploy** *(parametrizable)* — paso de **aprobación manual** (`input`) con *timeout* antes
   de desplegar al entorno elegido.

## Características de nivel avanzado

- **`agent none` + agentes por etapa**: cada etapa levanta exactamente el contenedor que
  necesita (Maven para build, el nodo con Docker para imágenes), en vez de un agente monolítico.
- **Paralelismo**: tests y construcción de imágenes se reparten en branches paralelos (uno por
  microservicio), reduciendo el tiempo total.
- **Parámetros del build** (`RUN_SONAR`, `PUSH_IMAGES`, `DEPLOY_ENV`): el mismo Jenkinsfile
  sirve para un PR (solo test) o para una release completa, sin tocar el código.
- **Quality Gate que aborta**: la calidad es un gate real, no informativo.
- **`stash`/`unstash`**: el código y los artefactos viajan entre agentes sin re-clonar.
- **Caché de Maven** (`~/.m2` montado) entre ejecuciones.
- **Aprobación con timeout** para el deploy; **reportes JUnit**; **disableConcurrentBuilds**
  para evitar ejecuciones pisándose.

## Equivalencia con GitHub Actions

| GitHub Actions        | Jenkins (`Jenkinsfile`)                         |
|-----------------------|-------------------------------------------------|
| workflow **CI**       | Build & Test (paralelo), SonarCloud + Quality Gate |
| workflow **CD**       | Package, Build & Push images, Deploy            |
| workflow **Security** | Security scan (Trivy)                           |

| Concepto      | GitHub Actions          | Jenkins                          |
|---------------|-------------------------|----------------------------------|
| Hospedaje     | SaaS (lo corre GitHub)  | Autohospedado (tu servidor)      |
| Configuración | YAML (`.github/workflows/`) | Groovy (`Jenkinsfile`)        |
| Disparador    | Eventos de GitHub       | Webhooks, polling, cron, manual  |
| Mantenimiento | Ninguno                 | Tú: servidor, plugins, agentes   |
| Típico en     | Cloud-native, OSS       | Banca, on-premise, legacy        |

## Cómo levantarlo en local

```bash
# 1) Arrancar Jenkins (imagen propia con el CLI de Docker y plugins incluidos)
docker compose -p bookplus-jenkins -f docker-compose.jenkins.yml up -d --build

# 2) Contraseña inicial de administrador (MSYS_NO_PATHCONV evita el mangling de rutas en Git Bash)
MSYS_NO_PATHCONV=1 docker exec bookplus-jenkins cat /var/jenkins_home/secrets/initialAdminPassword

# 3) http://localhost:8080 -> completar el asistente (los plugins ya vienen preinstalados).
# Parar:  docker compose -p bookplus-jenkins -f docker-compose.jenkins.yml down   (añade -v para borrar el volumen)
```

`jenkins/Dockerfile` extiende `jenkins/jenkins:lts-jdk21` añadiendo el **CLI de Docker** y los
plugins (Pipeline, Docker Pipeline, Git, Credentials Binding, JUnit, AnsiColor, Workspace
Cleanup, SonarQube Scanner). El compose monta el **socket de Docker** del host para construir
imágenes desde el pipeline.

## Configuración necesaria en Jenkins

- **Credenciales** (Manage Jenkins → Credentials):
  - `ghcr` (Username/Password) — usuario y token con permiso `write:packages` para GHCR.
- **Servidor SonarQube** (Manage Jenkins → System → SonarQube servers): uno llamado
  **`SonarCloud`** con la URL `https://sonarcloud.io` y el token de autenticación.
- **Job**: *New Item → Pipeline → Pipeline script from SCM* apuntando a este repo y al
  `Jenkinsfile` de la raíz. Recomendado: *Multibranch Pipeline* para construir ramas y PRs.

## Verificación

Es **infraestructura/configuración** (no hay test unitario que lo cubra). Se verifica
levantando Jenkins con el compose y ejecutando el job: las etapas deben pasar como en GitHub
Actions. En producción, Jenkins iría en un servidor con agentes dedicados, no en el contenedor
local de prueba.

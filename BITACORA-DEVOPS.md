# BookPlus — Bitácora DevOps (CI/CD · Cobertura · Calidad)

Resumen de todo lo realizado en la sesión de DevOps y dónde retomar. Pensado como
**punto de continuación** para el despliegue en Oracle Cloud.

> Repo en GitHub: **github.com/dhuarocc/bookplus** (público) · Organización SonarCloud: **dhuarocc**

---

## 1. Qué se hizo en esta sesión

### 1.1 Pruebas automatizadas (tests)
- Se escribieron/repararon los tests de los **8 servicios Java**; los 8 pasan en verde.
- **order-service**: tests de la **saga de compra** (máquina de estados del pedido + compensaciones de cancelación/reembolso). 16/16 verdes.
- Se alinearon tests heredados que nunca se ejecutaban (el build de Docker omite tests con `-Dmaven.test.skip=true`).

### 1.2 Cobertura de código (JaCoCo)
- Plugin **JaCoCo** añadido a los 8 `pom.xml`. Genera `target/site/jacoco/jacoco.xml` y el HTML navegable.
- Mide instructions, **branches**, lines, methods y complejidad.
- Script **`run-coverage.sh`**: corre tests + cobertura de los 8 servicios y da un resumen PASS/FAIL.
- Cobertura actual aproximada por servicio: 14–38 % (solo hay tests de dominio; es una base, no el 100 %).

### 1.3 Calidad (SonarCloud)
- Organización **dhuarocc** creada en sonarcloud.io (plan Free, repos públicos).
- Los **8 proyectos** (`dhuarocc_bookplus-*`) se analizan en cada push vía el job `sonar` del CI.
- Propiedades `sonar.*` configuradas en cada pom (projectKey único, ruta del reporte JaCoCo, exclusiones de DTOs/config).
- Mide: **bugs, code smells, vulnerabilidades, security hotspots, cobertura y duplicación**, con Quality Gate.

### 1.4 CI/CD (GitHub Actions)
- **`.github/workflows/ci.yml`** — en cada push/PR: tests + cobertura (8 servicios), build del frontend y análisis SonarCloud.
- **`.github/workflows/cd.yml`** — en push a `main` / tags `v*`: construye **11 imágenes Docker multi-arquitectura (amd64 + arm64)** y las publica en **GHCR** (`ghcr.io/dhuarocc/bookplus-*`). Job de **deploy por SSH** listo (se activa al poner los secretos).
- **`docker-compose.deploy.yml`** — variante del compose que usa imágenes del registro (para el servidor).

### 1.5 Bugs de calidad corregidos (de SonarCloud)
| Servicio | Archivo | Regla | Arreglo |
|----------|---------|-------|---------|
| order-service | `Order.java` | S2119 | `SecureRandom` reutilizado como campo `static final` |
| order-service | `UpdateOrderStatusUseCaseImpl.java` | S2230 | `@Transactional` movido de método privado a **nivel de clase** (antes no tenía efecto) |
| auth-service | `JwtService.java` | S2119 | `SecureRandom` reutilizado |
| catalog-service | `Slug.java` | S5850 | Regex `^-\|-$` agrupada → `(?:^-)\|(?:-$)` |
| inventory-service | `StockReservation.java` | S2184 | `30 * 60` → `30L * 60` (evita overflow int→long) |
| notification-service | `EmailTemplateService.java` | S8688 | `Year.now()` → `Year.now(ZoneId.systemDefault())` |

> Quedan algunos issues menores del mismo estilo (patrones repetidos) que se pueden limpiar al ritmo deseado; no son bloqueantes.

---

## 2. Estado actual (al cerrar la sesión)

- ✅ Repo público en GitHub con CI y CD corriendo.
- ✅ CI verde (8 tests + frontend) · SonarCloud analizando los 8 proyectos en la rama `main`.
- ✅ CD construyendo y publicando 11 imágenes multi-arch en GHCR.
- ⚠️ **Pendiente de push:** los últimos arreglos de SonarCloud (los 6 bugs de la tabla 1.5). Ejecutar:
  ```bash
  cd /c/proyecto-book-plus
  git add .
  git commit -m "fix(sonar): bugs de calidad (SecureRandom, transactional, regex, long, zona)"
  git push
  ```
- ⏳ **No desplegado todavía** en ningún servidor (todo local + pipeline en la nube).

### Detalle resuelto que conviene recordar
- SonarCloud creó los proyectos con la rama principal **`master`**, pero el repo usa **`main`**. Se corrigió **por proyecto** (Branches → renombrar `master`→`main`). Si en el futuro se crea un proyecto nuevo, repetir ese paso o **enlazarlo a GitHub** ("Bind project") para que tome `main` automáticamente.

---

## 3. Mañana: desplegar en Oracle Cloud (punto de partida)

Guía completa paso a paso en **`DESPLIEGUE-ORACLE.md`**. Resumen del plan recomendado:

1. **Crear instancia** Always Free **ARM (Ampere A1)**, Ubuntu 22.04, 4 vCPU / 24 GB.
2. **Abrir puertos 80/443** en las DOS capas (Security List de Oracle + iptables de Ubuntu).
3. **Instalar Docker** + Docker Compose.
4. **Clonar el repo**, crear el `.env` real (claves nuevas, NO las del repo) con `REGISTRY=ghcr.io/dhuarocc` y `TAG=latest`.
5. `docker login ghcr.io` + `docker compose -f docker-compose.deploy.yml pull && up -d`.
6. **Empezar PRIVADO con Tailscale** (VPN gratis) — desplegar en la nube sin exponer a internet. Ideal para aprender.
7. Solo al final, si se quiere público: dominio + HTTPS (Caddy/Let's Encrypt) + endurecimiento (§5 de la guía: cambiar secretos, no exponer BD/Kafka/ES, quitar MailHog/Zipkin).

### Recordatorios importantes para el deploy
- Como se tocó código de producción (Order, JwtService, etc.), al desplegar las imágenes deben ser **frescas** — el CD las reconstruye al hacer push a `main`.
- El `.env` del servidor se crea **a mano allá**, nunca se sube al repo (está en `.gitignore`).
- Migrar a AWS más adelante = mismo proceso (las imágenes multi-arch sirven en x86 y ARM) + `pg_dump`/restore de las BD.

---

## 4. Mapa de documentación del proyecto

| Archivo | Contenido |
|---------|-----------|
| **`CALIDAD-Y-PRUEBAS.md`** | Tests, JaCoCo, SonarQube/SonarCloud manual, todos los comandos |
| **`CI-CD.md`** | Pipeline GitHub Actions (CI + CD), secretos, GHCR, deploy |
| **`DESPLIEGUE-ORACLE.md`** | Despliegue en Oracle Cloud Free + endurecimiento de seguridad |
| **`BITACORA-DEVOPS.md`** | Este resumen / punto de continuación |
| `docker-compose.full.yml` | Stack completo para correr **local** (build) |
| `docker-compose.deploy.yml` | Stack para el **servidor** (imágenes de GHCR) |
| `docker-compose.sonar.yml` | SonarQube local (alternativa a SonarCloud) |
| `run-coverage.sh` | Tests + cobertura de los 8 servicios en contenedor Maven |

---

## 5. Comandos de referencia rápida

```bash
# Tests + cobertura de TODOS los servicios (local, contenedor Maven)
bash run-coverage.sh

# Subir cambios → dispara CI + CD + análisis SonarCloud
git add . && git commit -m "..." && git push

# (En el servidor Oracle) desplegar / actualizar
docker compose -f docker-compose.deploy.yml pull
docker compose -f docker-compose.deploy.yml up -d

# Levantar SonarQube local (si no se usa SonarCloud)
docker compose -f docker-compose.sonar.yml up -d
```

**Próximo paso mañana:** abrir `DESPLIEGUE-ORACLE.md` y crear la instancia ARM.
